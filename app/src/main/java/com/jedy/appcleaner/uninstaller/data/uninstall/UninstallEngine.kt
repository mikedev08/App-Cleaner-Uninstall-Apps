package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.pm.PackageInstaller
import android.database.sqlite.SQLiteFullException
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.analytics.CancelStreakAction
import com.jedy.appcleaner.uninstaller.core.analytics.SnapshotDegradedReason
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.format.bytesBucket
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.local.HistoryDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallBatchEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallHistoryEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** An unfinished batch the Home banner offers to finish (PRD §6 item 4). */
data class ResumableBatch(val batchId: Long, val remaining: Int)

/**
 * The batch uninstall queue (PRD Feature 2, §6 items 1–7 and 22).
 *
 * Room is the source of truth: every transition is written before the next step, so rotation or
 * process death resumes from the first item not in a final state. The only in-memory state is
 * what cannot survive a process anyway — the confirmation Intent PackageInstaller handed us and
 * whether the Progress screen is in the foreground.
 *
 * One dialog at a time, only in the foreground: the loop waits for [onScreenResumed] before it
 * asks for the next app, because Android blocks background activity starts and a dialog over
 * another app would be alarming. A result that arrives while the user is away is still recorded;
 * leaving only pauses the queue *after* the current dialog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UninstallEngine @Inject constructor(
    private val uninstallDao: UninstallDao,
    private val historyDao: HistoryDao,
    private val inventory: AppInventory,
    private val storage: StorageBreakdown,
    private val usageAccess: UsageAccess,
    private val premium: Premium,
    private val selection: SelectionStore,
    private val analytics: Analytics,
    private val remover: PackageRemover,
    private val icons: IconSnapshotStore,
    private val warnings: AppWarnings,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _runtime = MutableStateFlow(EngineRuntime())
    val runtime: StateFlow<EngineRuntime> = _runtime.asStateFlow()

    private val foreground = MutableStateFlow(false)

    /**
     * Batches this process created or is running. The resume banner is for batches a *killed*
     * process left behind, so it must never flash for the one being handed to the Progress screen.
     */
    private val ownedBatches = MutableStateFlow<Set<Long>>(emptySet())

    /** Final statuses by item id. A map, not a channel, so a result can never race its awaiter. */
    private val results = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Serialises read-modify-write of batch rows (Stop and finish both write the same row). */
    private val batchLock = Mutex()

    private val jobLock = Any()
    private var runJob: Job? = null
    private var stallJob: Job? = null

    @Volatile private var awaitingPackage: String? = null
    @Volatile private var stopRequestedFor: Long? = null

    /**
     * Cancels in a row in the running batch ([CancelStreak]). Lives with the engine, so rotation
     * keeps it; after process death the resumed queue starts over at 0 — at worst three more
     * dialogs before the question, never a question the user did not earn.
     */
    @Volatile private var cancelStreak = 0

    /** Writes a full disk refused, laid over Room until they can be written (PRD §6 "Storage almost full"). */
    private val held = MutableStateFlow(HeldWrites())
    private val flushLock = Mutex()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { remover.events.collect(::onRemovalEvent) }
    }

    // ------------------------------------------------------------------ create

    /**
     * Confirm Sheet → queue. Snapshots every app (label, installer, version, size, icon) before
     * any dialog can open (PRD §0 decision 2), writes the batch and its ordered items in one
     * transaction, and clears those apps from the global selection (PRD §6 item 21).
     */
    suspend fun createBatch(packages: List<String>, sourceTab: HomeTab): Long {
        val distinct = packages.distinct()
        require(distinct.isNotEmpty()) { "A batch needs at least one app" }
        val now = clock.now()
        housekeeping(now)

        val known = inventory.apps.value.associateBy { it.packageName }
        val snapshots = coroutineScope {
            distinct.mapIndexed { index, pkg ->
                async {
                    val app = inventory.find(pkg) ?: known[pkg]
                    val (bytes, estimate) = app?.let { measure(it) } ?: (0L to true)
                    // Text-only when the phone is almost full (PRD §6 "Storage almost full").
                    val icon = icons.save(pkg, "${pkg}_$now")
                    icon.degraded to UninstallItemEntity(
                        batchId = 0,
                        position = index,
                        packageName = pkg,
                        label = app?.label ?: pkg,
                        installerPackage = app?.installerPackage,
                        versionName = app?.versionName,
                        snapshotBytes = bytes,
                        bytesIsEstimate = estimate,
                        iconPath = icon.path,
                        state = ItemState.WAITING.name,
                        updatedAt = now,
                    )
                }
            }.awaitAll()
        }
        snapshots.mapNotNull { it.first }.forEach { analytics.log(AnalyticsEvent.HistorySnapshotDegraded(it)) }
        val items = snapshots.map { it.second }
        val batch = UninstallBatchEntity(createdAt = now, sourceTab = sourceTab.name)
        val batchId = try {
            uninstallDao.insertBatchWithItems(batch, items)
        } catch (_: SQLiteFullException) {
            // Not even the small queue rows fit: run the batch from memory and write it after the
            // first removal frees space. Negative ids can never collide with Room's own.
            analytics.log(AnalyticsEvent.HistorySnapshotDegraded(SnapshotDegradedReason.DB_FULL))
            holdNewBatch(batch, items, now)
        }
        ownedBatches.update { it + batchId }
        selection.deselect(distinct)

        val flagged = runCatching { warnings.warningsFor(distinct) }.getOrDefault(emptyMap())
        analytics.log(
            AnalyticsEvent.UninstallBatchStarted(
                appCount = distinct.size,
                bytesBucket = bytesBucket(items.sumOf { it.snapshotBytes }),
                sourceTab = sourceTab.name.lowercase(),
                hasWarnings = flagged.isNotEmpty(),
            )
        )
        return batchId
    }

    /** Result screen "Try again": a fresh batch (fresh snapshots) from the same source tab. */
    suspend fun retry(fromBatchId: Long, packages: List<String>): Long {
        val tab = batchOf(fromBatchId)?.sourceTab
            ?.let { runCatching { HomeTab.valueOf(it) }.getOrNull() } ?: HomeTab.ALL
        return createBatch(packages, tab)
    }

    // ------------------------------------------------------------------ observe

    /** Room, with any writes the full disk refused laid on top, so the screens never go stale. */
    fun observeSummary(batchId: Long): Flow<BatchSummary?> =
        combine(uninstallDao.observeBatch(batchId), uninstallDao.observeItems(batchId), held) { batch, items, h ->
            (h.batches[batchId] ?: batch)?.let { BatchSummary(it, h.overlay(batchId, items)) }
        }

    /** The kill-recovery banner; hidden for the batch this process is already running. */
    fun observeResumable(): Flow<ResumableBatch?> =
        uninstallDao.observeUnfinishedBatch().flatMapLatest { batch ->
            if (batch == null) {
                flowOf(null)
            } else {
                combine(uninstallDao.observeItems(batch.batchId), ownedBatches) { items, owned ->
                    if (batch.batchId in owned) null
                    else ResumableBatch(batch.batchId, items.count { !ItemState.of(it.state).isFinal })
                }
            }
        }

    // ------------------------------------------------------------------ Progress screen hooks

    /** Starts (or keeps) the run loop for [batchId]. Idempotent, so rotation is a no-op. */
    fun attach(batchId: Long) {
        synchronized(jobLock) {
            if (runJob?.isActive == true && _runtime.value.activeBatchId == batchId) return
            runJob?.cancel()
            stopRequestedFor = null
            awaitingPackage = null
            cancelStreak = 0
            _runtime.value = EngineRuntime(activeBatchId = batchId)
            ownedBatches.update { it + batchId }
            runJob = scope.launch { runLoop(batchId) }
        }
    }

    fun onScreenResumed() {
        foreground.value = true
        watchForStall()
    }

    /** Paused under Android's dialog: still "in the foreground" for the queue. */
    fun onScreenPaused() {
        stallJob?.cancel()
    }

    /** The user left the app: finish the current dialog, then wait. */
    fun onScreenStopped() {
        foreground.value = false
        stallJob?.cancel()
    }

    /** The Progress screen started (or failed to start) Android's dialog for [itemId]. */
    fun onConfirmationLaunched(itemId: Long, shown: Boolean) {
        if (!shown) {
            results.update { it + (itemId to PackageInstaller.STATUS_FAILURE) }
            return
        }
        _runtime.update { rt ->
            val pending = rt.confirmation
            if (pending?.itemId == itemId) rt.copy(confirmation = pending.copy(launched = true)) else rt
        }
    }

    /**
     * "Show it again": the dialog went away without a result reaching us. If the app is already
     * gone we take the success path (which re-verifies); otherwise we ask PackageInstaller again.
     */
    fun reRequestCurrent() {
        val itemId = _runtime.value.awaitingItemId ?: return
        val pkg = awaitingPackage ?: return
        _runtime.update { it.copy(confirmation = null, stalled = false) }
        scope.launch {
            if (!inventory.isInstalled(pkg)) {
                results.update { it + (itemId to PackageInstaller.STATUS_SUCCESS) }
            } else {
                remover.requestUninstall(itemId, pkg)
            }
        }
    }

    /**
     * PRD Feature 2: Stop ends the batch after the current dialog. A dialog that was requested but
     * not yet shown is withdrawn instead, and that app goes back to "not attempted".
     */
    suspend fun stop(batchId: Long) {
        val updated = updateBatch(batchId) { if (it.finishedAt == null) it.copy(stoppedEarly = true) else it } ?: return
        if (updated.finishedAt != null) return
        stopRequestedFor = batchId
        // Stop while "Stop removing the rest?" is up: the loop is waiting on it, release it.
        _runtime.update { it.copy(cancelStreakPrompt = null) }
        val rt = _runtime.value
        val awaiting = rt.awaitingItemId
        val running = rt.activeBatchId == batchId && runJob?.isActive == true
        when {
            !running -> finish(batchId)
            awaiting != null && rt.confirmation?.launched != true ->
                results.update { it + (awaiting to UninstallRules.STATUS_NOT_SHOWN) }
            // Otherwise Android's dialog is up; the loop finishes the batch when its result lands.
        }
    }

    /**
     * "Stop removing the rest?" → Stop (PRD §6 "Several dialogs cancelled in a row"): the same
     * Stop as the button on Screen 9, so the batch ends and the Result screen shows what was done.
     */
    suspend fun stopAfterCancelStreak(batchId: Long) {
        val remaining = _runtime.value.cancelStreakPrompt ?: return
        analytics.log(AnalyticsEvent.UninstallCancelStreakPrompt(CancelStreakAction.STOP, remaining))
        stop(batchId)
    }

    /** "Keep going": the streak starts over and the next dialog opens. */
    fun keepGoingAfterCancelStreak() {
        val remaining = _runtime.value.cancelStreakPrompt ?: return
        analytics.log(AnalyticsEvent.UninstallCancelStreakPrompt(CancelStreakAction.KEEP_GOING, remaining))
        cancelStreak = 0
        _runtime.update { it.copy(cancelStreakPrompt = null) }
    }

    // ------------------------------------------------------------------ Home banner

    /**
     * Banner "Continue": re-verifies every remaining package first (PRD §6 item 4), so apps removed
     * elsewhere meanwhile are not queued again. @return how many apps are still to go.
     */
    suspend fun continueBatch(batchId: Long): Int {
        val shown = remainingCount(batchId)
        ownedBatches.update { it + batchId }
        reconcile(batchId)
        updateBatch(batchId) { it.copy(stoppedEarly = false) }
        analytics.log(AnalyticsEvent.UninstallQueueResumed(remainingCount = shown, action = "continue"))
        val remaining = remainingCount(batchId)
        if (remaining == 0) finish(batchId)
        return remaining
    }

    /** Banner "Discard". Removals that completed before the kill still reach History. */
    suspend fun discardBatch(batchId: Long) {
        val shown = remainingCount(batchId)
        reconcile(batchId)
        updateBatch(batchId) { it.copy(discarded = true) }
        analytics.log(AnalyticsEvent.UninstallQueueResumed(remainingCount = shown, action = "discard"))
    }

    /** Closes a batch that was killed after its last result but before it was marked finished. */
    suspend fun finishIfComplete(batchId: Long) {
        if (remainingCount(batchId) == 0) finish(batchId)
    }

    // ------------------------------------------------------------------ run loop

    private suspend fun runLoop(batchId: Long) {
        while (true) {
            val batch = batchOf(batchId) ?: return
            if (batch.finishedAt != null || batch.discarded) return
            val items = itemsOf(batchId)
            val next = UninstallRules.nextItem(items)
            if (next == null || batch.stoppedEarly) {
                finish(batchId)
                return
            }
            if (!foreground.value) {
                foreground.first { it }
                continue // Re-read: Stop may have been pressed while we waited.
            }
            val remaining = items.count { !ItemState.of(it.state).isFinal }
            if (CancelStreak.shouldAsk(cancelStreak, remaining)) {
                // PRD §6: three Cancels in a row — ask before opening another dialog.
                _runtime.update { it.copy(cancelStreakPrompt = remaining) }
                _runtime.first { it.cancelStreakPrompt == null }
                continue // Re-read: "Stop" ends the batch, "Keep going" reset the streak.
            }
            process(batch, next)
        }
    }

    private suspend fun process(batch: UninstallBatchEntity, item: UninstallItemEntity) {
        val live = inventory.find(item.packageName)
        when (UninstallRules.preflight(ItemState.of(item.state), installed = live != null)) {
            Preflight.ALREADY_REMOVED -> {
                saveItem(item.copy(state = ItemState.ALREADY_REMOVED.name, updatedAt = clock.now()))
                return
            }
            Preflight.REMOVED_BY_US -> {
                recordRemoval(item)
                cancelStreak = CancelStreak.next(cancelStreak, Outcome.Removed)
                return
            }
            Preflight.REQUEST -> Unit
        }

        var current = item
        if (live != null && UninstallRules.needsRemeasure(live.lastUpdateTime, batch.createdAt)) {
            val (bytes, estimate) = measure(live)
            current = current.copy(
                label = live.label,
                versionName = live.versionName,
                installerPackage = live.installerPackage ?: current.installerPackage,
                snapshotBytes = bytes,
                bytesIsEstimate = estimate,
            )
        }
        current = current.copy(state = ItemState.IN_PROGRESS.name, updatedAt = clock.now())
        saveItem(current)

        val itemId = current.id
        val pkg = current.packageName
        results.update { it - itemId }
        awaitingPackage = pkg
        _runtime.update { it.copy(awaitingItemId = itemId, confirmation = null, stalled = false) }
        remover.requestUninstall(itemId, pkg)

        val status = results.mapNotNull { it[itemId] }.first()
        results.update { it - itemId }
        awaitingPackage = null
        stallJob?.cancel()
        _runtime.update { it.copy(awaitingItemId = null, confirmation = null, stalled = false) }

        val isFailure = status != PackageInstaller.STATUS_SUCCESS &&
            status != PackageInstaller.STATUS_FAILURE_ABORTED &&
            status != UninstallRules.STATUS_NOT_SHOWN
        val outcome = UninstallRules.classify(
            status = status,
            stillInstalled = status == PackageInstaller.STATUS_SUCCESS && isStillInstalled(pkg),
            isDeviceAdmin = isFailure && runCatching { remover.isDeviceAdmin(pkg) }.getOrDefault(false),
        )
        val now = clock.now()
        cancelStreak = CancelStreak.next(cancelStreak, outcome)
        when (outcome) {
            Outcome.Removed -> recordRemoval(current)
            Outcome.Skipped -> saveItem(
                current.copy(state = ItemState.SKIPPED.name, statusCode = status, updatedAt = now)
            )
            Outcome.NotShown -> saveItem(current.copy(state = ItemState.WAITING.name, updatedAt = now))
            is Outcome.Failed -> {
                saveItem(
                    current.copy(
                        state = ItemState.FAILED.name,
                        statusCode = status,
                        failureReason = outcome.reason.name,
                        updatedAt = now,
                    )
                )
                // PRD §0 decision 4: reason + status code only, never the package.
                analytics.log(AnalyticsEvent.UninstallFailed(outcome.reason.analyticsValue, status))
            }
        }
    }

    /**
     * The only path into History (PRD Feature 4): the package is verifiably gone.
     *
     * If the disk is too full even for this small write (SQLiteFullException), the removal still
     * counts: the rows are held in memory, the queue moves on, and they are written right after
     * the next successful removal or at batch end — by then Android has freed the removed app's
     * space (PRD §6 "Storage almost full").
     */
    private suspend fun recordRemoval(item: UninstallItemEntity) {
        val now = clock.now()
        val removed = item.copy(
            state = ItemState.REMOVED.name,
            statusCode = PackageInstaller.STATUS_SUCCESS,
            failureReason = null,
            updatedAt = now,
        )
        val entry = UninstallHistoryEntity(
            packageName = item.packageName,
            label = item.label,
            iconPath = item.iconPath,
            bytes = item.snapshotBytes,
            bytesIsEstimate = item.bytesIsEstimate,
            installerPackage = item.installerPackage,
            versionName = item.versionName,
            removedAt = now,
        )
        icons.evictLiveIcon(item.packageName)
        val saved = try {
            // A held item may have no Room row yet (its batch never fit): upsert it.
            if (removed.id in held.value.items) uninstallDao.upsertRemoved(removed, entry)
            else uninstallDao.markRemoved(removed, entry)
            held.update { it.release(removed.id, removed) }
            true
        } catch (_: SQLiteFullException) {
            held.update { it.holdRemoval(removed, entry) }
            analytics.log(AnalyticsEvent.HistorySnapshotDegraded(SnapshotDegradedReason.DB_FULL))
            false
        }
        if (saved) {
            tolerateFullDisk { historyDao.prune() }
            flushHeld()
        }
    }

    /** PRD Feature 2 "Verify", with a short grace for PackageManager to catch up. */
    private suspend fun isStillInstalled(pkg: String): Boolean {
        repeat(VERIFY_ATTEMPTS) { attempt ->
            if (!inventory.isInstalled(pkg)) return false
            if (attempt < VERIFY_ATTEMPTS - 1) delay(VERIFY_RETRY_MS)
        }
        return true
    }

    private suspend fun finish(batchId: Long) {
        updateBatch(batchId) { if (it.finishedAt == null) it.copy(finishedAt = clock.now()) else it }
        flushHeld() // Batch end: the last chance this run has to write what the full disk refused.
        awaitingPackage = null
        _runtime.update { it.copy(awaitingItemId = null, confirmation = null, stalled = false, cancelStreakPrompt = null) }
    }

    // ------------------------------------------------------------------ helpers

    private fun onRemovalEvent(event: RemovalEvent) {
        when (event) {
            is RemovalEvent.NeedsConfirmation -> {
                val rt = _runtime.value
                if (rt.awaitingItemId != event.itemId) return
                if (stopRequestedFor != null && stopRequestedFor == rt.activeBatchId) {
                    results.update { it + (event.itemId to UninstallRules.STATUS_NOT_SHOWN) }
                } else {
                    _runtime.update { it.copy(confirmation = PendingConfirmation(event.itemId, event.launcher), stalled = false) }
                }
            }
            is RemovalEvent.Finished -> results.update { it + (event.itemId to event.status) }
        }
    }

    /**
     * Our screen is back on top while a shown dialog has not reported. Usually the result is just
     * behind; if it never comes (an OEM dialog closed without reporting), offer to show it again
     * rather than timing the queue out (PRD §6 item 7).
     */
    private fun watchForStall() {
        stallJob?.cancel()
        val pending = _runtime.value.confirmation?.takeIf { it.launched } ?: return
        stallJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            _runtime.update { rt ->
                val current = rt.confirmation
                if (current?.itemId == pending.itemId && current.launched) rt.copy(stalled = true) else rt
            }
        }
    }

    /** Best available size (PRD Feature 2): full StorageStats with premium + Usage Access, else APK. */
    private suspend fun measure(app: InstalledApp): Pair<Long, Boolean> {
        if (premium.isPremium.value && usageAccess.isGranted.value) {
            runCatching { storage.measure(app.packageName) }.getOrNull()?.let { return it.totalBytes to false }
        }
        return app.apkBytes to true
    }

    /** Non-final items still installed or not, before reconciliation. */
    private suspend fun remainingCount(batchId: Long): Int =
        itemsOf(batchId).count { !ItemState.of(it.state).isFinal }

    private suspend fun reconcile(batchId: Long) {
        itemsOf(batchId)
            .filter { !ItemState.of(it.state).isFinal && !inventory.isInstalled(it.packageName) }
            .forEach { item ->
                when (UninstallRules.preflight(ItemState.of(item.state), installed = false)) {
                    Preflight.REMOVED_BY_US -> recordRemoval(item)
                    else -> saveItem(item.copy(state = ItemState.ALREADY_REMOVED.name, updatedAt = clock.now()))
                }
            }
    }

    private suspend fun updateBatch(
        batchId: Long,
        transform: (UninstallBatchEntity) -> UninstallBatchEntity,
    ): UninstallBatchEntity? = batchLock.withLock {
        val batch = batchOf(batchId) ?: return@withLock null
        transform(batch).also { if (it != batch) saveBatch(it) }
    }

    // ------------------------------------------------------------------ full disk (PRD §6 "Storage almost full")

    private suspend fun batchOf(batchId: Long): UninstallBatchEntity? =
        held.value.batches[batchId] ?: uninstallDao.getBatch(batchId)

    private suspend fun itemsOf(batchId: Long): List<UninstallItemEntity> =
        held.value.overlay(batchId, uninstallDao.getItems(batchId))

    /**
     * Queue bookkeeping must never stop a removal. A row already held stays in memory until the
     * next flush (its Room row may not even exist); otherwise Room is tried and, if the disk is
     * full, the row is held instead.
     */
    private suspend fun saveItem(item: UninstallItemEntity) {
        if (item.id in held.value.items) {
            held.update { it.copy(items = it.items + (item.id to item)) }
            return
        }
        try {
            uninstallDao.updateItem(item)
        } catch (_: SQLiteFullException) {
            held.update { it.copy(items = it.items + (item.id to item)) }
        }
    }

    private suspend fun saveBatch(batch: UninstallBatchEntity) {
        if (batch.batchId in held.value.batches) {
            held.update { it.copy(batches = it.batches + (batch.batchId to batch)) }
            return
        }
        try {
            uninstallDao.updateBatch(batch)
        } catch (_: SQLiteFullException) {
            held.update { it.copy(batches = it.batches + (batch.batchId to batch)) }
        }
    }

    /** A batch whose queue rows did not fit at all. @return its (negative) in-memory id. */
    private fun holdNewBatch(batch: UninstallBatchEntity, items: List<UninstallItemEntity>, now: Long): Long {
        val batchId = -now
        val rows = items.mapIndexed { index, item ->
            item.copy(id = batchId * HELD_ITEM_ID_SPAN - index, batchId = batchId)
        }
        held.update { h ->
            h.copy(
                batches = h.batches + (batchId to batch.copy(batchId = batchId)),
                items = h.items + rows.associateBy { it.id },
            )
        }
        return batchId
    }

    /**
     * Writes everything held: batches first, then items with their History rows. Stops at the
     * first SQLiteFullException and keeps the rest for the next try. A row changed while it was
     * being written stays held, so the newer version is written next time.
     */
    private suspend fun flushHeld() = flushLock.withLock {
        val snapshot = held.value
        if (snapshot.isEmpty) return@withLock
        tolerateFullDisk {
            snapshot.batches.values.forEach { batch ->
                uninstallDao.upsertBatch(batch)
                held.update { h -> if (h.batches[batch.batchId] == batch) h.copy(batches = h.batches - batch.batchId) else h }
            }
            snapshot.items.values.sortedBy { it.position }.forEach { item ->
                val entry = snapshot.history[item.id]
                if (entry != null) uninstallDao.upsertRemoved(item, entry) else uninstallDao.upsertItems(listOf(item))
                held.update { it.release(item.id, item) }
            }
            if (snapshot.history.isNotEmpty()) historyDao.prune()
        }
    }

    private inline fun tolerateFullDisk(block: () -> Unit) {
        try {
            block()
        } catch (_: SQLiteFullException) {
            // Still full: whatever was not written stays held for the next successful removal.
        }
    }

    /** Drops month-old closed batches and icon files nothing points at any more. Best effort. */
    private suspend fun housekeeping(now: Long) {
        runCatching {
            val cutoff = now - CLOSED_BATCH_RETENTION_MS
            uninstallDao.deleteItemsOfClosedBatchesBefore(cutoff)
            uninstallDao.deleteClosedBatchesBefore(cutoff)
            icons.sweep(keep = (historyDao.getIconPaths() + uninstallDao.getUnfinishedIconPaths()).toSet())
        }
    }

    companion object {
        const val STALL_TIMEOUT_MS = 6_000L
        const val VERIFY_ATTEMPTS = 3
        const val VERIFY_RETRY_MS = 250L
        const val CLOSED_BATCH_RETENTION_MS = 30 * DAY_MILLIS

        /** Room for item ids under one in-memory batch id; the queue has no size limit, so wide. */
        private const val HELD_ITEM_ID_SPAN = 1_000_000L
    }
}

