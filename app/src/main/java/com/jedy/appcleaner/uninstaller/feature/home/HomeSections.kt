package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.StatTile
import com.jedy.appcleaner.uninstaller.core.ui.component.StorageGauge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.core.ui.theme.color
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult

/** Small wordmark on the start side, History and Settings on the end. */
@Composable
internal fun HomeTopBar(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().height(64.dp).padding(start = Dimens.gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.home_action_history), tint = colors.textPrimary)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.home_action_settings), tint = colors.textPrimary)
        }
    }
}

/**
 * The hero: a 240dp gauge that sweeps in and turns amber at 75% / red at 90%, the percentage
 * counting up inside it, then the real "5.8 GB of 8 GB" and a headline that only says "almost
 * full" when [SeverityRules.storage] says so.
 */
@Composable
internal fun StorageHero(storage: DeviceStorage?, usedFraction: Float, severity: Severity, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val percent = rememberPercentFormat()
    val known = storage != null && storage.totalBytes > 0
    val shown = rememberCountUp(if (known) usedFraction else 0f)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StorageGauge(usedFraction = if (known) usedFraction else 0f, severity = severity, size = 240.dp, strokeWidth = 22.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (known) percent(shown) else "–",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
                Text(stringResource(R.string.home_gauge_used), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (storage == null) return@Column
        if (!known) {
            Text(
                stringResource(R.string.home_storage_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.home_hero_used_of, formatBytes(context, storage.usedBytes), formatBytes(context, storage.totalBytes)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                when (severity) {
                    Severity.DANGER -> R.string.home_headline_danger
                    Severity.WARNING -> R.string.home_headline_warning
                    Severity.OK -> R.string.home_headline_ok
                },
            ),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        val free = formatBytes(context, storage.freeBytes)
        Text(
            text = if (severity == Severity.OK) stringResource(R.string.home_subline_free, free) else stringResource(R.string.home_subline_left, free),
            style = MaterialTheme.typography.titleMedium,
            color = if (severity == Severity.OK) colors.accentText else severity.color,
            textAlign = TextAlign.Center,
        )
    }
}

/** Before any scan: the big green "Scan my phone". */
@Composable
internal fun ScanCallToAction(onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryButton(
            text = stringResource(R.string.home_scan_cta),
            onClick = onOpenScan,
            icon = Icons.Rounded.Radar,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_scan_cta_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * After a scan: "You can free up 3.4 GB" in amber or red when [SeverityRules.appSize] calls the
 * amount large — the same rule that flags a single space hog — plus when, and "Scan again".
 */
@Composable
internal fun LastScanCard(result: ScanResult, onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    // A quick scan (no Usage Access) cannot know what is unused, so it quotes the space hogs.
    val full = result.hasUsageAccess
    val bytes = if (full) result.reclaimableBytes else result.largeBytes
    val severity = if (full) SeverityRules.appSize(bytes, result.storage.totalBytes) else if (result.largeCount > 0) Severity.DANGER else Severity.OK
    val shown = rememberCountUp(bytes.toFloat())
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_last_scan_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(relativeTime(result.scannedAt), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        if (bytes > 0) {
            Text(stringResource(if (full) R.string.home_last_scan_free_up_prefix else R.string.home_last_scan_hogs_prefix), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = formatBytes(context, shown.toLong()),
                style = MaterialTheme.typography.displaySmall,
                color = if (severity == Severity.OK) colors.textPrimary else severity.color,
            )
        } else {
            Text(stringResource(R.string.home_last_scan_tidy), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.home_last_scan_again),
            onClick = onOpenScan,
            icon = Icons.Rounded.Radar,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The 2×2 dashboard. Numbers come from the last scan (or the same maths over live data before
 * one), so they match what the Scan result said. Tiles that need Usage Access say so and route
 * to the disclosure instead of showing a misleading zero.
 */
@Composable
internal fun StatGrid(
    summary: ScanResult?,
    freed: FreedSoFar,
    isPremium: Boolean,
    hasUsageAccess: Boolean,
    onOpenUnused: () -> Unit,
    onOpenHogs: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenHistory: () -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholder = "–"
    // IntrinsicSize.Min + fillMaxHeight: both tiles in a row share the taller one's height.
    val tile = Modifier.fillMaxHeight()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hasUsageAccess) {
                AccessTile(Icons.Rounded.HourglassBottom, stringResource(R.string.home_tile_unused), onAllowAccess, Modifier.weight(1f).then(tile))
            } else {
                val unusedCount = summary?.unusedCount ?: 0
                StatTile(
                    icon = Icons.Rounded.HourglassBottom,
                    label = stringResource(R.string.home_tile_unused),
                    value = summary?.let { unusedCount.toString() } ?: placeholder,
                    caption = summary?.let { formatBytes(context, it.unusedBytes) },
                    severity = if (unusedCount > 0) Severity.DANGER else Severity.OK,
                    locked = !isPremium,
                    onClick = onOpenUnused,
                    modifier = Modifier.weight(1f).then(tile),
                )
            }
            val hogCount = summary?.largeCount ?: 0
            StatTile(
                icon = Icons.Rounded.LocalFireDepartment,
                label = stringResource(R.string.home_tile_hogs),
                value = summary?.largeCount?.toString() ?: placeholder,
                caption = summary?.let { formatBytes(context, it.largeBytes) },
                severity = if (hogCount > 0) Severity.DANGER else Severity.OK,
                onClick = onOpenHogs,
                modifier = Modifier.weight(1f).then(tile),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hasUsageAccess) {
                AccessTile(Icons.Rounded.Layers, stringResource(R.string.home_tile_cache), onAllowAccess, Modifier.weight(1f).then(tile))
            } else {
                val cacheBytes = summary?.cacheBytes ?: 0L
                StatTile(
                    icon = Icons.Rounded.Layers,
                    label = stringResource(R.string.home_tile_cache),
                    value = summary?.let { formatBytes(context, cacheBytes) } ?: placeholder,
                    caption = if (summary != null && summary.cacheAppCount > 0) {
                        pluralStringResource(R.plurals.home_tile_cache_caption, summary.cacheAppCount, summary.cacheAppCount)
                    } else {
                        null
                    },
                    severity = summary?.let { SeverityRules.appSize(cacheBytes, it.storage.totalBytes) } ?: Severity.OK,
                    locked = !isPremium,
                    onClick = onOpenCache,
                    modifier = Modifier.weight(1f).then(tile),
                )
            }
            StatTile(
                icon = Icons.Rounded.AutoDelete,
                label = stringResource(R.string.home_tile_freed),
                value = formatBytes(context, freed.bytes),
                caption = if (freed.count > 0) {
                    pluralStringResource(R.plurals.home_tile_freed_caption, freed.count, freed.count)
                } else {
                    stringResource(R.string.home_tile_freed_none)
                },
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f).then(tile),
            )
        }
    }
}

/**
 * A tile whose number needs Usage Access: rather than a misleading zero it names what it would
 * show and offers the pill that leads to the disclosure.
 */
@Composable
private fun AccessTile(icon: ImageVector, label: String, onAllowAccess: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier, onClick = onAllowAccess) {
        IconBadge(icon = icon)
        Spacer(Modifier.height(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(colors.accentSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.home_tile_allow), style = MaterialTheme.typography.labelMedium, color = colors.accentText, maxLines = 1)
        }
    }
}
