package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UNUSED_THRESHOLD_DAYS
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real "Scan my phone" pass: refreshes inventory, usage and storage in that order (each step
 * feeds the next — usage and sizes are keyed by the fresh inventory), then derives the numbers
 * with [ScanMath]. The result lives in memory for the session and in [ScanResultStore] for the
 * next cold start.
 *
 * A scan is also kept true afterwards: when the installed set changes (the user just freed space,
 * or removed an app from the launcher) the numbers are recomputed from the current data, so Home
 * never keeps promising gigabytes that are already gone.
 */
@Singleton
class DefaultCleanupScan @Inject constructor(
    private val inventory: AppInventory,
    private val usageInsights: UsageInsights,
    private val storage: StorageBreakdown,
    private val usageAccess: UsageAccess,
    private val store: ScanResultStore,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : CleanupScan {

    private val _result = MutableStateFlow<ScanResult?>(null)
    override val result: StateFlow<ScanResult?> = _result.asStateFlow()

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    override val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val mutex = Mutex()

    /** The installed set the current [result] describes; null until it is known. */
    @Volatile private var scannedPackages: Set<String>? = null

    init {
        scope.launch {
            val saved = store.load()
            // A scan that finished while we were loading is newer than the saved one.
            if (saved != null && _result.value == null) {
                _result.compareAndSet(null, saved)
                scannedPackages = store.loadPackages()
            }
        }
        scope.launch { inventory.apps.collect(::onInventoryChanged) }
    }

    override suspend fun run(): ScanResult = mutex.withLock {
        try {
            _progress.value = ScanProgress(ScanStep.APPS, 0f)
            inventory.refresh()
            _progress.value = ScanProgress(ScanStep.USAGE, 1f / 3)
            usageAccess.recheck()
            usageInsights.refresh()
            _progress.value = ScanProgress(ScanStep.STORAGE, 2f / 3)
            storage.refresh()
            _progress.value = ScanProgress(ScanStep.DONE, 1f)
            val apps = inventory.apps.value
            val computed = compute(apps, scannedAt = System.currentTimeMillis())
            publish(computed, apps)
            computed
        } finally {
            _progress.value = null
        }
    }

    private suspend fun compute(apps: List<InstalledApp>, scannedAt: Long): ScanResult {
        val threshold = UNUSED_THRESHOLD_DAYS
        val hasAccess = usageAccess.isGranted.value
        val device = withContext(io) {
            runCatching { storage.deviceStorage() }.getOrDefault(DeviceStorage(totalBytes = 0, freeBytes = 0))
        }
        return ScanMath.compute(
            apps = apps,
            unused = if (hasAccess) usageInsights.unusedApps(apps, threshold) else emptyList(),
            sizes = storage.sizes.value,
            storage = device,
            thresholdDays = threshold,
            hasUsageAccess = hasAccess,
            now = scannedAt,
        )
    }

    private suspend fun publish(result: ScanResult, apps: List<InstalledApp>) {
        val packages = apps.mapTo(HashSet(apps.size)) { it.packageName }
        scannedPackages = packages
        _result.value = result
        store.save(result, packages)
    }

    private suspend fun onInventoryChanged(apps: List<InstalledApp>) {
        val previous = _result.value ?: return
        val known = scannedPackages ?: return
        if (apps.isEmpty() || inventory.isInitialLoading.value) return
        val current = apps.mapTo(HashSet(apps.size)) { it.packageName }
        if (current == known) return
        // Usage data not loaded yet (cold start): recomputing now would read "0 unused" — wait for run().
        if (previous.hasUsageAccess && usageInsights.lastUsed.value.isEmpty()) return
        mutex.withLock {
            if (_result.value?.scannedAt != previous.scannedAt) return
            publish(compute(apps, scannedAt = previous.scannedAt), apps)
        }
    }
}
