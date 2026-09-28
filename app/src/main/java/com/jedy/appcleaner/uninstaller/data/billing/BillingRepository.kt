package com.jedy.appcleaner.uninstaller.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * The paywall's and Settings' view of the one V1 product (PRD §2): a weekly auto-renewing
 * subscription with a 3-day free trial, mapped to the RevenueCat entitlement `premium`.
 *
 * Everything else in the app reads only [Premium.isPremium]. This richer surface exists so the
 * two screens that sell or manage the subscription never touch RevenueCat types directly — which
 * is also what lets them render sensibly when RevenueCat is not configured at all (no API key in
 * `local.properties`): the app must stay fully usable in that state.
 */
interface BillingRepository {
    /** False when no RevenueCat API key was built in; every call below then degrades quietly. */
    val isConfigured: Boolean

    /** The real store entitlement, ignoring the debug override. Persisted, so right on cold start. */
    val entitlementActive: StateFlow<Boolean>

    /** The weekly package from the current offering, or why there is none. */
    val offer: StateFlow<OfferState>

    /** (Re)fetches the current offering. Cheap when already loaded; bounded by a timeout. */
    suspend fun loadOffer()

    /** Launches the Play purchase sheet for the weekly package. A user cancel is not an error. */
    suspend fun purchase(activity: Activity): PurchaseOutcome

    suspend fun restore(): RestoreOutcome

    /**
     * Play's own subscription-management page for this app (PRD §4 Screen 13), pointing at the
     * exact subscription when we know its product id.
     */
    fun manageSubscriptionUrl(): String
}

/** Offering load state. Never an endless spinner: [Loading] always resolves (PRD §6 item 16). */
sealed interface OfferState {
    /** No API key built in — billing is intentionally off in this build. */
    data object NotConfigured : OfferState

    data object Loading : OfferState

    data class Loaded(val offer: WeeklyOffer) : OfferState

    /**
     * RevenueCat could not produce a weekly package: offline, Play unavailable, or the dashboard
     * has no current offering yet. [offline] is true when it was a connectivity failure.
     */
    data class Unavailable(val offline: Boolean) : OfferState
}

/**
 * What the paywall prints on its plan card.
 *
 * @param price the store-localized recurring price, e.g. "$2.99".
 * @param trialDays length of the free-trial phase the user is *eligible* for, or null. Play only
 * returns offers the signed-in account can still redeem, so null means "will be charged today"
 * and the paywall must not say "free" (PRD §6 item 19).
 */
data class WeeklyOffer(
    val price: String,
    val trialDays: Int?,
    val productId: String,
) {
    val trialEligible: Boolean get() = trialDays != null
}

sealed interface PurchaseOutcome {
    /** Entitlement is active. [startedTrial] picks `trial_started` vs `subscription_started`. */
    data class Success(val startedTrial: Boolean) : PurchaseOutcome

    /** The user backed out of the Play sheet. Silent: no error, no message. */
    data object Cancelled : PurchaseOutcome

    /** Payment accepted but not yet settled (e.g. cash payment methods). */
    data object Pending : PurchaseOutcome

    data class Failed(val network: Boolean) : PurchaseOutcome

    /** No package to buy (not configured, or offering not loaded). */
    data object Unavailable : PurchaseOutcome
}

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data object NothingToRestore : RestoreOutcome
    data class Failed(val network: Boolean) : RestoreOutcome
    data object NotConfigured : RestoreOutcome
}
