package com.jedy.appcleaner.uninstaller.core.analytics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics sink. Firebase Analytics/Crashlytics (PRD §2) plug in here once
 * `google-services.json` is added; until then [LogcatAnalytics] keeps every call site honest.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)
}

@Singleton
class LogcatAnalytics @Inject constructor() : Analytics {
    override fun log(event: AnalyticsEvent) {
        val params = event.params
        Log.d(TAG, if (params.isEmpty()) event.name else "${event.name} ${params.entries.joinToString { "${it.key}=${it.value}" }}")
    }

    private companion object {
        const val TAG = "AppCleanerFunnel"
    }
}
