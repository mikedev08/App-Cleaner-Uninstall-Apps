package com.jedy.appcleaner.uninstaller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM app_size WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppSizeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sizes: List<AppSizeEntity>)

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

    /** PRD Feature 4: capped at 1,000 rows, oldest pruned. */
    @Query("DELETE FROM uninstall_history WHERE id NOT IN (SELECT id FROM uninstall_history ORDER BY removedAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 1_000)
}
