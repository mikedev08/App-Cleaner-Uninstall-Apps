package com.jedy.appcleaner.uninstaller.ads

import com.jedy.appcleaner.uninstaller.core.analytics.AdTrigger

/**
 * What is on screen, as far as interstitials care (AdMob PRD §5.3). Nothing ever interrupts
 * onboarding, the paywall, the Usage access disclosure or the uninstall path (the Review sheet and
 * the Progress screen, where Android's dialogs are queued). In-session ads only land on Home.
 */
enum class AdSurface(val allowsResume: Boolean, val allowsInSession: Boolean) {
    ONBOARDING(allowsResume = false, allowsInSession = false),
    PAYWALL(allowsResume = false, allowsInSession = false),
    USAGE_ACCESS(allowsResume = false, allowsInSession = false),
    /** The Uninstall confirmation sheet is open, over the Apps screen. */
    REVIEW(allowsResume = false, allowsInSession = false),
    UNINSTALL(allowsResume = false, allowsInSession = false),
    RESULT(allowsResume = true, allowsInSession = false),
    SCAN(allowsResume = true, allowsInSession = false),
    APPS(allowsResume = true, allowsInSession = false),
    HISTORY(allowsResume = true, allowsInSession = false),
    SETTINGS(allowsResume = true, allowsInSession = false),
    HOME(allowsResume = true, allowsInSession = true),
}

/**
 * When an interstitial may show (AdMob PRD §5.2). After the paywall and on resume there is no
 * minimum interval; in-session shows wait [minIntervalMs] after the last impression of any
 * trigger. Times are `elapsedRealtime` milliseconds.
 */
class InterstitialPolicy(private val minIntervalMs: Long = AdConfig.INTERSTITIAL_MIN_INTERVAL_MS) {

    private var lastImpressionAt: Long? = null

    fun onImpression(now: Long) {
        lastImpressionAt = now
    }

    fun allows(trigger: AdTrigger, surface: AdSurface, now: Long, isShowing: Boolean): Boolean {
        if (isShowing) return false
        return when (trigger) {
            AdTrigger.AFTER_OFFER -> true
            AdTrigger.RESUME -> surface.allowsResume
            AdTrigger.IN_SESSION -> surface.allowsInSession && cooledDown(now)
        }
    }

    private fun cooledDown(now: Long): Boolean {
        val last = lastImpressionAt ?: return true
        return now - last >= minIntervalMs
    }
}

/**
 * Tells a real return to the foreground apart from the activity coming back for any other
 * reason: first launch, a configuration change, or the end of something the app opened itself
 * (Usage access or App info settings, the Play Store, the purchase sheet, an ad).
 *
 * The app calls [onExternalLaunch] right before starting such an activity. The flag lasts until
 * the app is resumed again, so a translucent one that never stops us (Android's uninstall dialog,
 * a permission prompt) cannot leave it set for the next real trip to the home screen.
 */
class ResumeGate {
    private var backgrounded = false
    private var launchedExternal = false
    private var returning = false

    fun onExternalLaunch() {
        launchedExternal = true
    }

    /**
     * A new activity instance, in a process that may have outlived the last one (Back from Home,
     * then a relaunch): that is a launch, never a return. A configuration change never set
     * [backgrounded] in the first place.
     */
    fun onCreate() {
        backgrounded = false
        returning = false
    }

    fun onStop(changingConfigurations: Boolean) {
        if (!changingConfigurations) backgrounded = true
    }

    fun onStart() {
        returning = backgrounded && !launchedExternal
        backgrounded = false
    }

    /** True once per genuine return from the background: the moment for a resume interstitial. */
    fun onResume(): Boolean {
        val show = returning
        returning = false
        launchedExternal = false
        return show
    }
}

/**
 * Delay before retrying a failed interstitial load (AdMob PRD §10): 30 s, 60 s, 90 s, then
 * nothing until the next show point asks for an ad again. Null means stop retrying.
 */
fun retryDelayMs(consecutiveFails: Int): Long? {
    if (consecutiveFails !in 1..AdConfig.MAX_CONSECUTIVE_FAILS) return null
    return (AdConfig.RETRY_STEP_MS * consecutiveFails).coerceAtMost(AdConfig.RETRY_MAX_DELAY_MS)
}
