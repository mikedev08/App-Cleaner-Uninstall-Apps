package com.jedy.appcleaner.uninstaller.feature.history

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded snapshot PNGs, keyed by path. Small: 96px icons, a screenful at a time. */
private object SnapshotIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(path: String): ImageBitmap? = peek(path) ?: withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()?.also { cache.put(path, it) }
    }
}

/**
 * The icon saved before the app was removed (PRD §0 decision 2) — PackageManager has nothing for
 * an uninstalled package, which is why other history screens show blank icons. Falls back to the
 * live icon (or its neutral placeholder) when there is no snapshot, e.g. an app still installed.
 */
@Composable
fun SnapshotAppIcon(
    iconPath: String?,
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.appIconSize,
) {
    var bitmap by remember(iconPath) { mutableStateOf(iconPath?.let(SnapshotIconCache::peek)) }
    var missing by remember(iconPath) { mutableStateOf(iconPath == null) }
    LaunchedEffect(iconPath) {
        if (iconPath != null && bitmap == null) {
            bitmap = SnapshotIconCache.load(iconPath)
            missing = bitmap == null
        }
    }
    val current = bitmap
    when {
        current != null -> Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
        missing -> AppIcon(packageName = packageName, modifier = modifier, size = size)
        else -> Box(
            modifier.size(size).clip(RoundedCornerShape(size / 4)).background(AppTheme.colors.surfaceMuted)
        )
    }
}