/**
 * Room writes the full disk refused (PRD §6 "Storage almost full"), by primary key. History rows
 * are keyed by their item's id: a removal's item row and History row are written together.
 */
private data class HeldWrites(
    val batches: Map<Long, UninstallBatchEntity> = emptyMap(),
    val items: Map<Long, UninstallItemEntity> = emptyMap(),
    val history: Map<Long, UninstallHistoryEntity> = emptyMap(),
) {
    val isEmpty: Boolean get() = batches.isEmpty() && items.isEmpty() && history.isEmpty()

    /** [rows] from Room for [batchId], with held versions (and held-only rows) laid on top. */
    fun overlay(batchId: Long, rows: List<UninstallItemEntity>): List<UninstallItemEntity> {
        val mine = items.filterValues { it.batchId == batchId }
        if (mine.isEmpty()) return rows
        return (rows.associateBy { it.id } + mine).values.sortedBy { it.position }
    }

    fun holdRemoval(item: UninstallItemEntity, entry: UninstallHistoryEntity) =
        copy(items = items + (item.id to item), history = history + (item.id to entry))

    /** Drops [itemId] once [written] reached Room — unless a newer version was held meanwhile. */
    fun release(itemId: Long, written: UninstallItemEntity): HeldWrites {
        val current = items[itemId]
        if (current != null && current != written) return copy(history = history - itemId)
        return copy(items = items - itemId, history = history - itemId)
    }
}
