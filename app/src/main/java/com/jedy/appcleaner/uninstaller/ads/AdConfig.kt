package com.jedy.appcleaner.uninstaller.ads

/** Ad unit IDs and timing (AdMob PRD §7). */
object AdConfig {
    // Google's sample units, which always serve test ads, in debug and release alike for now.
    // They, and the app ID in app/build.gradle.kts, are replaced with App Cleaner's real IDs before
    // publishing (AdMob PRD §2.2). No test device IDs are needed while these are in place.
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    /** In-session interstitials wait this long after the last impression of any trigger. */
    const val INTERSTITIAL_MIN_INTERVAL_MS = 45_000L

    /** After the launch paywall, how long the first interstitial may take to load once the SDK is up. */
    const val AFTER_OFFER_MAX_WAIT_MS = 5_000L

    /** Interstitial load retries back off by this much per consecutive failure, up to the cap. */
    const val RETRY_STEP_MS = 30_000L
    const val RETRY_MAX_DELAY_MS = 300_000L
    const val MAX_CONSECUTIVE_FAILS = 3

    /** A loaded ad is served for an hour; one kept longer than this is thrown away unshown. */
    const val AD_MAX_AGE_MS = 55 * 60_000L
}
