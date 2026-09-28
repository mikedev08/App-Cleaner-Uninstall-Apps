package com.jedy.appcleaner.uninstaller.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jedy.appcleaner.uninstaller.MainActivity
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.locale.LocaleController
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.navigation.DeepLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cleanup reminder and the free nudge (PRD Feature 4, §2).
 *
 * Workers run outside any composition, so the in-app language (applied by LocalizedContent) is
 * not in force here; copy is resolved against the stored language explicitly, otherwise a user
 * who picked Hebrew on an English phone would get English notifications.
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val localeController: LocaleController,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates or renames (after a language change) the reminder channel. Idempotent. */
    suspend fun ensureChannel() {
        val localized = localizedContext()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.insights_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localized.getString(R.string.insights_reminder_channel_description)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** App-level switch, POST_NOTIFICATIONS on 13+, and our channel not muted by the user. */
    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * "9 apps unused for 60+ days · 2.1 GB. Review them?" Tapping opens the Unused tab with those
     * apps pre-selected. The package names ride only in this explicit, immutable, local
     * PendingIntent to our own activity; they never reach analytics or the notification text.
     */
    suspend fun postReminder(packages: List<String>, thresholdDays: Int, totalBytes: Long) {
        if (!canPost()) return
        ensureChannel()
        val localized = localizedContext()
        val count = packages.size
        val title = localized.resources.getQuantityString(
            R.plurals.insights_reminder_title, count, count, thresholdDays, formatBytes(localized, totalBytes),
        )
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DeepLinks.EXTRA_OPEN_TAB, HomeTab.UNUSED.name)
            putExtra(DeepLinks.EXTRA_PRESELECT, packages.toTypedArray())
            putExtra(DeepLinks.EXTRA_REMINDER_COUNT, count)
            putExtra(DeepLinks.EXTRA_REMINDER_THRESHOLD, thresholdDays)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.insights_ic_notification)
            .setContentTitle(title)
            .setContentText(localized.getString(R.string.insights_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingActivity(REQUEST_REMINDER, launch))
            .build()
        notify(ID_REMINDER, notification)
    }

    /** "Time for a cleanup? You have 126 apps installed" — needs no usage data (PRD §2). */
    suspend fun postNudge(installedCount: Int) {
        if (!canPost()) return
        ensureChannel()
        val localized = localizedContext()
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.insights_ic_notification)
            .setContentTitle(localized.getString(R.string.insights_nudge_title))
            .setContentText(
                localized.resources.getQuantityString(R.plurals.insights_nudge_text, installedCount, installedCount)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingActivity(REQUEST_NUDGE, launch))
            .build()
        notify(ID_NUDGE, notification)
    }

    private fun pendingActivity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(id: Int, notification: android.app.Notification) {
        // canPost() verified POST_NOTIFICATIONS; the explicit catch covers a revoke in between.
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: stay silent.
        }
    }

    private suspend fun localizedContext(): Context {
        val language = localeController.resolve(preferences.languageTag.first())
        val configuration = Configuration(context.resources.configuration).apply { setLocale(language.toLocale()) }
        return context.createConfigurationContext(configuration)
    }

    companion object {
        const val CHANNEL_ID = "cleanup_reminders"
        private const val ID_REMINDER = 4101
        private const val ID_NUDGE = 4102
        private const val REQUEST_REMINDER = 4101
        private const val REQUEST_NUDGE = 4102
    }
}
