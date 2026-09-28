package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.pm.PackageInstaller
import com.jedy.appcleaner.uninstaller.data.local.UninstallBatchEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity

/** What to do with one queue item before (re)requesting its uninstall. */
enum class Preflight {
    /** Hand it to PackageInstaller. */
    REQUEST,

    /** Gone before we asked: removed elsewhere (PRD §6 item 5). */
    ALREADY_REMOVED,

    /**
     * We had already asked, the result never reached us (process death drops the broadcast), and
     * the package is gone now: that is our removal, verified by its absence (PRD §6 item 22).
     */
    REMOVED_BY_US,
}

/** The final state a PackageInstaller status maps to. */
sealed interface Outcome {
    data object Removed : Outcome
    data object Skipped : Outcome
    data class Failed(val reason: FailureReason) : Outcome

    /** The dialog was never shown (Stop pressed first): the app goes back to waiting, untouched. */
    data object NotShown : Outcome
}

/**
 * The queue's decisions as plain functions, so every branch of PRD Feature 2 and §6 items 1–7 is
 * unit-tested without a device. [UninstallEngine] only sequences these against Room.
 */
object UninstallRules {

    /** Our own "the dialog never opened" status; outside PackageInstaller's range on purpose. */
    const val STATUS_NOT_SHOWN = Int.MIN_VALUE

    fun preflight(state: ItemState, installed: Boolean): Preflight = when {
        installed -> Preflight.REQUEST
        state == ItemState.IN_PROGRESS -> Preflight.REMOVED_BY_US
        else -> Preflight.ALREADY_REMOVED
    }

    /**
     * Maps a final status. Order matters: a device admin's uninstall is rejected with the same
     * BLOCKED status as policy locks, but it *can* be fixed by the user, so it is checked first.
     *
     * @param stillInstalled the post-success verification (PRD Feature 2 "Verify"); ignored otherwise.
     */
    fun classify(status: Int, stillInstalled: Boolean, isDeviceAdmin: Boolean): Outcome = when (status) {
        STATUS_NOT_SHOWN -> Outcome.NotShown
        PackageInstaller.STATUS_SUCCESS ->
            if (stillInstalled) Outcome.Failed(FailureReason.NOT_REMOVED_AFTER_SUCCESS) else Outcome.Removed
        PackageInstaller.STATUS_FAILURE_ABORTED -> Outcome.Skipped
        else -> when {
            isDeviceAdmin -> Outcome.Failed(FailureReason.DEVICE_ADMIN)
            status == PackageInstaller.STATUS_FAILURE_BLOCKED -> Outcome.Failed(FailureReason.BLOCKED)
            else -> Outcome.Failed(FailureReason.STATUS_CODE_OTHER)
        }
    }

    /** The resume point after rotation or process death: the first item not in a final state. */
    fun nextItem(items: List<UninstallItemEntity>): UninstallItemEntity? =
        items.sortedBy { it.position }.firstOrNull { !ItemState.of(it.state).isFinal }

    /** Updated since the batch was confirmed, so the pre-dialog size must be re-measured (PRD §6 item 5). */
    fun needsRemeasure(lastUpdateTime: Long, batchCreatedAt: Long): Boolean = lastUpdateTime > batchCreatedAt
}

/** One app the Result screen lists under "not removed". */
data class NotRemovedItem(
    val item: UninstallItemEntity,
    val state: ItemState,
    val reason: FailureReason?,
) {
    /** Skipped, never attempted, or a failure that the user can fix; never BLOCKED (PRD §6 item 2). */
    val canRetry: Boolean get() = reason?.canRetry ?: true
}

/** Everything Screens 9 and 10 show about a batch, derived from Room alone. */
data class BatchSummary(
    val batch: UninstallBatchEntity,
    val items: List<UninstallItemEntity>,
) {
    private val byState = items.groupBy { ItemState.of(it.state) }
    private fun count(state: ItemState) = byState[state]?.size ?: 0

    val total: Int get() = items.size
    val removed: List<UninstallItemEntity> get() = byState[ItemState.REMOVED].orEmpty()
    val removedCount: Int get() = count(ItemState.REMOVED)
    val skippedCount: Int get() = count(ItemState.SKIPPED)
    val failedCount: Int get() = count(ItemState.FAILED)
    val alreadyRemovedCount: Int get() = count(ItemState.ALREADY_REMOVED)
    val finalCount: Int get() = items.count { ItemState.of(it.state).isFinal }

    /** Only verified removals count (PRD §6 item 6): never an estimate of what we hoped. */
    val freedBytes: Long get() = removed.sumOf { it.snapshotBytes }
    val freedIsEstimate: Boolean get() = removed.any { it.bytesIsEstimate }

    val isFinished: Boolean get() = batch.finishedAt != null

    /** 1-based index for "Removing 2 of 5": the item being worked on, or the last one when done. */
    val currentPosition: Int get() = (finalCount + 1).coerceAtMost(total).coerceAtLeast(if (total == 0) 0 else 1)

    val progress: Float get() = if (total == 0) 1f else finalCount / total.toFloat()

    /** Skipped, failed and — after Stop — never attempted, in queue order. */
    val notRemoved: List<NotRemovedItem>
        get() = items.sortedBy { it.position }.mapNotNull { item ->
            when (val state = ItemState.of(item.state)) {
                ItemState.SKIPPED, ItemState.WAITING, ItemState.IN_PROGRESS -> NotRemovedItem(item, state, null)
                ItemState.FAILED -> NotRemovedItem(item, state, FailureReason.of(item.failureReason) ?: FailureReason.STATUS_CODE_OTHER)
                else -> null
            }
        }
}
