package com.jedy.appcleaner.uninstaller.data.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager scheduling for the premium reminder and the free nudge (PRD Feature 4, §2).
 *
 * Nothing outside Insights calls this: the Settings screen only writes
 * `AppPreferences.remindersEnabled` / `unusedThresholdDays`, and
 * [com.jedy.appcleaner.uninstaller.data.usage.InsightsSync] turns those prefs plus the entitlement
 * into [schedule] / [cancel]. The threshold is read by the worker at run time, so changing it
 * never needs a reschedule.
 */
@Singleton
class WorkManagerCleanupReminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CleanupReminders {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

    /**
     * Weekly, flex 1 day, battery not low; KEEP so repeated calls never reset the cadence. The
     * first run waits a day so unlocking premium isn't immediately followed by a notification
     * about what the user is looking at right now.
     */
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<CleanupReminderWorker>(7, TimeUnit.DAYS, 1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(REMINDER_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(REMINDER_WORK)
    }

    /**
     * Always kept scheduled; the worker itself skips premium users. The first check waits 30
     * days so a brand-new user is never nudged about a cleanup they have just done.
     */
    fun scheduleNudge() {
        val request = PeriodicWorkRequestBuilder<CleanupNudgeWorker>(7, TimeUnit.DAYS, 1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(NUDGE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val REMINDER_WORK = "cleanup_reminder"
        const val NUDGE_WORK = "cleanup_nudge"
    }
}
