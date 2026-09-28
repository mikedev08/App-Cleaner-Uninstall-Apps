package com.jedy.appcleaner.uninstaller.data.usage

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps that work constantly without ever being "opened" (PRD Feature 3 exclusions, §6 item 9):
 * default launcher, keyboard, SMS app and dialer, enabled accessibility services and notification
 * listeners, active device admins. Suggesting one of these as unused would be a bad recommendation
 * dressed up as a smart one, so the finder never lists them and the Confirm Sheet warns on them.
 *
 * Read live on every call — the user can switch keyboard or launcher at any moment — and each
 * source is isolated so one OEM quirk cannot hide the others.
 */
@Singleton
class AlwaysRunningApps @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun packages(): Set<String> = buildSet {
        addIfPresent { defaultLauncher() }
        addIfPresent {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let(ComponentName::unflattenFromString)?.packageName
        }
        addIfPresent { Telephony.Sms.getDefaultSmsPackage(context) }
        addIfPresent { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
        addAllSafely {
            UnusedRule.packagesFromComponentList(
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            )
        }
        addAllSafely {
            UnusedRule.packagesFromComponentList(
                Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
            )
        }
        addAllSafely {
            context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty()
                .map { it.packageName }
                .toSet()
        }
    }

    private fun defaultLauncher(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        // "android" is the chooser shown when no default launcher is set — not a real launcher.
        return resolved?.activityInfo?.packageName?.takeIf { it != "android" }
    }

    private inline fun MutableSet<String>.addIfPresent(block: () -> String?) {
        runCatching(block).getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
    }

    private inline fun MutableSet<String>.addAllSafely(block: () -> Set<String>) {
        runCatching(block).getOrNull()?.let(::addAll)
    }

    private companion object {
        /** `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` is hidden API; the key itself is stable. */
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }
}
