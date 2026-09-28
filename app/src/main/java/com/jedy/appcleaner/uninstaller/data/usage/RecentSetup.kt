package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UNUSED_THRESHOLD_DAYS

/**
 * PRD §6 "Apps restored to a new phone". After Smart Switch or Google Restore every restored app
 * carries the restore date as its `firstInstallTime`, so the unused rule (which also needs the
 * app to be installed for the whole threshold) finds nothing for a while — deliberately. This
 * only decides whether the empty Unused list should say why.
 *
 * "Looks recently set up": at least [RECENT_SHARE] of the user's apps were first installed
 * within the last [windowDays]. A normal phone gains a handful of apps a month; a restored one
 * gains nearly all of them on the same day.
 */
object RecentSetup {

    /** 60%: well above a normal month, well below "all of them" (some apps ship preloaded as updates). */
    const val RECENT_SHARE = 0.6

    fun looksRecentlySetUp(
        apps: List<InstalledApp>,
        now: Long,
        windowDays: Int = UNUSED_THRESHOLD_DAYS,
    ): Boolean {
        if (apps.isEmpty()) return false
        val cutoff = now - windowDays * DAY_MILLIS
        // An unknown install time (0) is not "recent". A future one (clock moved back) is.
        val recent = apps.count { it.firstInstallTime > 0 && it.firstInstallTime > cutoff }
        return recent >= apps.size * RECENT_SHARE
    }
}
