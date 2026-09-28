package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.pm.PackageInstaller
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.local.UninstallBatchEntity
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UninstallEngineTest {

    private val history = FakeHistoryDao()
    private val dao = FakeUninstallDao(history)
    private val analytics = FakeAnalytics()
    private val icons = FakeIcons()
    private val selection = SelectionStore()

    private fun TestScope.engine(
        inventory: FakeInventory,
        remover: FakeRemover = FakeRemover(inventory),
        premium: Boolean = false,
        granted: Boolean = false,
        measured: Map<String, AppSize> = emptyMap(),
        warnings: Map<String, List<AppWarning>> = emptyMap(),
    ) = UninstallEngine(
        uninstallDao = dao,
        historyDao = history,
        inventory = inventory,
        storage = FakeStorage(measured),
        usageAccess = FakeUsageAccess(granted),
        premium = FakePremium(premium),
        selection = selection,
        analytics = analytics,
        remover = remover,
        icons = icons,
        warnings = FakeWarnings(warnings),
        clock = Clock { BASE_TIME + testScheduler.currentTime },
        scope = backgroundScope,
    )

    @Test
    fun `createBatch snapshots every app in order and clears the selection`() = runTest {
        val inventory = FakeInventory(listOf(app("a.one", apkBytes = 100), app("b.two", apkBytes = 200)))
        selection.select(listOf("a.one", "b.two", "c.other"))
        val engine = engine(inventory, warnings = mapOf("b.two" to listOf(AppWarning.KEYBOARD)))

        val id = engine.createBatch(listOf("b.two", "a.one", "b.two"), HomeTab.LARGE)

        val items = dao.getItems(id)
        assertEquals(listOf("b.two", "a.one"), items.map { it.packageName })
        assertTrue(items.all { ItemState.of(it.state) == ItemState.WAITING })
        assertTrue(items.all { it.bytesIsEstimate })
        assertEquals(listOf(200L, 100L), items.map { it.snapshotBytes })
        assertTrue(items.all { it.iconPath != null })
        assertEquals("LARGE", dao.getBatch(id)?.sourceTab)
        assertEquals(setOf("c.other"), selection.selected.value)
        val started = analytics.events.filterIsInstance<AnalyticsEvent.UninstallBatchStarted>().single()
        assertEquals(2, started.appCount)
        assertEquals("large", started.sourceTab)
        assertTrue(started.hasWarnings)
    }

    @Test
    fun `premium with usage access snapshots the full size, not an estimate`() = runTest {
        val inventory = FakeInventory(listOf(app("a.one", apkBytes = 100)))
        val engine = engine(
            inventory, premium = true, granted = true,
            measured = mapOf("a.one" to AppSize(appBytes = 100, dataBytes = 50, cacheBytes = 25, measuredAt = 0)),
        )
        val item = dao.getItems(engine.createBatch(listOf("a.one"), HomeTab.ALL)).single()
        assertEquals(175, item.snapshotBytes)
        assertFalse(item.bytesIsEstimate)
    }

    @Test
    fun `every PackageInstaller outcome lands in the right state and only removals reach History`() = runTest {
        val inventory = FakeInventory(listOf("ok", "cancel", "blocked", "admin", "liar").map { app("p.$it", apkBytes = 1_000) })
        val remover = FakeRemover(inventory).apply {
            script["p.ok"] = PackageInstaller.STATUS_SUCCESS
            script["p.cancel"] = PackageInstaller.STATUS_FAILURE_ABORTED
            script["p.blocked"] = PackageInstaller.STATUS_FAILURE_BLOCKED
            script["p.admin"] = PackageInstaller.STATUS_FAILURE_BLOCKED
            script["p.liar"] = PackageInstaller.STATUS_SUCCESS
            keepInstalled += "p.liar"
            admins = setOf("p.admin")
        }
        val engine = engine(inventory, remover)
        val id = engine.createBatch(listOf("p.ok", "p.cancel", "p.blocked", "p.admin", "p.liar"), HomeTab.ALL)

        engine.attach(id)
        engine.onScreenResumed()
        settle()

        assertEquals(ItemState.REMOVED, dao.state(id, "p.ok"))
        assertEquals(ItemState.SKIPPED, dao.state(id, "p.cancel"))
        assertEquals(FailureReason.BLOCKED.name, dao.item(id, "p.blocked").failureReason)
        assertEquals(FailureReason.DEVICE_ADMIN.name, dao.item(id, "p.admin").failureReason)
        assertEquals(FailureReason.NOT_REMOVED_AFTER_SUCCESS.name, dao.item(id, "p.liar").failureReason)
        assertEquals(listOf("p.ok"), history.rows.value.map { it.packageName })
        assertEquals(listOf("p.ok"), icons.evicted)
        assertTrue(history.pruneCalls > 0)
        assertNotNull(dao.getBatch(id)?.finishedAt)

        val failures = analytics.events.filterIsInstance<AnalyticsEvent.UninstallFailed>().map { it.reason }
        assertEquals(listOf("blocked", "device_admin", "not_removed_after_success"), failures)

        val summary = BatchSummary(dao.getBatch(id)!!, dao.getItems(id))
        assertEquals(1_000, summary.freedBytes)
        assertEquals(1, summary.removedCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(3, summary.failedCount)
    }

    @Test
    fun `the queue waits for the foreground and pauses after the current dialog`() = runTest {
        val inventory = FakeInventory(listOf(app("p.a"), app("p.b")))
        val remover = FakeRemover(inventory)
        val engine = engine(inventory, remover)
        val id = engine.createBatch(listOf("p.a", "p.b"), HomeTab.ALL)

        engine.attach(id)
        settle()
        assertTrue("no dialog while the Progress screen is not resumed", remover.requests.isEmpty())

        engine.onScreenResumed()
        settle()
        val (firstId, firstPkg) = remover.requests.single()
        assertEquals("p.a", firstPkg)

        // Android's dialog shows; the user leaves the app, then confirms from wherever they are.
        remover.needsConfirmation(firstId)
        settle()
        val pending = engine.runtime.value.confirmation
        assertEquals(firstId, pending?.itemId)
        engine.onConfirmationLaunched(firstId, shown = true)
        engine.onScreenPaused()
        engine.onScreenStopped()
        remover.finish(firstId, firstPkg, PackageInstaller.STATUS_SUCCESS)
        settle()

        assertEquals("the result is recorded while away", ItemState.REMOVED, dao.state(id, "p.a"))
        assertEquals("but the next dialog waits", 1, remover.requests.size)

        engine.onScreenResumed()
        settle()
        assertEquals("p.b", remover.requests.last().second)
    }

    @Test
    fun `stop finishes after the dialog that is already up`() = runTest {
        val inventory = FakeInventory(listOf(app("p.a"), app("p.b")))
        val remover = FakeRemover(inventory)
        val engine = engine(inventory, remover)
        val id = engine.createBatch(listOf("p.a", "p.b"), HomeTab.ALL)
        engine.attach(id)
        engine.onScreenResumed()
        settle()
        val (firstId, firstPkg) = remover.requests.single()
        remover.needsConfirmation(firstId)
        settle()
        engine.onConfirmationLaunched(firstId, shown = true)

        engine.stop(id)
        settle()
        assertTrue(dao.getBatch(id)!!.stoppedEarly)
        assertNull("not finished until the dialog answers", dao.getBatch(id)!!.finishedAt)

        remover.finish(firstId, firstPkg, PackageInstaller.STATUS_FAILURE_ABORTED)
        settle()
        assertNotNull(dao.getBatch(id)!!.finishedAt)
        assertEquals(ItemState.SKIPPED, dao.state(id, "p.a"))
        assertEquals(ItemState.WAITING, dao.state(id, "p.b"))
        assertEquals(1, remover.requests.size)
    }

    @Test
    fun `stop withdraws a dialog that was requested but never shown`() = runTest {
        val inventory = FakeInventory(listOf(app("p.a"), app("p.b")))
        val remover = FakeRemover(inventory)
        val engine = engine(inventory, remover)
        val id = engine.createBatch(listOf("p.a", "p.b"), HomeTab.ALL)
        engine.attach(id)
        engine.onScreenResumed()
        settle()
        remover.needsConfirmation(remover.requests.single().first)
        settle()

        engine.stop(id)
        settle()

        assertNotNull(dao.getBatch(id)!!.finishedAt)
        assertEquals(ItemState.WAITING, dao.state(id, "p.a"))
        assertNull(engine.runtime.value.confirmation)
        assertTrue(history.rows.value.isEmpty())
    }

    @Test
    fun `after process death the engine resumes from Room without re-asking for apps already gone`() = runTest {
        // A batch as a killed process left it: one result lost after removal, one lost before the
        // user answered, one app removed elsewhere meanwhile.
        dao.insertBatchWithItems(
            UninstallBatchEntity(createdAt = BASE_TIME, sourceTab = "ALL"),
            listOf(
                queued(0, "p.lost_but_removed", ItemState.IN_PROGRESS, bytes = 300),
                queued(1, "p.lost_not_answered", ItemState.IN_PROGRESS, bytes = 500),
                queued(2, "p.removed_elsewhere", ItemState.WAITING, bytes = 700),
            ),
        )
        val inventory = FakeInventory(listOf(app("p.lost_not_answered")))
        val remover = FakeRemover(inventory).apply { script["p.lost_not_answered"] = PackageInstaller.STATUS_SUCCESS }
        val engine = engine(inventory, remover)

        engine.attach(1)
        engine.onScreenResumed()
        settle()

        assertEquals(listOf("p.lost_not_answered"), remover.requests.map { it.second })
        assertEquals(ItemState.REMOVED, dao.state(1, "p.lost_but_removed"))
        assertEquals(ItemState.REMOVED, dao.state(1, "p.lost_not_answered"))
        assertEquals(ItemState.ALREADY_REMOVED, dao.state(1, "p.removed_elsewhere"))
        assertEquals(setOf("p.lost_but_removed", "p.lost_not_answered"), history.rows.value.map { it.packageName }.toSet())
        assertEquals(800, BatchSummary(dao.getBatch(1)!!, dao.getItems(1)).freedBytes)
    }

    @Test
    fun `an app updated since selection is re-measured before its dialog`() = runTest {
        val inventory = FakeInventory(listOf(app("p.a", apkBytes = 100, lastUpdateTime = 0)))
        val remover = FakeRemover(inventory)
        val engine = engine(inventory, remover)
        val id = engine.createBatch(listOf("p.a"), HomeTab.ALL)
        inventory.installed["p.a"] = app("p.a", apkBytes = 900, lastUpdateTime = BASE_TIME + 1)

        engine.attach(id)
        engine.onScreenResumed()
        settle()

        assertEquals(900, dao.item(id, "p.a").snapshotBytes)
        assertEquals(ItemState.IN_PROGRESS, dao.state(id, "p.a"))
    }

    @Test
    fun `banner continue re-verifies remaining apps and discard keeps completed removals`() = runTest {
        dao.insertBatchWithItems(
            UninstallBatchEntity(createdAt = BASE_TIME, sourceTab = "UNUSED"),
            listOf(
                queued(0, "p.gone_ours", ItemState.IN_PROGRESS),
                queued(1, "p.gone_elsewhere", ItemState.WAITING),
                queued(2, "p.still_here", ItemState.WAITING),
            ),
        )
        val inventory = FakeInventory(listOf(app("p.still_here")))
        val engine = engine(inventory)

        val remaining = engine.continueBatch(1)

        assertEquals(1, remaining)
        assertEquals(ItemState.REMOVED, dao.state(1, "p.gone_ours"))
        assertEquals(ItemState.ALREADY_REMOVED, dao.state(1, "p.gone_elsewhere"))
        assertEquals(ItemState.WAITING, dao.state(1, "p.still_here"))
        val resumed = analytics.events.filterIsInstance<AnalyticsEvent.UninstallQueueResumed>().single()
        assertEquals(3, resumed.remainingCount)
        assertEquals("continue", resumed.action)

        engine.discardBatch(1)
        assertTrue(dao.getBatch(1)!!.discarded)
        assertEquals(listOf("p.gone_ours"), history.rows.value.map { it.packageName })
    }

    @Test
    fun `the resume banner offers only batches a killed process left behind`() = runTest {
        val leftBehind = dao.insertBatchWithItems(
            UninstallBatchEntity(createdAt = BASE_TIME, sourceTab = "ALL"),
            listOf(queued(0, "p.a", ItemState.WAITING), queued(1, "p.b", ItemState.REMOVED)),
        )
        val inventory = FakeInventory(listOf(app("p.a"), app("p.c")))
        val engine = engine(inventory)
        assertEquals(ResumableBatch(leftBehind, 1), engine.observeResumable().first())

        engine.attach(leftBehind)
        assertNull("running here now", engine.observeResumable().first())

        dao.updateBatch(dao.getBatch(leftBehind)!!.copy(discarded = true))
        engine.createBatch(listOf("p.c"), HomeTab.ALL)
        assertNull("never flashes for the batch just confirmed", engine.observeResumable().first())
    }

    /**
     * The engine runs in [TestScope.backgroundScope], which `advanceUntilIdle` stops serving once no
     * foreground work is left; advancing virtual time explicitly runs it (and its verify delays).
     */
    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    private fun queued(position: Int, pkg: String, state: ItemState, bytes: Long = 100) = UninstallItemEntity(
        batchId = 0,
        position = position,
        packageName = pkg,
        label = pkg,
        installerPackage = InstalledApp.PLAY_STORE_PACKAGE,
        versionName = "1",
        snapshotBytes = bytes,
        bytesIsEstimate = true,
        iconPath = null,
        state = state.name,
        updatedAt = BASE_TIME,
    )

    private companion object {
        const val BASE_TIME = 1_700_000_000_000L
    }
}
