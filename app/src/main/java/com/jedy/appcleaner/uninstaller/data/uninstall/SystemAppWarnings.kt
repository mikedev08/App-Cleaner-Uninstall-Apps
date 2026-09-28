package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default-app and device-admin warnings for the Confirm Sheet (PRD §6 items 1 and 9).
 *
 * [UsageInsights.alwaysRunningPackages] says *that* an app runs constantly; the roles here say
 * *why*, so the chip can read "Your current keyboard" instead of a generic caution. Each lookup
 * is individually guarded: a missing telephony stack on a tablet must not hide the other chips.
 */
@Singleton
class SystemAppWarnings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remover: PackageRemover,
    private val usageInsights: UsageInsights,
) : AppWarnings {

    override suspend fun warningsFor(packages: Collection<String>): Map<String, List<AppWarning>> =
        withContext(Dispatchers.IO) {
            val keyboard = safe { defaultKeyboard() }
            val launcher = safe { defaultLauncher() }
            val sms = safe { Telephony.Sms.getDefaultSmsPackage(context) }
            val dialer = safe { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            val alwaysRunning = safe { usageInsights.alwaysRunningPackages() }.orEmpty()

            packages.associateWith { pkg ->
                buildList {
                    if (safe { remover.isDeviceAdmin(pkg) } == true) add(AppWarning.DEVICE_ADMIN)
                    if (pkg == keyboard) add(AppWarning.KEYBOARD)
                    if (pkg == launcher) add(AppWarning.LAUNCHER)
                    if (pkg == sms) add(AppWarning.SMS)
                    if (pkg == dialer) add(AppWarning.DIALER)
                    if (isEmpty() && pkg in alwaysRunning) add(AppWarning.ALWAYS_RUNNING)
                }
            }.filterValues { it.isNotEmpty() }
        }

    private fun defaultKeyboard(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName

    private fun defaultLauncher(): String? =
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }
}
