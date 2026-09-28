package com.jedy.appcleaner.uninstaller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Owner: Inventory. */
@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_app")
    fun observeAll(): Flow<List<InventoryAppEntity>>

    @Query("SELECT * FROM inventory_app")
    suspend fun getAll(): List<InventoryAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<InventoryAppEntity>)

    @Query("DELETE FROM inventory_app WHERE packageName IN (:packages)")
    suspend fun deleteAll(packages: List<String>)

    @Query("DELETE FROM inventory_app WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

/** Owner: Insights. */
@Dao
interface AppSizeDao {
    @Query("SELECT * FROM app_size")
    fun observeAll(): Flow<List<AppSizeEntity>>

    @Query("SELECT * FROM app_size")
    suspend fun getAll(): List<AppSizeEntity>

    @Query("SELECT * FROM app_size WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppSizeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sizes: List<AppSizeEntity>)

    /** Callers chunk [packages] (SQLite caps bound variables at 999 on older Android). */
    @Query("DELETE FROM app_size WHERE packageName IN (:packages)")
    suspend fun deleteAll(packages: List<String>)

    @Query("DELETE FROM app_size")
    suspend fun clear()
}

/** Owner: Uninstall. */
@Dao
interface UninstallDao {
    @Insert
    suspend fun insertBatch(batch: UninstallBatchEntity): Long

    @Update
    suspend fun updateBatch(batch: UninstallBatchEntity)

    @Query("SELECT * FROM uninstall_batch WHERE batchId = :batchId")
    suspend fun getBatch(batchId: Long): UninstallBatchEntity?

    @Query("SELECT * FROM uninstall_batch WHERE batchId = :batchId")
    fun observeBatch(batchId: Long): Flow<UninstallBatchEntity?>

    /** The resume-after-kill banner (PRD §6 item 4). */
    @Query("SELECT * FROM uninstall_batch WHERE finishedAt IS NULL AND discarded = 0 ORDER BY createdAt DESC LIMIT 1")
    fun observeUnfinishedBatch(): Flow<UninstallBatchEntity?>

    @Insert
    suspend fun insertItems(items: List<UninstallItemEntity>)

    @Update
    suspend fun updateItem(item: UninstallItemEntity)

    @Query("SELECT * FROM uninstall_item WHERE batchId = :batchId ORDER BY position")
    suspend fun getItems(batchId: Long): List<UninstallItemEntity>

    @Query("SELECT * FROM uninstall_item WHERE batchId = :batchId ORDER BY position")
    fun observeItems(batchId: Long): Flow<List<UninstallItemEntity>>

    /** The batch and its ordered items land together or not at all, so a kill can't leave a half queue. */
    @Transaction
    suspend fun insertBatchWithItems(batch: UninstallBatchEntity, items: List<UninstallItemEntity>): Long {
        val batchId = insertBatch(batch)
        insertItems(items.map { it.copy(batchId = batchId) })
        return batchId
    }

    @Insert
    suspend fun insertHistoryEntry(entry: UninstallHistoryEntity): Long

    /**
     * The verified-success path (PRD Feature 4): the item turns REMOVED and its History row appears
     * in one transaction, so a kill in between can neither lose the row nor write it twice.
     */
    @Transaction
    suspend fun markRemoved(item: UninstallItemEntity, entry: UninstallHistoryEntity) {
        updateItem(item)
        insertHistoryEntry(entry)
    }

    /** Snapshots a live queue may still need; the icon sweep keeps these. */
    @Query(
        "SELECT iconPath FROM uninstall_item WHERE iconPath IS NOT NULL AND batchId IN " +
            "(SELECT batchId FROM uninstall_batch WHERE finishedAt IS NULL AND discarded = 0)"
    )
    suspend fun getUnfinishedIconPaths(): List<String>

    @Query(
        "DELETE FROM uninstall_item WHERE batchId IN (SELECT batchId FROM uninstall_batch " +
            "WHERE (finishedAt IS NOT NULL OR discarded = 1) AND createdAt < :before)"
    )
    suspend fun deleteItemsOfClosedBatchesBefore(before: Long)

    @Query("DELETE FROM uninstall_batch WHERE (finishedAt IS NOT NULL OR discarded = 1) AND createdAt < :before")
    suspend fun deleteClosedBatchesBefore(before: Long)
}

/** Owner: History. */
@Dao
interface HistoryDao {
    @Query("SELECT * FROM uninstall_history ORDER BY removedAt DESC")
    fun observeAll(): Flow<List<UninstallHistoryEntity>>

    @Insert
    suspend fun insert(entry: UninstallHistoryEntity): Long

    @Query("DELETE FROM uninstall_history")
    suspend fun clear()

    /** Snapshots History still shows; the icon sweep keeps these. */
    @Query("SELECT iconPath FROM uninstall_history WHERE iconPath IS NOT NULL")
    suspend fun getIconPaths(): List<String>

    /** PRD Feature 4: capped at 1,000 rows, oldest pruned. */
    @Query("DELETE FROM uninstall_history WHERE id NOT IN (SELECT id FROM uninstall_history ORDER BY removedAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 1_000)
}
