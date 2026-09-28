package com.jedy.appcleaner.uninstaller.feature.paywall

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.data.billing.BillingRepository
import com.jedy.appcleaner.uninstaller.data.billing.OfferState
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.billing.PurchaseOutcome
import com.jedy.appcleaner.uninstaller.data.billing.RestoreOutcome
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val source: PaywallSource = PaywallSource.SETTINGS,
    val headline: PaywallHeadline = PaywallHeadline.Default,
    val plan: PaywallPlan = PaywallPlan.Loading,
    val isPremium: Boolean = false,
    val purchasing: Boolean = false,
    val restoring: Boolean = false,
)

sealed interface PaywallEvent {
    /** Premium is now active — leave the paywall (PRD §4 Screen 3: purchase success closes it). */
    data object Close : PaywallEvent

    data class Message(@param:StringRes val messageRes: Int) : PaywallEvent
}

private enum class Busy { NONE, PURCHASING, RESTORING }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val premium: Premium,
    private val preferences: AppPreferences,
    private val inventory: AppInventory,
    private val usageAccess: UsageAccess,
    private val insights: UsageInsights,
    private val storage: StorageBreakdown,
    private val analytics: Analytics,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val source = MutableStateFlow<PaywallSource?>(null)
    private val busy = MutableStateFlow(Busy.NONE)

    private val headline: Flow<PaywallHeadline> = source.filterNotNull().flatMapLatest { src ->
        when (src) {
            PaywallSource.UNUSED_TAB -> combine(
                usageAccess.isGranted,
                inventory.apps,
                insights.lastUsed, // only a trigger: unusedApps() reads the latest itself
                storage.sizes,
                preferences.unusedThresholdDays,
            ) { granted, apps, _, sizes, days ->
                if (!granted) return@combine PaywallHeadline.Default
                val unused = insights.unusedApps(apps, days)
                if (unused.isEmpty()) {
                    PaywallHeadline.Default
                } else {
                    PaywallHeadline.Unused(
                        count = unused.size,
                        thresholdDays = days,
                        bytes = unused.sumOf { PaywallHeadline.bytesOf(it.app, sizes) },
                    )
                }
            }
            PaywallSource.LARGE_TAB -> combine(usageAccess.isGranted, inventory.apps, storage.sizes) { granted, apps, sizes ->
                if (granted) PaywallHeadline.largest(apps, sizes) else PaywallHeadline.Default
            }
            else -> flowOf(PaywallHeadline.Default)
        }
    }.flowOn(defaultDispatcher)

    val uiState: StateFlow<PaywallUiState> = combine(
        source.filterNotNull(),
        headline.onStart { emit(PaywallHeadline.Default) },
        billing.offer,
        premium.isPremium,
        busy,
    ) { src, headline, offer, isPremium, busy ->
        PaywallUiState(
            source = src,
            headline = headline,
            plan = PaywallPlan.from(offer, BuildConfig.DEBUG),
            isPremium = isPremium,
            purchasing = busy == Busy.PURCHASING,
            restoring = busy == Busy.RESTORING,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // Seeded with the live values so a premium user never sees a paywall frame first.
        initialValue = PaywallUiState(
            plan = PaywallPlan.from(billing.offer.value, BuildConfig.DEBUG),
            isPremium = premium.isPremium.value,
        ),
    )

    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events: Flow<PaywallEvent> = _events.receiveAsFlow()

    private var shownAtMillis = 0L
    private var finished = false
    private var viewJob: Job? = null
    private var viewLogged = false

    /** Idempotent: the ViewModel outlives rotation, the screen's LaunchedEffect does not. */
    fun onShown(src: PaywallSource) {
        if (source.value != null) return
        source.value = src
        shownAtMillis = SystemClock.elapsedRealtime()
        viewJob = viewModelScope.launch {
            // A premium user sees the confirmation, which is not a paywall view.
            if (premium.isPremium.value) return@launch
            // Retries a failed startup prefetch; returns at once when the price is already here.
            // Logged after it resolves so offering_loaded / trial_eligible are the truth.
            billing.loadOffer()
            logViewed()
        }
    }

    private fun logViewed() {
        if (viewLogged) return
        viewLogged = true
        val offer = billing.offer.value
        analytics.log(
            AnalyticsEvent.PaywallViewed(
                triggerSource = currentSource().value,
                trialEligible = (offer as? OfferState.Loaded)?.offer?.trialEligible == true,
                offeringLoaded = offer is OfferState.Loaded,
            )
        )
    }

    fun retry() {
        viewModelScope.launch { billing.loadOffer() }
    }

    /** Close (X) or system back without buying. */
    fun onDismiss() {
        if (finished) return
        finished = true
        if (premium.isPremium.value) return
        // Closed before the price resolved: record the view as it was, so the funnel never has a
        // dismissal without its view.
        viewJob?.cancel()
        logViewed()
        val seconds = (SystemClock.elapsedRealtime() - shownAtMillis).coerceAtLeast(0) / 1000
        analytics.log(AnalyticsEvent.PaywallDismissed(currentSource().value, seconds))
    }

    fun purchase(activity: Activity?) {
        if (busy.value != Busy.NONE) return
        val plan = uiState.value.plan
        if (plan is PaywallPlan.NotConfigured) {
            // Debug builds only (PaywallPlan never offers this in release): unlock locally.
            if (plan.debugUnlock && BuildConfig.DEBUG) {
                viewModelScope.launch {
                    preferences.setDebugForcePremium(true)
                    finish()
                }
            }
            return
        }
        if (activity == null || !plan.canPurchase) return
        viewModelScope.launch {
            busy.value = Busy.PURCHASING
            val outcome = billing.purchase(activity)
            busy.value = Busy.NONE
            when (outcome) {
                is PurchaseOutcome.Success -> {
                    val trigger = currentSource().value
                    analytics.log(
                        if (outcome.startedTrial) AnalyticsEvent.TrialStarted(trigger)
                        else AnalyticsEvent.SubscriptionStarted(trigger)
                    )
                    finish()
                }
                PurchaseOutcome.Cancelled -> Unit // The user said no; nothing to explain.
                PurchaseOutcome.Pending -> message(R.string.billing_purchase_pending)
                is PurchaseOutcome.Failed -> message(
                    if (outcome.network) R.string.billing_network_error else R.string.billing_purchase_failed
                )
                PurchaseOutcome.Unavailable -> message(R.string.billing_not_available)
            }
        }
    }

    fun restore() {
        if (busy.value != Busy.NONE) return
        viewModelScope.launch {
            busy.value = Busy.RESTORING
            val outcome = billing.restore()
            busy.value = Busy.NONE
            when (outcome) {
                RestoreOutcome.Restored -> {
                    analytics.log(AnalyticsEvent.PurchaseRestored(restoredPremium = true))
                    // The screen flips to "You're on Premium" on its own; say why.
                    message(R.string.billing_restore_success)
                }
                RestoreOutcome.NothingToRestore -> {
                    analytics.log(AnalyticsEvent.PurchaseRestored(restoredPremium = false))
                    message(R.string.billing_restore_nothing)
                }
                is RestoreOutcome.Failed -> message(
                    if (outcome.network) R.string.billing_network_error else R.string.billing_restore_failed
                )
                RestoreOutcome.NotConfigured -> message(R.string.billing_not_available)
            }
        }
    }

    private suspend fun finish() {
        finished = true
        _events.send(PaywallEvent.Close)
    }

    private suspend fun message(@StringRes res: Int) = _events.send(PaywallEvent.Message(res))

    private fun currentSource(): PaywallSource = source.value ?: PaywallSource.SETTINGS
}
