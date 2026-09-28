package com.jedy.appcleaner.uninstaller.data.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.local.AppSizeDao
import com.jedy.appcleaner.uninstaller.data.local.AppSizeEntity
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device storage (free, no permission) and per-app StorageStatsManager sizes (PRD Features 1, 3).
 *
 * Sizes are cached in Room so the Large tab renders instantly on the next launch, and re-measured
 * only when stale (24h, or the app was updated). Measurement streams in batches of 20 so a
 * 500-app phone fills the list progressively instead of behind one long spinner (PRD §6 item 10).
 *
 * Honesty rules: an app whose volume is unmounted is *omitted* — the UI says "Size unavailable",
 * never 0 B (§6 item 10) — and without Usage Access [sizes] is empty and the cache is wiped, so
 * stale numbers are never presented as current (§6 item 12).
 */
@Singleton
class StorageStatsBreakdown @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: UsageAccess,
    private val inventory: AppInventory,
    private val dao: AppSizeDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope scope: CoroutineScope,
) : StorageBreakdown {

    private val statsManager: StorageStatsManager? = context.getSystemService(StorageStatsManager::class.java)
    private val mutex = Mutex()

    override val sizes: StateFlow<Map<String, AppSize>> =
        combine(access.isGranted, dao.observeAll()) { granted, rows ->
            if (granted) rows.associate { it.packageName to it.toModel() } else emptyMap()
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun deviceStorage(): DeviceStorage = try {
        val manager = checkNotNull(statsManager)
        DeviceStorage(
            totalBytes = manager.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = manager.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
    } catch (_: Exception) {
        // IOException (volume busy) or an OEM without the service: StatFs never needs a permission.
        val stat = StatFs(Environment.getDataDirectory().path)
        DeviceStorage(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }

    override suspend fun refresh() = mutex.withLock {
        withContext(io) { refreshLocked() }
    }

    private suspend fun refreshLocked() {
        if (!access.isGranted.value) {
            dao.clear()
            return
        }
        val apps = inventory.apps.value
        // An empty inventory means "not loaded yet", not "no apps": don't prune the cache on it.
        if (apps.isEmpty()) return
        val now = System.currentTimeMillis()
        val cached = dao.getAll().associateBy { it.packageName }

        val installed = apps.mapTo(HashSet()) { it.packageName }
        deleteChunked(cached.keys.filter { it !in installed })

        val due = apps.filter { app ->
            val row = cached[app.packageName]
            AppSizeMath.isStale(row?.measuredAt, row?.packageUpdatedAt, app.lastUpdateTime, now)
        }
        for (batch in due.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val measured = ArrayList<AppSizeEntity>(batch.size)
            val unavailable = ArrayList<String>()
            for (app in batch) {
                when (val result = query(app.packageName, parseUuid(app.storageUuid))) {
                    is Measurement.Ok -> measured += result.size.toEntity(app.packageName, app.lastUpdateTime)
                    Measurement.Unavailable -> unavailable += app.packageName
                    Measurement.AccessLost -> {
                        access.recheck()
                        dao.clear()
                        return
                    }
                }
            }
            if (measured.isNotEmpty()) dao.upsertAll(measured)
            deleteChunked(unavailable)
        }
    }

    override suspend fun measure(packageName: String): AppSize? {
        if (!access.isGranted.value) return null
        return withContext(io) {
            val info = try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                dao.deleteAll(listOf(packageName))
                return@withContext null
            }
            val uuid = info.applicationInfo?.storageUuid ?: StorageManager.UUID_DEFAULT
            when (val result = query(packageName, uuid)) {
                is Measurement.Ok -> result.size.also { dao.upsertAll(listOf(it.toEntity(packageName, info.lastUpdateTime))) }
                Measurement.Unavailable -> {
                    dao.deleteAll(listOf(packageName))
                    null
                }
                Measurement.AccessLost -> {
                    access.recheck()
                    null
                }
            }
        }
    }

    private fun query(packageName: String, uuid: UUID): Measurement {
        val manager = statsManager ?: return Measurement.Unavailable
        return try {
            val stats = manager.queryStatsForPackage(uuid, packageName, Process.myUserHandle())
            val externalCache = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stats.externalCacheBytes else 0L
            Measurement.Ok(
                AppSizeMath.fromStats(
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                    externalCacheBytes = externalCache,
                    measuredAt = System.currentTimeMillis(),
                )
            )
        } catch (_: SecurityException) {
            Measurement.AccessLost
        } catch (_: PackageManager.NameNotFoundException) {
            Measurement.Unavailable
        } catch (_: IOException) {
            // Adopted storage / SD card that is currently unmounted (PRD §6 item 10).
            Measurement.Unavailable
        } catch (_: IllegalArgumentException) {
            Measurement.Unavailable
        }
    }

    private suspend fun deleteChunked(packages: List<String>) {
        packages.chunked(DELETE_CHUNK).forEach { dao.deleteAll(it) }
    }

    private sealed interface Measurement {
        data class Ok(val size: AppSize) : Measurement
        data object Unavailable : Measurement
        data object AccessLost : Measurement
    }

    private companion object {
        const val BATCH_SIZE = 20
        const val DELETE_CHUNK = 500

        /** Query on the app's own volume (§6 item 10); UUID_DEFAULT only when the inventory has none. */
        fun parseUuid(value: String?): UUID =
            value?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: StorageManager.UUID_DEFAULT
    }
}

private fun AppSizeEntity.toModel() = AppSize(
    appBytes = appBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    measuredAt = measuredAt,
)

private fun AppSize.toEntity(packageName: String, packageUpdatedAt: Long) = AppSizeEntity(
    packageName = packageName,
    appBytes = appBytes,
    dataBytes = dataBytes,
    cacheBytes = cacheBytes,
    measuredAt = measuredAt,
    packageUpdatedAt = packageUpdatedAt,
)
