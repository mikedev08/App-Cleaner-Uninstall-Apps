package com.jedy.appcleaner.uninstaller.ads

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.jedy.appcleaner.uninstaller.core.analytics.AdPlacement
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent

/**
 * Loads native ads for [NativeAdQueue] (AdMob PRD §6.4). Loads asked for before the SDK has
 * initialised wait for it, so no request ever goes out ahead of consent. Main thread only.
 */
internal class NativeAdManager(
    private val context: Context,
    private val logger: AdEventLogger,
    /** False for Pro users: nothing loads, and a load already sent is dropped when it lands. */
    private val canLoad: () -> Boolean,
) {
    private var sdkReady = false
    private var deferredLoads = 0

    private val queue = NativeAdQueue<NativeAd>(
        startLoad = ::startLoad,
        destroy = NativeAd::destroy,
        now = SystemClock::elapsedRealtime,
    )

    fun onSdkReady() {
        sdkReady = true
        repeat(deferredLoads) { load() }
        deferredLoads = 0
    }

    fun preload() = queue.preload()

    fun request(onReady: (NativeAd) -> Unit): NativeAdQueue<NativeAd>.Ticket =
        queue.request(onReady, onMiss = { logger.log(AnalyticsEvent.NativePreloadMiss) })

    fun onShown(placement: AdPlacement) = logger.log(AnalyticsEvent.NativeShowed(placement))

    fun destroyAll() = queue.clear()

    /** Pro: drops the loads still waiting for the SDK, the preloaded ad and every waiting slot. */
    fun clear() {
        repeat(deferredLoads) { queue.onDiscarded() }
        deferredLoads = 0
        queue.clear()
    }

    private fun startLoad() {
        if (sdkReady) load() else deferredLoads++
    }

    private fun load() {
        if (!canLoad()) return queue.onDiscarded()
        AdLoader.Builder(context, AdConfig.NATIVE_UNIT_ID)
            .forNativeAd { ad ->
                if (!canLoad()) {
                    ad.destroy()
                    queue.onDiscarded()
                    return@forNativeAd
                }
                logger.log(AnalyticsEvent.NativeLoaded)
                queue.onLoaded(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    logger.log(AnalyticsEvent.NativeLoadFailed(error.code))
                    queue.onFailed()
                }

                override fun onAdImpression() = logger.log(AnalyticsEvent.NativeImpression)

                override fun onAdClicked() = logger.log(AnalyticsEvent.NativeClicked)
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }
}
