package com.jedy.appcleaner.uninstaller.data.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-used data from UsageStatsManager (PRD Feature 3). Computed for anyone who granted access —
 * the count on the blurred teaser has to be real (PRD §0 decision 3) — and never leaves the device.
 *
 * [windowStart] stays 0 until the first successful [refresh] and returns to 0 when access is
 * revoked, so callers can tell "not read yet" from "read, nothing unused". Until then
 * [unusedApps] returns nothing: with no data every app would look unused, and a wrong "you can
 * remove these" is worse than a moment of loading.
 */
@Singleton
class UsageStatsUsageInsights @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: UsageAccess,
    private val alwaysRunning: AlwaysRunningApps,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : UsageInsights {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val mutex = Mutex()

    private val _lastUsed = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val lastUsed: StateFlow<Map<String, Long>> = _lastUsed.asStateFlow()

    private val _windowStart = MutableStateFlow(0L)
    override val windowStart: StateFlow<Long> = _windowStart.asStateFlow()

    override suspend fun refresh() = mutex.withLock {
        if (!access.isGranted.value) {
            clear()
            return@withLock
        }
        val now = System.currentTimeMillis()
        val records = withContext(io) {
            runCatching {
                // Yearly buckets are the only ones Android keeps long enough to answer "90 days".
                usageStats.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - UnusedRule.RETENTION_MILLIS, now)
                    .orEmpty()
                    .map { stats ->
                        UsageRecord(
                            packageName = stats.packageName,
                            lastTimeUsed = stats.lastTimeUsed,
                            lastTimeVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) stats.lastTimeVisible else 0L,
                            bucketStart = stats.firstTimeStamp,
                        )
                    }
            }.getOrNull()
        }
        if (records == null) {
            // SecurityException: the grant vanished between the check and the query.
            access.recheck()
            clear()
            return@withLock
        }
        val snapshot = UnusedRule.reduce(records, now)
        _lastUsed.value = snapshot.lastUsed
        _windowStart.value = snapshot.windowStart
    }

    override fun unusedApps(apps: List<InstalledApp>, thresholdDays: Int, now: Long): List<UnusedApp> {
        if (!access.isGranted.value || _windowStart.value == 0L) return emptyList()
        return UnusedRule.find(apps, _lastUsed.value, alwaysRunningPackages(), thresholdDays, now)
    }

    override fun alwaysRunningPackages(): Set<String> = alwaysRunning.packages()

    private fun clear() {
        _lastUsed.value = emptyMap()
        _windowStart.value = 0L
    }
}
