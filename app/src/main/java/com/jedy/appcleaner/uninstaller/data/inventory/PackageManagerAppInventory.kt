package com.jedy.appcleaner.uninstaller.data.inventory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.local.InventoryDao
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app inventory (PRD Feature 1): every user-installed app, read from PackageManager and
 * mirrored into Room so a cold start renders instantly.
 *
 * Lifecycle:
 *  1. On creation the Room snapshot is loaded into [apps] (the cache-first half).
 *  2. [refresh] rescans PackageManager, diffs against Room and writes only what changed.
 *  3. A context-registered package receiver ([registerPackageReceiver], wired by
 *     [InventoryStartup]) patches single apps as they are added, replaced or removed, so an app
 *     removed from the launcher meanwhile disappears without a manual refresh.
 *
 * Every mutation of [apps] and of the Room table runs under one [Mutex], so a receiver patch can
 * never be overwritten by a scan that started before it. The inventory never leaves the device:
 * nothing here logs a package name or label (PRD §0 decision 4).
 */
@Singleton
class PackageManagerAppInventory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: InventoryDao,
    private val analytics: Analytics,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AppInventory, InventoryHealth {

    private val packageManager: PackageManager get() = context.packageManager
    private val ownPackage: String = context.packageName

    private val mutex = Mutex()
    private val refreshLock = Any()
    @Volatile private var inFlightRefresh: Deferred<Unit>? = null

    private val receiverRegistered = AtomicBoolean(false)
    private val incompleteLogged = AtomicBoolean(false)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    /** Starts true: until Room has answered we don't know whether this is the first-ever scan. */
    private val _isInitialLoading = MutableStateFlow(true)
    override val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isIncomplete = MutableStateFlow(false)
    override val isIncomplete: StateFlow<Boolean> = _isIncomplete.asStateFlow()

    init {
        scope.launch(io) { loadCache() }
    }

    /**
     * Full rescan. Concurrent callers (process start, onboarding warm-up, pull-to-refresh) share
     * one in-flight scan instead of queueing duplicates. The scan itself runs on the application
     * scope, so a caller leaving the screen can't abort it halfway through the Room write.
     */
    override suspend fun refresh() {
        val job = synchronized(refreshLock) {
            inFlightRefresh?.takeIf { it.isActive }
                ?: scope.async(io) { rescan() }.also { inFlightRefresh = it }
        }
        job.await()
    }

    override suspend fun find(packageName: String): InstalledApp? = withContext(io) {
        runCatching { packageInfo(packageName) }.getOrNull()
            ?.takeIf { it.packageName != ownPackage }
            ?.toInstalledApp()
    }

    override fun isInstalled(packageName: String): Boolean = try {
        packageInfo(packageName)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Registers the live-update receiver on the application context. Idempotent. Package
     * broadcasts come from the system, which reaches a not-exported receiver, so nothing else on
     * the device can spoof one.
     */
    fun registerPackageReceiver() {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName == ownPackage) return
            // An update arrives as REMOVED(replacing) + ADDED(replacing) + REPLACED; only the last
            // one describes the final state, so the replacing halves are ignored.
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> if (!replacing) scope.launch(io) { patchRemoved(packageName) }
                Intent.ACTION_PACKAGE_ADDED -> if (!replacing) scope.launch(io) { patchChanged(packageName) }
                Intent.ACTION_PACKAGE_REPLACED -> scope.launch(io) { patchChanged(packageName) }
            }
        }
    }

    private suspend fun loadCache() = mutex.withLock {
        // A scan that won the lock first is newer than anything in Room.
        if (_apps.value.isNotEmpty()) return@withLock
        val cached = runCatching { dao.getAll() }
            .onFailure { Log.w(TAG, "Inventory cache unreadable", it) }
            .getOrDefault(emptyList())
            .map { it.toModel() }
        if (cached.isNotEmpty()) {
            _apps.value = InventoryRules.ordered(cached)
            _isInitialLoading.value = false
        }
    }

    private suspend fun rescan() = mutex.withLock {
        val live = runCatching { scanUserApps() }
            .onFailure { Log.w(TAG, "PackageManager scan failed", it) }
            .getOrNull()
        if (live == null) {
            // Keep whatever the cache showed; a failed scan must not blank the list.
            _isInitialLoading.value = false
            return@withLock
        }
        runCatching {
            val cached = dao.getAll().map { it.toModel() }
            val diff = InventoryRules.diff(cached, live)
            if (!diff.isEmpty) {
                val now = System.currentTimeMillis()
                if (diff.upserts.isNotEmpty()) dao.upsertAll(diff.upserts.map { it.toEntity(now) })
                // Stay well under SQLite's bound-variable limit on very large removals.
                diff.removedPackages.chunked(DELETE_CHUNK).forEach { dao.deleteAll(it) }
            }
        }.onFailure { Log.w(TAG, "Inventory cache write failed", it) }

        _apps.value = InventoryRules.ordered(live)
        _isInitialLoading.value = false
        reportCompleteness(lastVisiblePackageCount)
    }

    private suspend fun patchChanged(packageName: String) = mutex.withLock {
        val app = runCatching { packageInfo(packageName) }.getOrNull()?.toUserAppOrNull()
        if (app == null) {
            removeLocked(packageName)
            return@withLock
        }
        _apps.update { InventoryRules.upsert(it, app) }
        runCatching { dao.upsertAll(listOf(app.toEntity(System.currentTimeMillis()))) }
            .onFailure { Log.w(TAG, "Inventory cache write failed", it) }
    }

    private suspend fun patchRemoved(packageName: String) = mutex.withLock {
        // The broadcast can race a reinstall; trust PackageManager over the intent.
        if (isInstalled(packageName)) return@withLock
        removeLocked(packageName)
    }

    private suspend fun removeLocked(packageName: String) {
        _apps.update { InventoryRules.remove(it, packageName) }
        runCatching { dao.delete(packageName) }
            .onFailure { Log.w(TAG, "Inventory cache delete failed", it) }
    }

    /** PRD §6 item 11 / §9 `inventory_incomplete`: logged at most once per process. */
    private fun reportCompleteness(visiblePackageCount: Int) {
        val incomplete = InventoryRules.isIncomplete(visiblePackageCount)
        _isIncomplete.value = incomplete
        if (incomplete && incompleteLogged.compareAndSet(false, true)) {
            analytics.log(AnalyticsEvent.InventoryIncomplete(visibleCount = visiblePackageCount))
        }
    }

    /** Every package the last scan could see, system ones included (PRD §6 item 11). */
    @Volatile private var lastVisiblePackageCount = Int.MAX_VALUE

    private fun scanUserApps(): List<InstalledApp> {
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }
        lastVisiblePackageCount = packages.size
        return packages.mapNotNull { it.toUserAppOrNull() }
    }

    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }

    private fun PackageInfo.toUserAppOrNull(): InstalledApp? {
        val info = applicationInfo ?: return null
        if (!InventoryRules.isUserApp(info.flags, packageName, ownPackage)) return null
        return toInstalledApp()
    }

    /** Blocking: `loadLabel` and the APK stat calls hit disk. Always called on [io]. */
    private fun PackageInfo.toInstalledApp(): InstalledApp? {
        val info = applicationInfo ?: return null
        val label = runCatching { info.loadLabel(packageManager).toString().trim() }.getOrNull()
        return InstalledApp(
            packageName = packageName,
            label = label?.takeIf { it.isNotEmpty() } ?: packageName,
            versionName = versionName,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            installerPackage = installerOf(packageName),
            apkBytes = InventoryRules.apkBytes(info.sourceDir, info.splitSourceDirs) { File(it).length() },
            storageUuid = info.storageUuid?.toString(),
            uid = info.uid,
        )
    }

    private fun installerOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private companion object {
        const val TAG = "AppInventory"
        const val DELETE_CHUNK = 500
    }
}
