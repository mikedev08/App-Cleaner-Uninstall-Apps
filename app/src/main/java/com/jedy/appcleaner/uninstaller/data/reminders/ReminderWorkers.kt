package com.jedy.appcleaner.uninstaller.data.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The weekly premium cleanup reminder (PRD Feature 4).
 *
 * Every exit is a silent success: a reminder that could not be computed simply doesn't happen,
 * and WorkManager keeps the weekly cadence. Revoked access or a lapsed subscription therefore
 * stops reminders without any extra bookkeeping (PRD §6 items 12 and 18).
 */
@HiltWorker
class CleanupReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val premium: Premium,
    private val access: UsageAccess,
    private val inventory: AppInventory,
    private val insights: UsageInsights,
    private val storage: StorageBreakdown,
    private val preferences: AppPreferences,
    private val notifications: ReminderNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferences.remindersEnabled.first()) return Result.success()
        if (!premium.awaitPremium()) return Result.success()
        access.recheck()
        if (!access.isGranted.value) return Result.success()
        if (!notifications.canPost()) return Result.success()

        val apps = inventory.awaitApps()
        if (apps.isEmpty()) return Result.success()
        insights.refresh()
        val threshold = preferences.unusedThresholdDays.first()
        val now = System.currentTimeMillis()
        val unused = insights.unusedApps(apps, threshold, now)

        val remind = ReminderPolicy.shouldRemind(
            unusedCount = unused.size,
            lastCount = preferences.lastReminderCount.first(),
            lastAt = preferences.lastReminderAt.first(),
            now = now,
        )
        if (!remind) return Result.success()

        // Fresh sizes for just the flagged apps; APK size where the volume is unavailable.
        val totalBytes = unused.sumOf { storage.measure(it.app.packageName)?.totalBytes ?: it.app.apkBytes }
        notifications.postReminder(unused.map { it.app.packageName }, threshold, totalBytes)
        preferences.recordReminder(unused.size, now)
        return Result.success()
    }
}

/**
 * The free monthly nudge (PRD §2): "Time for a cleanup? You have 126 apps installed". Checked
 * weekly but posted at most once per 30 days, so the month lands within a week of being due
 * instead of drifting a whole period whenever WorkManager runs a few minutes early.
 */
@HiltWorker
class CleanupNudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val premium: Premium,
    private val inventory: AppInventory,
    private val preferences: AppPreferences,
    private val notifications: ReminderNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!notifications.canPost()) return Result.success()
        val isPremium = premium.awaitPremium()
        val now = System.currentTimeMillis()
        val apps = if (isPremium) emptyList() else inventory.awaitApps()
        if (!ReminderPolicy.shouldNudge(isPremium, apps.size, preferences.lastNudgeAt.first(), now)) {
            return Result.success()
        }
        notifications.postNudge(apps.size)
        preferences.setLastNudgeAt(now)
        return Result.success()
    }
}

/**
 * In a worker-only process the entitlement may still be loading from RevenueCat's cache; give it
 * a moment before concluding "not premium" (a wrong answer here only costs one skipped week).
 */
private suspend fun Premium.awaitPremium(): Boolean =
    isPremium.value || withTimeoutOrNull(PREMIUM_WAIT_MILLIS) { isPremium.first { it } } == true

/** Cold worker process: the inventory's Room snapshot may not have landed yet. */
private suspend fun AppInventory.awaitApps(): List<InstalledApp> {
    apps.value.takeIf { it.isNotEmpty() }?.let { return it }
    withTimeoutOrNull(INVENTORY_WAIT_MILLIS) { apps.first { it.isNotEmpty() } }?.let { return it }
    refresh()
    return apps.value
}

private const val PREMIUM_WAIT_MILLIS = 5_000L
private const val INVENTORY_WAIT_MILLIS = 3_000L
