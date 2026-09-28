package com.jedy.appcleaner.uninstaller.data.uninstall

import com.jedy.appcleaner.uninstaller.core.analytics.SnapshotDegradedReason
import kotlinx.coroutines.flow.Flow

/**
 * The Android side of the queue, behind interfaces so [UninstallEngine] stays plain Kotlin and
 * unit-testable. Implementations: [PackageInstallerRemover], [FileIconSnapshotStore],
 * [SystemAppWarnings].
 */

/** PRD §0 decision 1: Android uninstalls, we queue. */
interface PackageRemover {
    /** Every status PackageInstaller reports, tagged with the queue item it belongs to. */
    val events: Flow<RemovalEvent>

    /** `PackageInstaller.uninstall()`; the answer arrives on [events], never as a return value. */
    fun requestUninstall(itemId: Long, packageName: String)

    /** `DevicePolicyManager.getActiveAdmins()` contains it (PRD §6 item 1). */
    fun isDeviceAdmin(packageName: String): Boolean
}

/**
 * PRD §0 decision 2: once a package is gone, PackageManager has no icon for it, so each one is
 * saved as a PNG before its dialog can open. History and the Result screen render from these.
 */
interface IconSnapshotStore {
    /**
     * Saves the icon PNG, unless the phone is almost out of space (PRD §6 "Storage almost full"):
     * then the snapshot is text-only and History draws a neutral placeholder.
     */
    suspend fun save(packageName: String, key: String): IconSnapshot

    suspend fun delete(paths: Collection<String>)

    /** Deletes every snapshot not in [keep] (pruned history rows, failed items of old batches). */
    suspend fun sweep(keep: Set<String>)

    /** Drops the live-icon cache entry so a later reinstall is not served the old icon. */
    fun evictLiveIcon(packageName: String)
}

/**
 * [path] is the saved PNG, or null for a text-only snapshot. [degraded] says why the PNG was
 * skipped for lack of space; it is null when the icon simply could not be read (no degradation).
 */
data class IconSnapshot(val path: String?, val degraded: SnapshotDegradedReason? = null)

/** Confirm Sheet chips (PRD §6 items 1 and 9). */
interface AppWarnings {
    suspend fun warningsFor(packages: Collection<String>): Map<String, List<AppWarning>>
}
