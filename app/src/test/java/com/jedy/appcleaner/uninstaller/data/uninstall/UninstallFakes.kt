package com.jedy.appcleaner.uninstaller.data.uninstall

import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.local.HistoryDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallBatchEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallHistoryEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccessIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

fun app(
    pkg: String,
    apkBytes: Long = 10_000_000,
    lastUpdateTime: Long = 0,
    installer: String? = InstalledApp.PLAY_STORE_PACKAGE,
) = InstalledApp(
    packageName = pkg,
    label = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
    versionName = "1.0",
    firstInstallTime = 0,
    lastUpdateTime = lastUpdateTime,
    installerPackage = installer,
    apkBytes = apkBytes,
    storageUuid = null,
    uid = 10_000,
)

class FakeHistoryDao : HistoryDao {
    val rows = MutableStateFlow<List<UninstallHistoryEntity>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<UninstallHistoryEntity>> = rows.map { list -> list.sortedByDescending { it.removedAt } }
    override suspend fun insert(entry: UninstallHistoryEntity): Long {
        val id = nextId++
        rows.update { it + entry.copy(id = id) }
        return id
    }
    override suspend fun clear() { rows.value = emptyList() }
    override suspend fun getIconPaths(): List<String> = rows.value.mapNotNull { it.iconPath }
    var pruneCalls = 0
    override suspend fun prune(keep: Int) {
        pruneCalls++
        rows.update { list -> list.sortedByDescending { it.removedAt }.take(keep) }
    }
}

class FakeUninstallDao(private val history: FakeHistoryDao) : UninstallDao {
    val batches = MutableStateFlow<Map<Long, UninstallBatchEntity>>(emptyMap())
    val items = MutableStateFlow<Map<Long, UninstallItemEntity>>(emptyMap())
    private var nextBatch = 1L
    private var nextItem = 1L

    override suspend fun insertBatch(batch: UninstallBatchEntity): Long {
        val id = nextBatch++
        batches.update { it + (id to batch.copy(batchId = id)) }
        return id
    }
    override suspend fun updateBatch(batch: UninstallBatchEntity) = batches.update { it + (batch.batchId to batch) }
    override suspend fun getBatch(batchId: Long) = batches.value[batchId]
    override fun observeBatch(batchId: Long) = batches.map { it[batchId] }
    override fun observeUnfinishedBatch() = batches.map { all ->
        all.values.filter { it.finishedAt == null && !it.discarded }.maxByOrNull { it.createdAt }
    }
    override suspend fun insertItems(items: List<UninstallItemEntity>) {
        items.forEach { item ->
            val id = nextItem++
            this.items.update { it + (id to item.copy(id = id)) }
        }
    }
    override suspend fun updateItem(item: UninstallItemEntity) = items.update { it + (item.id to item) }
    override suspend fun getItems(batchId: Long) = items.value.values.filter { it.batchId == batchId }.sortedBy { it.position }
    override fun observeItems(batchId: Long) = items.map { all ->
        all.values.filter { it.batchId == batchId }.sortedBy { it.position }
    }
    override suspend fun insertHistoryEntry(entry: UninstallHistoryEntity) = history.insert(entry)
    override suspend fun getUnfinishedIconPaths(): List<String> {
        val open = batches.value.values.filter { it.finishedAt == null && !it.discarded }.map { it.batchId }.toSet()
        return items.value.values.filter { it.batchId in open }.mapNotNull { it.iconPath }
    }
    override suspend fun deleteItemsOfClosedBatchesBefore(before: Long) = Unit
    override suspend fun deleteClosedBatchesBefore(before: Long) = Unit

    fun item(batchId: Long, pkg: String) = items.value.values.single { it.batchId == batchId && it.packageName == pkg }
    fun state(batchId: Long, pkg: String) = ItemState.of(item(batchId, pkg).state)
}

class FakeInventory(initial: List<InstalledApp>) : AppInventory {
    val installed = initial.associateBy { it.packageName }.toMutableMap()
    override val apps = MutableStateFlow(initial)
    override val isInitialLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun refresh() = Unit
    override suspend fun find(packageName: String): InstalledApp? = installed[packageName]
    override fun isInstalled(packageName: String): Boolean = packageName in installed
}

class FakeStorage(private val measured: Map<String, AppSize> = emptyMap()) : StorageBreakdown {
    override val sizes: StateFlow<Map<String, AppSize>> = MutableStateFlow(measured)
    override fun deviceStorage() = DeviceStorage(0, 0)
    override suspend fun refresh() = Unit
    override suspend fun measure(packageName: String): AppSize? = measured[packageName]
}

class FakeUsageAccess(granted: Boolean) : UsageAccess {
    override val isGranted: StateFlow<Boolean> = MutableStateFlow(granted)
    override fun recheck() = Unit
    override fun settingsIntent(): UsageAccessIntent = throw UnsupportedOperationException()
}

class FakePremium(premium: Boolean) : Premium {
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(premium)
}

class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { events += event }
}

class FakeIcons : IconSnapshotStore {
    val evicted = mutableListOf<String>()
    override suspend fun save(packageName: String, key: String) = "/icons/$key.png"
    override suspend fun delete(paths: Collection<String>) = Unit
    override suspend fun sweep(keep: Set<String>) = Unit
    override fun evictLiveIcon(packageName: String) { evicted += packageName }
}

class FakeWarnings(private val flagged: Map<String, List<AppWarning>> = emptyMap()) : AppWarnings {
    override suspend fun warningsFor(packages: Collection<String>) = flagged.filterKeys { it in packages }
}

/**
 * Stands in for PackageInstaller. [script] decides what each request answers immediately; a null
 * answer leaves the request pending so the test can drive it (dialogs, Stop, leaving the app).
 */
class FakeRemover(private val inventory: FakeInventory) : PackageRemover {
    private val _events = MutableSharedFlow<RemovalEvent>(extraBufferCapacity = 64)
    override val events: Flow<RemovalEvent> = _events
    val requests = mutableListOf<Pair<Long, String>>()
    var admins: Set<String> = emptySet()

    /** package -> status answered at once. SUCCESS also removes the package, unless in [keepInstalled]. */
    val script = mutableMapOf<String, Int>()
    val keepInstalled = mutableSetOf<String>()

    override fun requestUninstall(itemId: Long, packageName: String) {
        requests += itemId to packageName
        script[packageName]?.let { finish(itemId, packageName, it) }
    }

    fun finish(itemId: Long, packageName: String, status: Int) {
        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS && packageName !in keepInstalled) {
            inventory.installed.remove(packageName)
        }
        _events.tryEmit(RemovalEvent.Finished(itemId, status))
    }

    fun needsConfirmation(itemId: Long) {
        _events.tryEmit(RemovalEvent.NeedsConfirmation(itemId) { true })
    }

    override fun isDeviceAdmin(packageName: String) = packageName in admins
}
