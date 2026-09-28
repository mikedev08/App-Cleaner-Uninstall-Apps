package com.jedy.appcleaner.uninstaller.data.storage

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen). Device storage (free, no permission) and per-app StorageStatsManager sizes
 * (need Usage Access; premium to display). PRD Features 1 and 3.
 */
interface StorageBreakdown {
    /** package -> measured size. Empty without Usage Access. */
    val sizes: StateFlow<Map<String, AppSize>>

    fun deviceStorage(): DeviceStorage

    /** Measures every inventory app (batched, cached, stale after 24h). No-op without access. */
    suspend fun refresh()

    /** One app, freshly measured; null without access or when the volume is unavailable. */
    suspend fun measure(packageName: String): AppSize?
}
