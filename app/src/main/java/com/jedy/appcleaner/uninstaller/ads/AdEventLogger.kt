package com.jedy.appcleaner.uninstaller.ads

import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ads package's bridge to analytics (AdMob PRD §9). Ad events are ordinary [AnalyticsEvent]s,
 * so they go through the same sink as the funnel, with no second logging path.
 */
@Singleton
class AdEventLogger @Inject constructor(private val analytics: Analytics) {
    fun log(event: AnalyticsEvent) = analytics.log(event)
}
