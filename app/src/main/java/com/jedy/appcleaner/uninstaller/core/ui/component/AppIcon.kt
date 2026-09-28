package com.jedy.appcleaner.uninstaller.core.ui.component

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide icon cache keyed by package + pixel size. Icons load off the main thread.
 *
 * Memory only, on purpose: it never writes to disk, so it keeps working on a phone with 0 bytes
 * free (PRD §6 "Storage almost full" asks for no icon-cache disk writes under 5 MB free; with no
 * disk tier there is nothing to switch off). Keep it that way — do not add a disk cache here.
 */
object AppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(packageName: String, px: Int): ImageBitmap? = cache.get("$packageName@$px")

    suspend fun load(context: Context, packageName: String, px: Int): ImageBitmap? {
        peek(packageName, px)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap()
            }.getOrNull()?.also { cache.put("$packageName@$px", it) }
        }
    }

    /** Called after an uninstall so a reinstall with a new icon is not served stale. */
    fun evict(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName@") }.forEach(cache::remove)
    }

    /** True when [packageName] is installed (icon lookups for uninstalled apps must use a snapshot). */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }
}

/** An installed app's launcher icon, with a neutral placeholder while it loads. */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.appIconSize,
) {
    val context = LocalContext.current.applicationContext
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(packageName, px) { mutableStateOf(AppIconCache.peek(packageName, px)) }
    LaunchedEffect(packageName, px) {
        if (bitmap == null) bitmap = AppIconCache.load(context, packageName, px)
    }
    Box(modifier.size(size)) {
        val current = bitmap
        if (current != null) {
            Image(bitmap = current, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size / 3.4f))
                    .background(AppTheme.colors.surfaceMuted)
            )
        }
    }
}
