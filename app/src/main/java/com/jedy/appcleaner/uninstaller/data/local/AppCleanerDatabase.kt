package com.jedy.appcleaner.uninstaller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        InventoryAppEntity::class,
        AppSizeEntity::class,
        UninstallBatchEntity::class,
        UninstallItemEntity::class,
        UninstallHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppCleanerDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun appSizeDao(): AppSizeDao
    abstract fun uninstallDao(): UninstallDao
    abstract fun historyDao(): HistoryDao
}
