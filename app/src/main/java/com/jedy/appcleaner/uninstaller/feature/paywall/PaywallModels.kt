package com.jedy.appcleaner.uninstaller.feature.paywall

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.billing.OfferState

/**
 * What the plan card and CTA show (PRD §4 Screen 3, §6 items 16 and 19). Pure, so every branch of
 * the copy rules is unit-tested.
 */
sealed interface PaywallPlan {
    /** Bounded by the billing timeout — it always resolves to one of the states below. */
    data object Loading : PaywallPlan

    /** "3 days free, then <price>/week" + "Start free trial". Only for trial-eligible accounts. */
    data class Trial(val price: String, val trialDays: Int) : PaywallPlan

    /** "<price>/week" + "Subscribe": the trial was already used, so the user pays today. */
    data class Weekly(val price: String) : PaywallPlan

    /** PRD §6 item 16: "Connect to the internet to start your free trial" + "Try again". */
    data object Offline : PaywallPlan

    /** Store answered but has nothing to sell (no current offering yet). Neutral copy + retry. */
    data object Unavailable : PaywallPlan

    /**
     * No RevenueCat key in this build. Same neutral card as [Unavailable]; in a debug build the
     * CTA becomes "Unlock (debug)" and flips the local premium override, so the whole premium
     * flow can be tested end to end on a device before billing exists. Never in release.
     */
    data class NotConfigured(val debugUnlock: Boolean) : PaywallPlan

    /** True when the CTA does something. */
    val canPurchase: Boolean
        get() = this is Trial || this is Weekly || (this is NotConfigured && debugUnlock)

    companion object {
        fun from(offer: OfferState, debugBuild: Boolean): PaywallPlan = when (offer) {
            OfferState.NotConfigured -> NotConfigured(debugUnlock = debugBuild)
            OfferState.Loading -> Loading
            is OfferState.Loaded -> offer.offer.trialDays
                ?.let { days -> Trial(offer.offer.price, days) }
                ?: Weekly(offer.offer.price)
            is OfferState.Unavailable -> if (offer.offline) Offline else Unavailable
        }
    }
}

/**
 * PRD §0 decision 3 — show the count before the price. From a locked tab, with Usage Access
 * granted, the headline becomes the user's own finding instead of the generic promise.
 */
sealed interface PaywallHeadline {
    data object Default : PaywallHeadline

    /** "14 apps unused for 60+ days · 3.4 GB". */
    data class Unused(val count: Int, val thresholdDays: Int, val bytes: Long) : PaywallHeadline

    /** "Your 10 largest apps use 18.7 GB". */
    data class Large(val count: Int, val bytes: Long) : PaywallHeadline

    companion object {
        /** Same size of the Large tab's top list (PRD §4 Screen 6: "Your 10 largest apps"). */
        const val LARGEST_COUNT = 10

        /**
         * Best available size for one app: the measured app + data + cache total when Usage
         * Access has produced one, the APK size otherwise — the same rule the uninstall snapshot
         * uses (PRD Feature 2), so the paywall never quotes a bigger number than the tabs.
         */
        fun bytesOf(app: InstalledApp, sizes: Map<String, AppSize>): Long =
            sizes[app.packageName]?.totalBytes ?: app.apkBytes

        fun largest(apps: List<InstalledApp>, sizes: Map<String, AppSize>): PaywallHeadline {
            val top = apps.map { bytesOf(it, sizes) }.sortedDescending().take(LARGEST_COUNT)
            return if (top.isEmpty()) Default else Large(top.size, top.sum())
        }
    }
}
