package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.pm.PackageInstaller
import com.jedy.appcleaner.uninstaller.data.local.UninstallBatchEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallRulesTest {

    @Test
    fun `success is removed only when the package is really gone`() {
        assertEquals(Outcome.Removed, UninstallRules.classify(PackageInstaller.STATUS_SUCCESS, stillInstalled = false, isDeviceAdmin = false))
        assertEquals(
            Outcome.Failed(FailureReason.NOT_REMOVED_AFTER_SUCCESS),
            UninstallRules.classify(PackageInstaller.STATUS_SUCCESS, stillInstalled = true, isDeviceAdmin = false),
        )
    }

    @Test
    fun `cancel is a skip, never a failure`() {
        assertEquals(Outcome.Skipped, UninstallRules.classify(PackageInstaller.STATUS_FAILURE_ABORTED, stillInstalled = true, isDeviceAdmin = true))
    }

    @Test
    fun `device admin wins over blocked because the user can fix it`() {
        assertEquals(
            Outcome.Failed(FailureReason.DEVICE_ADMIN),
            UninstallRules.classify(PackageInstaller.STATUS_FAILURE_BLOCKED, stillInstalled = true, isDeviceAdmin = true),
        )
        assertEquals(
            Outcome.Failed(FailureReason.BLOCKED),
            UninstallRules.classify(PackageInstaller.STATUS_FAILURE_BLOCKED, stillInstalled = true, isDeviceAdmin = false),
        )
    }

    @Test
    fun `any other status is a coded failure`() {
        assertEquals(
            Outcome.Failed(FailureReason.STATUS_CODE_OTHER),
            UninstallRules.classify(PackageInstaller.STATUS_FAILURE, stillInstalled = true, isDeviceAdmin = false),
        )
        assertEquals(Outcome.NotShown, UninstallRules.classify(UninstallRules.STATUS_NOT_SHOWN, stillInstalled = true, isDeviceAdmin = false))
    }

    @Test
    fun `blocked is the only failure without retry`() {
        FailureReason.entries.forEach { reason ->
            assertEquals(reason != FailureReason.BLOCKED, reason.canRetry)
        }
    }

    @Test
    fun `preflight distinguishes removed elsewhere from our own lost result`() {
        assertEquals(Preflight.REQUEST, UninstallRules.preflight(ItemState.WAITING, installed = true))
        assertEquals(Preflight.REQUEST, UninstallRules.preflight(ItemState.IN_PROGRESS, installed = true))
        assertEquals(Preflight.ALREADY_REMOVED, UninstallRules.preflight(ItemState.WAITING, installed = false))
        assertEquals(Preflight.REMOVED_BY_US, UninstallRules.preflight(ItemState.IN_PROGRESS, installed = false))
    }

    @Test
    fun `resume point is the first non-final item in queue order`() {
        val items = listOf(
            item(3, ItemState.WAITING),
            item(0, ItemState.REMOVED),
            item(2, ItemState.IN_PROGRESS),
            item(1, ItemState.SKIPPED),
        )
        assertEquals(2, UninstallRules.nextItem(items)?.position)
        assertNull(UninstallRules.nextItem(listOf(item(0, ItemState.FAILED), item(1, ItemState.ALREADY_REMOVED))))
    }

    @Test
    fun `re-measure only when updated after the batch was confirmed`() {
        assertTrue(UninstallRules.needsRemeasure(lastUpdateTime = 2_000, batchCreatedAt = 1_000))
        assertFalse(UninstallRules.needsRemeasure(lastUpdateTime = 1_000, batchCreatedAt = 1_000))
    }

    @Test
    fun `summary counts only verified removals in the freed total`() {
        val summary = BatchSummary(
            batch = UninstallBatchEntity(batchId = 1, createdAt = 0, sourceTab = "ALL", finishedAt = 10),
            items = listOf(
                item(0, ItemState.REMOVED, bytes = 1_000, estimate = false),
                item(1, ItemState.REMOVED, bytes = 2_000, estimate = true),
                item(2, ItemState.ALREADY_REMOVED, bytes = 50_000),
                item(3, ItemState.SKIPPED, bytes = 7),
                item(4, ItemState.FAILED, bytes = 9, reason = FailureReason.BLOCKED),
                item(5, ItemState.WAITING, bytes = 11),
            ),
        )
        assertEquals(3_000, summary.freedBytes)
        assertTrue(summary.freedIsEstimate)
        assertEquals(2, summary.removedCount)
        assertEquals(1, summary.alreadyRemovedCount)
        assertEquals(listOf(3, 4, 5), summary.notRemoved.map { it.item.position })
        assertEquals(listOf(true, false, true), summary.notRemoved.map { it.canRetry })
        assertTrue(summary.isFinished)
    }

    @Test
    fun `progress reads the item being worked on`() {
        val batch = UninstallBatchEntity(batchId = 1, createdAt = 0, sourceTab = "ALL")
        val running = BatchSummary(batch, listOf(item(0, ItemState.REMOVED), item(1, ItemState.IN_PROGRESS), item(2, ItemState.WAITING)))
        assertEquals(2, running.currentPosition)
        assertEquals(1 / 3f, running.progress, 0.0001f)
        val done = BatchSummary(batch, listOf(item(0, ItemState.REMOVED), item(1, ItemState.SKIPPED)))
        assertEquals(2, done.currentPosition)
        assertEquals(1f, done.progress, 0.0001f)
    }

    private fun item(
        position: Int,
        state: ItemState,
        bytes: Long = 0,
        estimate: Boolean = false,
        reason: FailureReason? = null,
    ) = UninstallItemEntity(
        id = position.toLong() + 1,
        batchId = 1,
        position = position,
        packageName = "pkg.$position",
        label = "App $position",
        installerPackage = null,
        versionName = null,
        snapshotBytes = bytes,
        bytesIsEstimate = estimate,
        iconPath = null,
        state = state.name,
        failureReason = reason?.name,
        updatedAt = 0,
    )
}
