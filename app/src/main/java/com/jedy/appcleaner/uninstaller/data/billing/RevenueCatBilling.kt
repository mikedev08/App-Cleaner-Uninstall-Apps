package com.jedy.appcleaner.uninstaller.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RevenueCat-backed billing (PRD §2): one weekly subscription with a 3-day free trial behind the
 * `premium` entitlement.
 *
 * With no API key in the build (`revenuecat.apiKey` in local.properties), nothing here touches
 * the SDK: [isConfigured] is false, the offer is [OfferState.NotConfigured], purchases report
 * [PurchaseOutcome.Unavailable], and startup logs nothing — the rest of the app works unchanged.
 */
@Singleton
class RevenueCatBilling @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cache: EntitlementCache,
    @param:ApplicationScope private val scope: CoroutineScope,
) : BillingRepository {

    private val apiKey: String = BuildConfig.REVENUECAT_API_KEY.trim()

    @Volatile private var configured = false
    override val isConfigured: Boolean get() = configured

    /**
     * Seeded synchronously from disk: every screen and worker must see the right answer without
     * waiting for RevenueCat. The read is one tiny file. This singleton is normally first built by
     * [BillingStartup] on a background thread right after `Application.onCreate`, so the blocking
     * read stays off the main thread; a screen that asks before it finishes simply waits for it.
     */
    @Volatile private var lastEntitlement: CachedEntitlement = runBlocking { cache.read() }
    private val cacheWriteMutex = Mutex()

    private val _entitlementActive = MutableStateFlow(lastEntitlement.isStillActive(System.currentTimeMillis()))
    override val entitlementActive: StateFlow<Boolean> = _entitlementActive.asStateFlow()

    private val _offer = MutableStateFlow<OfferState>(
        if (apiKey.isEmpty()) OfferState.NotConfigured else OfferState.Loading
    )
    override val offer: StateFlow<OfferState> = _offer.asStateFlow()

    /** The RevenueCat package behind [OfferState.Loaded]; never leaves this class. */
    @Volatile private var weeklyPackage: Package? = null
    private val offerMutex = Mutex()

    /** Called once from [BillingStartup], off the main thread, at process start. */
    fun start() {
        if (apiKey.isEmpty() || configured) return
        try {
            if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        } catch (e: RuntimeException) {
            // A malformed key must not take the app down; billing just stays off.
            Log.w(TAG, "RevenueCat not configured: ${e.javaClass.simpleName}")
            _offer.value = OfferState.NotConfigured
            return
        }
        configured = true
        // Fires on purchases, restores and RevenueCat's own foreground refresh — which is how an
        // offline premium user gets re-validated on the next connected start (PRD §6 item 17).
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> onCustomerInfo(info) }
        scope.launch { refreshCustomerInfo() }
        // Prefetch so the paywall usually opens with its price already in hand.
        scope.launch { loadOffer() }
    }

    private suspend fun refreshCustomerInfo() {
        try {
            onCustomerInfo(Purchases.sharedInstance.awaitCustomerInfo())
        } catch (_: PurchasesException) {
            // Offline: keep the cached entitlement until RevenueCat can answer.
        }
    }

    private fun onCustomerInfo(info: CustomerInfo) {
        val entitlement = info.entitlements.all[BillingRules.ENTITLEMENT_ID]
        val next = CachedEntitlement(
            active = entitlement?.isActive == true,
            expiresAtMillis = entitlement?.expirationDate?.time,
            willRenew = entitlement?.willRenew == true,
            productId = entitlement?.productIdentifier ?: lastEntitlement.productId,
        )
        lastEntitlement = next
        // RevenueCat's own isActive already accounts for grace periods and account hold.
        _entitlementActive.value = next.active
        // Always persists the *latest* value, so two quick updates can't land out of order.
        scope.launch { cacheWriteMutex.withLock { cache.write(lastEntitlement) } }
    }

    override suspend fun loadOffer() {
        if (!configured) {
            _offer.value = OfferState.NotConfigured
            return
        }
        // Fetched on the application scope and only joined here: a paywall closed mid-fetch
        // cancels its wait, never the fetch, so the shared state can't be left stuck in Loading.
        scope.launch { fetchOffer() }.join()
    }

    private suspend fun fetchOffer() {
        offerMutex.withLock {
            if (_offer.value is OfferState.Loaded) return
            _offer.value = OfferState.Loading
            var failure: PurchasesException? = null
            val offerings = withTimeoutOrNull(OFFER_TIMEOUT_MS) {
                try {
                    Purchases.sharedInstance.awaitOfferings()
                } catch (e: PurchasesException) {
                    failure = e
                    null
                }
            }
            // No answer and no error = timed out, which in practice is a connection that is up in
            // name only; the user-facing fix is the same as for a network error.
            val offline = failure?.code == PurchasesErrorCode.NetworkError || (offerings == null && failure == null)
            val current = offerings?.current
            val pkg = current?.weekly ?: current?.availablePackages?.firstOrNull()
            weeklyPackage = pkg
            _offer.value = if (pkg != null) OfferState.Loaded(pkg.toWeeklyOffer()) else OfferState.Unavailable(offline)
        }
    }

    override suspend fun purchase(activity: Activity): PurchaseOutcome {
        if (!configured) return PurchaseOutcome.Unavailable
        val pkg = weeklyPackage ?: return PurchaseOutcome.Unavailable
        val trialOffered = pkg.product.subscriptionOptions?.freeTrial != null
        return try {
            // Buying the package lets RevenueCat pick the product's default option, which is the
            // free-trial offer whenever Play says this account is still eligible for it.
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            onCustomerInfo(result.customerInfo)
            val entitlement = result.customerInfo.entitlements.all[BillingRules.ENTITLEMENT_ID]
            if (entitlement?.isActive == true) {
                PurchaseOutcome.Success(startedTrial = entitlement.periodType == PeriodType.TRIAL || trialOffered)
            } else {
                PurchaseOutcome.Pending
            }
        } catch (e: PurchasesTransactionException) {
            when {
                e.userCancelled || e.code == PurchasesErrorCode.PurchaseCancelledError -> PurchaseOutcome.Cancelled
                e.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
                else -> PurchaseOutcome.Failed(network = e.code == PurchasesErrorCode.NetworkError)
            }
        } catch (e: PurchasesException) {
            PurchaseOutcome.Failed(network = e.code == PurchasesErrorCode.NetworkError)
        }
    }

    override suspend fun restore(): RestoreOutcome {
        if (!configured) return RestoreOutcome.NotConfigured
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            onCustomerInfo(info)
            if (_entitlementActive.value) RestoreOutcome.Restored else RestoreOutcome.NothingToRestore
        } catch (e: PurchasesException) {
            RestoreOutcome.Failed(network = e.code == PurchasesErrorCode.NetworkError)
        }
    }

    override fun manageSubscriptionUrl(): String =
        BillingLinks.manageSubscription(context.packageName, lastEntitlement.productId?.substringBefore(':'))

    private fun Package.toWeeklyOffer(): WeeklyOffer {
        val trialOption = product.subscriptionOptions?.freeTrial
        val trialDays = trialOption?.freePhase?.billingPeriod?.toDays()
        // The recurring price, never the free phase's "0".
        val price = (trialOption ?: product.defaultOption)?.fullPricePhase?.price?.formatted
            ?: product.price.formatted
        return WeeklyOffer(price = price, trialDays = trialDays, productId = product.id)
    }

    private fun Period.toDays(): Int? = BillingRules.periodToDays(
        value,
        when (unit) {
            Period.Unit.DAY -> BillingRules.PeriodUnit.DAY
            Period.Unit.WEEK -> BillingRules.PeriodUnit.WEEK
            Period.Unit.MONTH -> BillingRules.PeriodUnit.MONTH
            Period.Unit.YEAR -> BillingRules.PeriodUnit.YEAR
            Period.Unit.UNKNOWN -> BillingRules.PeriodUnit.UNKNOWN
        },
    )

    private companion object {
        const val TAG = "AppCleanerBilling"

        /** PRD §6 item 16: the plan card resolves to "offline" rather than spinning forever. */
        const val OFFER_TIMEOUT_MS = 10_000L
    }
}
