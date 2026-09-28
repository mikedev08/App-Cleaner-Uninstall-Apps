package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.Context
import android.graphics.Bitmap
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIconCache
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Icon snapshots as 96px PNGs (PRD Feature 2) under `noBackupFilesDir/icons`: app-private, and
 * outside auto-backup because the icons of removed apps have no value on a new device (PRD
 * Feature 4) — without having to edit the shared backup rules.
 */
@Singleton
class FileIconSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IconSnapshotStore {

    private val dir: File get() = File(context.noBackupFilesDir, DIR)

    override suspend fun save(packageName: String, key: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
            dir.mkdirs()
            val file = File(dir, "${key.filter { it.isLetterOrDigit() || it == '.' || it == '_' }}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }.getOrNull()
    }

    override suspend fun delete(paths: Collection<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path -> File(path).takeIf { it.parentFile == dir }?.delete() }
    }

    override suspend fun sweep(keep: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().filter { it.absolutePath !in keep }.forEach { it.delete() }
    }

    override fun evictLiveIcon(packageName: String) = AppIconCache.evict(packageName)

    private companion object {
        const val DIR = "icons"
        const val ICON_PX = 96
    }
}
