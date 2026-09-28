package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.storage.StorageManager
import com.jedy.appcleaner.uninstaller.core.analytics.SnapshotDegradedReason
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIconCache
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Icon snapshots as 96px PNGs (PRD Feature 2) under `noBackupFilesDir/icons`: app-private, and
 * outside auto-backup because the icons of removed apps have no value on a new device (PRD
 * Feature 4) — without having to edit the shared backup rules.
 *
 * People usually open the app because storage is almost full (PRD §6 "Storage almost full"), so
 * the PNG is optional: under [LOW_SPACE_BYTES] free, or when the write fails, the snapshot is
 * text-only and History draws a neutral placeholder. Nothing here can fail a removal.
 */
@Singleton
class FileIconSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IconSnapshotStore {

    private val dir: File get() = File(context.noBackupFilesDir, DIR)

    override suspend fun save(packageName: String, key: String): IconSnapshot = withContext(Dispatchers.IO) {
        // Uninstalled already, or no icon: nothing to save, and nothing degraded either.
        val icon: Drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return@withContext IconSnapshot(path = null)
        if (!hasRoomForIcons(context)) return@withContext IconSnapshot(path = null, SnapshotDegradedReason.LOW_SPACE)

        val file = File(dir, "${key.filter { it.isLetterOrDigit() || it == '.' || it == '_' }}.png")
        try {
            val bitmap = icon.toBitmap(ICON_PX, ICON_PX)
            dir.mkdirs()
            // compress() reports ENOSPC as false, or as an IOException on flush/close.
            val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!written) throw IOException("PNG not written")
            IconSnapshot(path = file.absolutePath)
        } catch (_: IOException) {
            file.delete()
            IconSnapshot(path = null, SnapshotDegradedReason.IO_ERROR)
        } catch (_: RuntimeException) {
            // An odd drawable (e.g. zero-size) is "no icon", not a storage problem.
            file.delete()
            IconSnapshot(path = null)
        }
    }

    override suspend fun delete(paths: Collection<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path -> File(path).takeIf { it.parentFile == dir }?.delete() }
    }

    override suspend fun sweep(keep: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().filter { it.absolutePath !in keep }.forEach { it.delete() }
    }

    override fun evictLiveIcon(packageName: String) = AppIconCache.evict(packageName)

    companion object {
        private const val DIR = "icons"
        private const val ICON_PX = 96

        /** PRD §6 "Storage almost full": below this, the app writes no optional files. */
        const val LOW_SPACE_BYTES = 5L * 1024 * 1024

        /**
         * `getAllocatableBytes` counts cache Android would clear for us, so it is the honest
         * "can this write succeed" number. It does binder + disk work: call it off the main thread.
         * An error reading it is treated as "no room" — the PNG is optional, the removal is not.
         */
        fun hasRoomForIcons(context: Context): Boolean = try {
            val storage = context.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(StorageManager.UUID_DEFAULT) >= LOW_SPACE_BYTES
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
