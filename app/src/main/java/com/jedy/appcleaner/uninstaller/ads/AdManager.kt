package com.jedy.appcleaner.uninstaller.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.core.analytics.AdFormat
import com.jedy.appcleaner.uninstaller.core.analytics.AdPlacement
import com.jedy.appcleaner.uninstaller.core.analytics.AdTrigger
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single entry point for ads (AdMob PRD §4.4). UI code talks to this, never to the
 * individual managers. Main thread only.
 *
 * Start-up order (PRD §4.2): consent is resolved, then the SDK initialises, then both formats
 * preload. Nothing is requested before that, and nothing on screen waits for it.
 *
 * Pro users get no ads at all (PRD §4.3): while [Premium.isPremium] is true no ad code runs, not
 * even the consent form or the SDK start, and every load and show checks it again at the moment it
 * would happen. A first-run user is left alone until the onboarding language step is behind them.
 */
@Singleton
class AdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AdEventLogger,
    private val premium: Premium,
) {
    private val main = Handler(Looper.getMainLooper())
    private val consent = ConsentManager(context, logger)
    private val resumeGate = ResumeGate()
    private val interstitials = InterstitialAdManager(
        context,
        logger,
        onLaunchingAd = resumeGate::onExternalLaunch,
        canLoad = ::adsAllowed,
    )
    private val natives = NativeAdManager(context, logger, canLoad = ::adsAllowed)
    private val suppressions = SuppressionLog()

    private var started = false
    private val _initialized = MutableStateFlow(false)
    private val initialized get() = _initialized.value

    private val _pastLanguageStep = MutableStateFlow(false)

    /** Onboarding's language step is done, or the user was onboarded before this launch. */
    val pastLanguageStep: StateFlow<Boolean> = _pastLanguageStep.asStateFlow()

    /** What is on screen; set by the navigation host. Nothing shows until it says otherwise. */
    var surface: AdSurface = AdSurface.ONBOARDING
        set(value) {
            if (field != value) suppressions.newVisit()
            field = value
        }

    /** Set while the Uninstall confirmation sheet is open: the start of the uninstall path. */
    var isReviewOpen = false

    /**
     * Pro removes ads, as ad slots follow it: a slot is not composed at all while this is true.
     * A build without subscriptions has every Pro feature on and ads for everyone.
     */
    val isPro: StateFlow<Boolean> = if (BuildConfig.SUBSCRIPTIONS) premium.isPremium else MutableStateFlow(false)

    val privacyOptionsRequired: StateFlow<Boolean> get() = consent.privacyOptionsRequired

    fun gate() = adGate(isPro.value, _pastLanguageStep.value)

    private fun adsAllowed() = gate() == AdGate.ALLOW

    fun onPastLanguageStep() {
        _pastLanguageStep.value = true
    }

    /**
     * From the activity, whenever Pro or the language step changes: starts ads once they are
     * allowed, and drops everything loaded or on its way the moment Pro turns on.
     */
    fun onGateChanged(activity: Activity) {
        when (gate()) {
            AdGate.ALLOW -> start(activity)
            AdGate.SUPPRESSED_PRO -> {
                interstitials.clear()
                natives.clear()
            }
            AdGate.WAIT_FOR_LANGUAGE -> Unit
        }
    }

    /** Consent is refreshed once per process, the first time ads are allowed. */
    private fun start(activity: Activity) {
        if (initialized) {
            preloadAll()
            return
        }
        if (started) return
        started = true
        consent.requestAndShow(activity) {
            // Pro may have arrived while the form was up; the next allowed gate starts again.
            if (!adsAllowed()) {
                started = false
                return@requestAndShow
            }
            // Initialising blocks on disk and IPC; Google asks for it off the main thread.
            Thread {
                MobileAds.initialize(context) {
                    main.post {
                        _initialized.value = true
                        natives.onSdkReady()
                        preloadAll()
                    }
                }
            }.start()
        }
    }

    /** True, and logs `ad_suppressed` once per visit, when [placement] is skipped for a Pro user. */
    private fun suppressedForPro(format: AdFormat, placement: String): Boolean {
        if (!isPro.value) return false
        if (suppressions.shouldLog(format, placement)) logger.log(AnalyticsEvent.AdSuppressed(format, placement))
        return true
    }

    /** A native slot that was not composed because the user is Pro. */
    fun onNativeSuppressed(placement: AdPlacement) {
        suppressedForPro(AdFormat.NATIVE, placement.value)
    }

    fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)

    // -- Interstitial ------------------------------------------------------------------------

    /** Trigger A, when the paywall is closed. No cooldown; Pro (just bought) sees nothing. */
    fun showAfterOffer(activity: Activity) {
        if (suppressedForPro(AdFormat.INTERSTITIAL, AdTrigger.AFTER_OFFER.value)) return
        if (!initialized || !allows(AdTrigger.AFTER_OFFER)) return
        interstitials.show(activity, AdTrigger.AFTER_OFFER)
    }

    /**
     * Trigger A after the onboarding paywall (PRD §5.4). The SDK may still be starting, so this
     * waits for it, then up to [AdConfig.AFTER_OFFER_MAX_WAIT_MS] for an ad to load. By then the
     * user must still be on Home; one who has already moved on is not interrupted.
     */
    suspend fun showAfterLaunchOffer(activity: Activity) {
        if (suppressedForPro(AdFormat.INTERSTITIAL, AdTrigger.AFTER_OFFER.value)) return
        _initialized.first { it }
        withTimeoutOrNull(AdConfig.AFTER_OFFER_MAX_WAIT_MS) { interstitials.ready.first { it } }
        if (surface != AdSurface.HOME || isReviewOpen || activity.isFinishing) return
        showAfterOffer(activity)
    }

    /** Trigger C, on arriving at Home. Skipped quietly inside the cooldown. */
    fun tryShowInSession(activity: Activity) {
        if (suppressedForPro(AdFormat.INTERSTITIAL, AdTrigger.IN_SESSION.value)) return
        if (!initialized || !allows(AdTrigger.IN_SESSION)) return
        interstitials.show(activity, AdTrigger.IN_SESSION)
    }

    /** Call right before starting another app's activity, so coming back is not a resume. */
    fun suppressNextResume() = resumeGate.onExternalLaunch()

    fun onActivityCreate() = resumeGate.onCreate()

    fun onActivityStart() = resumeGate.onStart()

    /** Trigger B: every genuine return from the background. No cooldown. */
    fun onActivityResume(activity: Activity) {
        if (!resumeGate.onResume()) return
        if (suppressedForPro(AdFormat.INTERSTITIAL, AdTrigger.RESUME.value)) return
        if (!initialized || !allows(AdTrigger.RESUME)) return
        interstitials.show(activity, AdTrigger.RESUME)
    }

    fun onActivityStop(changingConfigurations: Boolean) = resumeGate.onStop(changingConfigurations)

    private fun allows(trigger: AdTrigger): Boolean {
        val effective = if (isReviewOpen) AdSurface.REVIEW else surface
        return interstitials.policy.allows(trigger, effective, SystemClock.elapsedRealtime(), interstitials.isShowing)
    }

    // -- Native ------------------------------------------------------------------------------

    /**
     * A fresh native ad for one screen appearance; the caller destroys it when done. Returns
     * the cancel for a screen that goes away before its ad arrives.
     */
    fun requestNativeAd(onReady: (NativeAd) -> Unit): () -> Unit {
        if (isPro.value) return {}
        return natives.request(onReady)::cancel
    }

    /** A native ad was drawn into its slot: the numerator of the native show rate. */
    fun onNativeAdShown(placement: AdPlacement) = natives.onShown(placement)

    /** When the activity finishes for good; a relaunch in the same process preloads again. */
    fun destroy() = natives.destroyAll()

    private fun preloadAll() {
        if (!adsAllowed()) return
        interstitials.load()
        natives.preload()
    }
}
