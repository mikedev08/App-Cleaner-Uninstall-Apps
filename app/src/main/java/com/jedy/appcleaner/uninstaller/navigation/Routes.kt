package com.jedy.appcleaner.uninstaller.navigation

import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val SCAN = "scan"

    const val ARG_TAB = "tab"
    const val APPS = "apps/{$ARG_TAB}"
    fun apps(tab: com.jedy.appcleaner.uninstaller.core.model.HomeTab) = "apps/${tab.name}"

    const val ARG_SOURCE = "source"
    const val PAYWALL = "paywall/{$ARG_SOURCE}"
    fun paywall(source: PaywallSource) = "paywall/${source.value}"

    const val ARG_TRIGGER = "trigger"
    const val USAGE_ACCESS = "usage_access/{$ARG_TRIGGER}"
    fun usageAccess(trigger: UsageAccessTrigger) = "usage_access/${trigger.value}"

    const val ARG_BATCH = "batchId"
    const val UNINSTALL_PROGRESS = "uninstall/{$ARG_BATCH}"
    fun uninstallProgress(batchId: Long) = "uninstall/$batchId"

    const val UNINSTALL_RESULT = "result/{$ARG_BATCH}"
    fun uninstallResult(batchId: Long) = "result/$batchId"
}

/**
 * Extras on the reminder notification's launch intent (PRD Feature 4): open the Unused tab with
 * the flagged apps pre-selected. Package names travel only inside this local PendingIntent.
 */
object DeepLinks {
    const val EXTRA_OPEN_TAB = "com.jedy.appcleaner.extra.OPEN_TAB"
    const val EXTRA_PRESELECT = "com.jedy.appcleaner.extra.PRESELECT"
    const val EXTRA_REMINDER_COUNT = "com.jedy.appcleaner.extra.REMINDER_COUNT"
    const val EXTRA_REMINDER_THRESHOLD = "com.jedy.appcleaner.extra.REMINDER_THRESHOLD"
}
