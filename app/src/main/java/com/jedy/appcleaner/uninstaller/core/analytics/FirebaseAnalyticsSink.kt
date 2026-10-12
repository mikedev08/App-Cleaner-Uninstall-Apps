package com.jedy.appcleaner.uninstaller.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jedy.appcleaner.uninstaller.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends every [AnalyticsEvent] to Firebase Analytics (project app-cleaner-jedy). Debug builds also
 * echo it to logcat through [LogcatAnalytics], so the funnel can still be followed without the
 * DebugView. The events carry no package names or labels (see [AnalyticsEvent]), so neither does this.
 */
@Singleton
class FirebaseAnalyticsSink @Inject constructor(
    @param:ApplicationContext context: Context,
    private val logcat: LogcatAnalytics,
) : Analytics {

    private val firebase = FirebaseAnalytics.getInstance(context)

    override fun log(event: AnalyticsEvent) {
        firebase.logEvent(event.name, event.params.toBundle())
        if (BuildConfig.DEBUG) logcat.log(event)
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            // Firebase accepts strings, longs and doubles; anything else is sent as text.
            when (value) {
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Float -> putDouble(key, value.toDouble())
                is Double -> putDouble(key, value)
                is Boolean -> putLong(key, if (value) 1L else 0L)
                else -> putString(key, value.toString().take(MAX_VALUE_LENGTH))
            }
        }
    }

    private companion object {
        /** Firebase drops string parameters longer than this. */
        const val MAX_VALUE_LENGTH = 100
    }
}
