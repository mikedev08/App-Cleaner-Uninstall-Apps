package com.jedy.appcleaner.uninstaller.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema for V1. Each entity has one owning feature (noted per class); only that feature
 * edits the entity and its DAO. The database is still at version 1 and uses destructive
 * migration during development, so owners may add columns freely until release.
 */

/** Owner: Inventory (Feature 1). Cache-first app list: cold start renders from here. */
@Entity(tableName = "inventory_app")
data class InventoryAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val versionName: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val installerPackage: String?,
    val apkBytes: Long,
    val storageUuid: String?,
    val uid: Int,
    val refreshedAt: Long,
)

/** Owner: Insights (Feature 3). StorageStatsManager results, stale after 24h or on update. */
@Entity(tableName = "app_size")
data class AppSizeEntity(
    @PrimaryKey val packageName: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val measuredAt: Long,
    /** lastUpdateTime of the package when measured; a newer install invalidates the row. */
    val packageUpdatedAt: Long,
)

/** Owner: Uninstall (Feature 2). One confirmed batch. */
@Entity(tableName = "uninstall_batch")
data class UninstallBatchEntity(
    @PrimaryKey(autoGenerate = true) val batchId: Long = 0,
    val createdAt: Long,
    val sourceTab: String,
    val finishedAt: Long? = null,
    val stoppedEarly: Boolean = false,
    val discarded: Boolean = false,
)

/** Owner: Uninstall (Feature 2). One app in a batch, with its pre-dialog snapshot. */
@Entity(
    tableName = "uninstall_item",
    indices = [Index("batchId")],
)
data class UninstallItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val position: Int,
    val packageName: String,
    val label: String,
    val installerPackage: String?,
    val versionName: String?,
    val snapshotBytes: Long,
    val bytesIsEstimate: Boolean,
    /** App-private PNG of the icon, written before the dialog opens (PRD §0 decision 2). */
    val iconPath: String?,
    /** WAITING | IN_PROGRESS | REMOVED | SKIPPED | FAILED | ALREADY_REMOVED */
    val state: String,
    val statusCode: Int? = null,
    val failureReason: String? = null,
    val updatedAt: Long,
)

/** Owner: History (Feature 4). Written only from the verified-success path. */
@Entity(
    tableName = "uninstall_history",
    indices = [Index("removedAt")],
)
data class UninstallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val iconPath: String?,
    val bytes: Long,
    val bytesIsEstimate: Boolean,
    val installerPackage: String?,
    val versionName: String?,
    val removedAt: Long,
)
