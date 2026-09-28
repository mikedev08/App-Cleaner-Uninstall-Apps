package com.jedy.appcleaner.uninstaller.data.usage

import android.content.Intent
import android.provider.Settings
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.data.reminders.CleanupReminders
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** STUBS — replaced by the Insights feature. */
@Singleton
class StubUsageAccess @Inject constructor() : UsageAccess {
    override val isGranted: StateFlow<Boolean> = MutableStateFlow(false)
    override fun recheck() = Unit
    override fun settingsIntent() = UsageAccessIntent(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), usesFallbackPage = true)
}

@Singleton
class StubUsageInsights @Inject constructor() : UsageInsights {
    override val lastUsed: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
    override val windowStart: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun refresh() = Unit
    override fun unusedApps(apps: List<InstalledApp>, thresholdDays: Int, now: Long): List<UnusedApp> = emptyList()
    override fun alwaysRunningPackages(): Set<String> = emptySet()
}

@Singleton
class StubStorageBreakdown @Inject constructor() : StorageBreakdown {
    override val sizes: StateFlow<Map<String, AppSize>> = MutableStateFlow(emptyMap())
    override fun deviceStorage() = DeviceStorage(totalBytes = 0, freeBytes = 0)
    override suspend fun refresh() = Unit
    override suspend fun measure(packageName: String): AppSize? = null
}

@Singleton
class StubCleanupReminders @Inject constructor() : CleanupReminders {
    override fun schedule() = Unit
    override fun cancel() = Unit
}
