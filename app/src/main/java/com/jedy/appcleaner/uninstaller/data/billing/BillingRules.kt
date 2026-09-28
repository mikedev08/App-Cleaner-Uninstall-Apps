package com.jedy.appcleaner.uninstaller.data.billing

/**
 * The pure decisions behind premium state, kept free of Android and RevenueCat so they are
 * unit-tested directly.
 */
object BillingRules {
    /** RevenueCat entitlement identifier (PRD §2): one entitlement for the one product. */
    const val ENTITLEMENT_ID = "premium"

    /**
     * Premium = the store entitlement, OR the debug override — and the override only ever counts
     * in a debug build, so a stale DataStore flag copied onto a release install unlocks nothing.
     */
    fun isPremium(entitlementActive: Boolean, debugBuild: Boolean, debugForcePremium: Boolean): Boolean =
        entitlementActive || (debugBuild && debugForcePremium)

    /**
     * Whether the entitlement we persisted last time still counts, before RevenueCat has answered
     * (cold start, offline, a background worker in a fresh process — PRD §6 item 17).
     *
     * An auto-renewing subscription stays trusted past its stored expiry, because Play renews it
     * whether or not this device is online and RevenueCat corrects us on the next connected start.
     * One the user cancelled ([willRenew] false) stops counting at its expiry, so a lapsed trial
     * on a phone that never reconnects does not stay premium forever.
     */
    fun cachedEntitlementActive(
        active: Boolean,
        expiresAtMillis: Long?,
        willRenew: Boolean,
        nowMillis: Long,
    ): Boolean = when {
        !active -> false
        expiresAtMillis == null -> true // lifetime / no expiry
        willRenew -> true
        else -> nowMillis < expiresAtMillis
    }

    /** ISO-8601 period (value + unit) of a free-trial phase, as whole days. */
    fun periodToDays(value: Int, unit: PeriodUnit): Int? = when (unit) {
        PeriodUnit.DAY -> value
        PeriodUnit.WEEK -> value * 7
        PeriodUnit.MONTH -> value * 30
        PeriodUnit.YEAR -> value * 365
        PeriodUnit.UNKNOWN -> null
    }?.takeIf { it > 0 }

    enum class PeriodUnit { DAY, WEEK, MONTH, YEAR, UNKNOWN }
}
