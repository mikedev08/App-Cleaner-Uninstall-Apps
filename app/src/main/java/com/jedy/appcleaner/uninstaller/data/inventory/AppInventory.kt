package com.jedy.appcleaner.uninstaller.data.inventory

import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen): other features depend on these members. The owner may add members but must
 * not rename or remove these. PRD Feature 1.
 *
 * User-installed apps only (system apps hidden in V1), never including App Cleaner itself.
 */
interface AppInventory {
    /** Cache-first: emits the Room snapshot immediately, then live refreshes. */
    val apps: StateFlow<List<InstalledApp>>

    /** True while the first-ever scan runs (shimmer rows). */
    val isInitialLoading: StateFlow<Boolean>

    /** Full rescan against PackageManager (pull-to-refresh, onboarding warm-up). */
    suspend fun refresh()

    /** Live PackageManager lookup; null when the package is not installed. */
    suspend fun find(packageName: String): InstalledApp?

    /** Cheap live check used by the uninstall engine's post-success verification. */
    fun isInstalled(packageName: String): Boolean
}
