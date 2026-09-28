package com.jedy.appcleaner.uninstaller.data.usage

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen). Usage Access (PACKAGE_USAGE_STATS special access). PRD Feature 3.
 */
interface UsageAccess {
    val isGranted: StateFlow<Boolean>

    /** Re-reads AppOpsManager. Call from every onResume (PRD §6 item 12). */
    fun recheck()

    /**
     * The Settings intent to grant access: package-specific page where supported, plain list
     * otherwise (PRD §6 item 13). [usesFallbackPage] tells the UI to show the "find us" hint.
     */
    fun settingsIntent(): UsageAccessIntent
}

data class UsageAccessIntent(val intent: Intent, val usesFallbackPage: Boolean)
