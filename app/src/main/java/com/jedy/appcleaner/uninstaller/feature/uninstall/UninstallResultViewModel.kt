package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.format.bytesBucket
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.uninstall.BatchSummary
import com.jedy.appcleaner.uninstaller.data.uninstall.Clock
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.UninstallEngine
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The Result screen's teaser card (PRD §4 Screen 10). [count] 0 hides it. [bytes] is what those
 * apps still occupy; [isEstimate] = APK-only sizes, so the card says "about".
 */
data class UnusedTeaser(
    val count: Int,
    val thresholdDays: Int,
    val bytes: Long = 0,
    val isEstimate: Boolean = true,
)

data class ResultUiState(
    val summary: BatchSummary? = null,
    val teaser: UnusedTeaser = UnusedTeaser(0, AppPreferences.DEFAULT_UNUSED_DAYS),
    val isRetrying: Boolean = false,
    /** Gauge before → after. Null until the batch is finished and storage has been read. */
    val storageDrop: StorageDrop? = null,
)

/**
 * PRD §4 Screen 10 and the North Star event (PRD §9). Everything shown comes from Room: only
 * verified removals are counted, so "2.4 GB freed" is what actually came back (PRD §6 item 6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UninstallResultViewModel @Inject constructor(
    private val engine: UninstallEngine,
    private val inventory: AppInventory,
    private val usageAccess: UsageAccess,
    private val usageInsights: UsageInsights,
    private val storage: StorageBreakdown,
    private val preferences: AppPreferences,
    private val premium: Premium,
    private val analytics: Analytics,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val batchId = MutableStateFlow<Long?>(null)
    private val retrying = MutableStateFlow(false)

    private val _retried = Channel<Long>(Channel.BUFFERED)
    val retried: Flow<Long> = _retried.receiveAsFlow()

    private val summary = batchId.filterNotNull().flatMapLatest(engine::observeSummary)

    private val teaser: Flow<UnusedTeaser> = combine(
        usageAccess.isGranted, inventory.apps, preferences.unusedThresholdDays, summary, storage.sizes,
    ) { granted, apps, days, current, sizes ->
        if (!granted) return@combine UnusedTeaser(0, days)
        // The inventory may not have caught up with this batch yet; never tease an app just removed.
        val gone = current?.items
            ?.filter { ItemState.of(it.state) == ItemState.REMOVED || ItemState.of(it.state) == ItemState.ALREADY_REMOVED }
            ?.mapTo(HashSet()) { it.packageName }
            .orEmpty()
        runCatching {
            val unused = usageInsights.unusedApps(apps.filterNot { it.packageName in gone }, days, clock.now())
            ResultMath.unusedTeaser(unused, sizes, days)
        }.getOrDefault(UnusedTeaser(0, days))
    }.flowOn(Dispatchers.Default)

    /**
     * Read once per batch, after it finished: re-reading on every emission would let the "before"
     * drift as the OS settles, and the gauge must drop exactly by what this batch freed.
     */
    private val storageDrop = MutableStateFlow<StorageDrop?>(null)

    val uiState: StateFlow<ResultUiState> = combine(summary, teaser, retrying, storageDrop) { current, unused, isRetrying, drop ->
        ResultUiState(summary = current, teaser = unused, isRetrying = isRetrying, storageDrop = drop)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState())

    fun bind(id: Long) {
        if (batchId.value == id) return
        batchId.value = id
        viewModelScope.launch { logCompletionOnce(id) }
        viewModelScope.launch { readStorageDrop(id) }
    }

    private suspend fun readStorageDrop(id: Long) {
        val done = engine.observeSummary(id).filterNotNull().first { it.isFinished }
        storageDrop.value = withContext(Dispatchers.IO) {
            runCatching { ResultMath.storageDrop(storage.deviceStorage(), done.freedBytes) }.getOrNull()
        }
    }

    /** North Star (PRD §9): once per batch, only with at least one app removed. Survives rotation and process death. */
    private suspend fun logCompletionOnce(id: Long) {
        val key = "$KEY_LOGGED$id"
        if (savedState.get<Boolean>(key) == true) return
        val done = engine.observeSummary(id).filterNotNull().first { it.isFinished }
        if (done.removedCount < 1) return
        savedState[key] = true
        val finishedAt = done.batch.finishedAt ?: clock.now()
        analytics.log(
            AnalyticsEvent.UninstallBatchCompleted(
                removedCount = done.removedCount,
                skippedCount = done.skippedCount,
                failedCount = done.failedCount,
                bytesFreedBucket = bytesBucket(done.freedBytes),
                bytesIsEstimate = done.freedIsEstimate,
                secondsToComplete = ((finishedAt - done.batch.createdAt) / 1000).coerceAtLeast(0),
                stoppedEarly = done.batch.stoppedEarly,
                isPremium = premium.isPremium.value,
            )
        )
    }

    /** "Try again": the given apps become a fresh batch with fresh snapshots. */
    fun retry(packages: List<String>) {
        val id = batchId.value ?: return
        if (packages.isEmpty() || retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            val newId = runCatching { engine.retry(id, packages) }.getOrNull()
            retrying.value = false
            if (newId != null) _retried.send(newId)
        }
    }

    private companion object {
        const val KEY_LOGGED = "completed_logged_"
    }
}
