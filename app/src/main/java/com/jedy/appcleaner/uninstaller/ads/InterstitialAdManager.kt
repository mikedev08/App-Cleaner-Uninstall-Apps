package com.jedy.appcleaner.uninstaller.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.jedy.appcleaner.uninstaller.core.analytics.AdTrigger
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.analytics.MissedShowReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps one interstitial loaded ahead of every show point (AdMob PRD §5.1). A show point with no
 * ad ready never waits for one: it logs a missed show and loads for next time. The one exception
 * is the first ad after the launch paywall, which watches [ready] for up to
 * [AdConfig.AFTER_OFFER_MAX_WAIT_MS] (PRD §5.4). The SDK's own full-screen activity owns Back
 * while the ad is up, so only its close control dismisses it. Main thread only.
 */
internal class InterstitialAdManager(
    private val context: Context,
    private val logger: AdEventLogger,
    /** Runs right before the ad's activity starts, so its end does not count as a resume. */
    private val onLaunchingAd: () -> Unit,
    /** False for Pro users: nothing loads, and nothing already loaded is shown. */
    private val canLoad: () -> Boolean,
) {
    val policy = InterstitialPolicy()

    var isShowing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val retry = Runnable { load() }
    private var ad: InterstitialAd? = null
        set(value) {
            field = value
            _ready.value = value != null
        }
    private val _ready = MutableStateFlow(false)

    /** True while an ad is loaded and waiting to show. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private var loadedAt = 0L
    private var isLoading = false
    private var failCount = 0

    fun load() {
        if (!canLoad() || isLoading || isShowing || freshAd() != null) return
        handler.removeCallbacks(retry)
        isLoading = true
        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    if (!canLoad()) {
                        // Pro arrived while this was loading: it is dropped, never shown.
                        isLoading = false
                        return
                    }
                    ad = loaded
                    loadedAt = SystemClock.elapsedRealtime()
                    isLoading = false
                    failCount = 0
                    logger.log(AnalyticsEvent.InterstitialLoaded)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    isLoading = false
                    failCount++
                    logger.log(AnalyticsEvent.InterstitialLoadFailed(error.code))
                    if (canLoad()) retryDelayMs(failCount)?.let { handler.postDelayed(retry, it) }
                }
            },
        )
    }

    /** Pro: stops any retry and drops the loaded ad. A load already sent is dropped when it lands. */
    fun clear() {
        handler.removeCallbacks(retry)
        ad = null
        failCount = 0
    }

    /** Shows the loaded ad, or logs a missed show. [onDismissed] runs either way, once. */
    fun show(activity: Activity, trigger: AdTrigger, onDismissed: () -> Unit = {}) {
        val current = freshAd()
        if (current == null) {
            val reason = if (isLoading) MissedShowReason.LOADING else MissedShowReason.NOT_LOADED
            logger.log(AnalyticsEvent.InterstitialMissedShow(trigger, reason))
            onDismissed()
            load()
            return
        }
        ad = null
        // The last check before an ad covers the screen: Pro may have arrived since it loaded.
        if (!canLoad()) {
            onDismissed()
            return
        }
        isShowing = true
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                policy.onImpression(SystemClock.elapsedRealtime())
                logger.log(AnalyticsEvent.InterstitialShowed(trigger))
            }

            override fun onAdImpression() = logger.log(AnalyticsEvent.InterstitialImpression)

            override fun onAdClicked() = logger.log(AnalyticsEvent.InterstitialClicked)

            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                load()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isShowing = false
                logger.log(AnalyticsEvent.InterstitialShowFailed(error.code))
                load()
                onDismissed()
            }
        }
        onLaunchingAd()
        current.show(activity)
    }

    private fun freshAd(): InterstitialAd? {
        val current = ad ?: return null
        if (SystemClock.elapsedRealtime() - loadedAt < AdConfig.AD_MAX_AGE_MS) return current
        ad = null
        return null
    }
}
