package com.jedy.appcleaner.uninstaller.data.history

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.data.local.HistoryDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallHistoryEntity
import com.jedy.appcleaner.uninstaller.data.uninstall.IconSnapshotStore
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uninstall History (PRD Feature 4). Rows are written only by the engine's verified-success path;
 * this side reads them and clears them. History is the undo (PRD §6 item 21): nothing here can
 * delete an app, only the record of one.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val icons: IconSnapshotStore,
) {
    val entries: Flow<List<UninstallHistoryEntity>> = historyDao.observeAll()

    /** "Clear history" also deletes the icon snapshots, which have no other use. */
    suspend fun clear() {
        val paths = historyDao.getIconPaths()
        historyDao.clear()
        icons.delete(paths)
    }
}

/** A calendar day of History, newest first. */
data class HistoryDay(
    val kind: Kind,
    val date: LocalDate,
    val entries: List<UninstallHistoryEntity>,
) {
    enum class Kind { TODAY, YESTERDAY, OTHER }
}

object HistoryRules {

    /** Groups by the user's local calendar day (PRD §4 Screen 12: Today / Yesterday / date). */
    fun groupByDay(
        entries: List<UninstallHistoryEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HistoryDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return entries
            .sortedByDescending { it.removedAt }
            .groupBy { Instant.ofEpochMilli(it.removedAt).atZone(zone).toLocalDate() }
            .map { (date, rows) ->
                val kind = when (date) {
                    today -> HistoryDay.Kind.TODAY
                    today.minusDays(1) -> HistoryDay.Kind.YESTERDAY
                    else -> HistoryDay.Kind.OTHER
                }
                HistoryDay(kind, date, rows)
            }
    }

    /** PRD §9 `history_reinstall_tapped.days_since_removed_bucket`: 0 | 1-7 | 8-30 | >30. */
    fun daysSinceRemovedBucket(removedAt: Long, now: Long): String {
        val days = ((now - removedAt).coerceAtLeast(0) / DAY_MILLIS)
        return when {
            days < 1 -> "0"
            days <= 7 -> "1-7"
            days <= 30 -> "8-30"
            else -> ">30"
        }
    }
}
