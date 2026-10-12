package com.jedy.appcleaner.uninstaller.core.analytics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics sink, bound to [FirebaseAnalyticsSink] (PRD §2). Call sites only see this interface,
 * so nothing outside `core/analytics` depends on a Firebase type and tests can swap in a fake.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)
}

/** The funnel in logcat (tag AppCleanerFunnel); debug builds echo every Firebase event through it. */
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
