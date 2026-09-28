package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar
import com.jedy.appcleaner.uninstaller.core.ui.component.PlaceholderLine
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.StorageGauge
import com.jedy.appcleaner.uninstaller.core.ui.component.TopBarAction
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult

/** The shared [AppTopBar]: wordmark on the start side, outlined History and Settings at the end. */
@Composable
internal fun HomeTopBar(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit, scrolled: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppTopBar(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        scrolled = scrolled,
        navigationIcon = {
            // The bar starts 8dp in (for a 48dp button); 12dp more puts the logo on the 20dp gutter.
            Box(
                Modifier
                    .padding(start = Dimens.space12, end = Dimens.space4)
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(19.dp))
            }
        },
        actions = {
            TopBarAction(Icons.Outlined.History, stringResource(R.string.home_action_history), onOpenHistory)
            TopBarAction(Icons.Outlined.Settings, stringResource(R.string.home_action_settings), onOpenSettings)
        },
    )
}

/**
 * The storage gauge: a 200dp ring that sweeps in and turns amber at 75% / red at 90% (the ring is
 * an indicator; no text is ever amber or red), the percentage counting up inside, and
 * "5.3 GB of 8.0 GB" in the ring's open bottom. Under it, one short status line and "X free".
 *
 * Every line has its final height while loading (placeholders), so nothing jumps.
 */
@Composable
internal fun StorageHero(storage: DeviceStorage?, usedFraction: Float, severity: Severity, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val percent = rememberPercentFormat()
    val known = storage != null && storage.totalBytes > 0
    val shown = rememberCountUp(if (known) usedFraction else 0f)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StorageGauge(usedFraction = if (known) usedFraction else 0f, severity = severity, size = GaugeSize, strokeWidth = 18.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (known) percent(shown) else "–",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary,
                )
                Text(stringResource(R.string.home_gauge_used), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
            }
            // The ring's open bottom (the 90° gap) is free across the full width: "5.3 GB of 8 GB".
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.space4)) {
                when {
                    storage == null -> PlaceholderLine(MaterialTheme.typography.bodyMedium, Modifier.size(width = 96.dp, height = 20.dp), widthFraction = 1f)
                    known -> Text(
                        text = stringResource(R.string.home_hero_used_of, formatBytes(context, storage.usedBytes), formatBytes(context, storage.totalBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.space8))
        val statusStyle = MaterialTheme.typography.headlineSmall
        val freeStyle = MaterialTheme.typography.titleMedium
        when {
            storage == null -> {
                PlaceholderLine(statusStyle, widthFraction = 0.45f)
                Spacer(Modifier.height(Dimens.space4))
                PlaceholderLine(freeStyle, widthFraction = 0.25f)
            }
            !known -> Text(
                stringResource(R.string.home_storage_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            else -> {
                Text(
                    text = stringResource(
                        when (severity) {
                            Severity.DANGER -> R.string.home_headline_danger
                            Severity.WARNING -> R.string.home_headline_warning
                            Severity.OK -> R.string.home_headline_ok
                        },
                    ),
                    style = statusStyle,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Dimens.space4))
                val free = formatBytes(context, storage.freeBytes)
                Text(
                    text = if (severity == Severity.OK) stringResource(R.string.home_subline_free, free) else stringResource(R.string.home_subline_left, free),
                    style = freeStyle,
                    color = if (severity == Severity.OK) colors.positive else colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The one state-driven hero card (design review §2.6):
 * - never scanned: "See what you can remove" → **Scan my phone**;
 * - scanned: "You can free up X" in green → **Review** · a small "Scan again" link;
 * - scanned, nothing to free: "Nothing big to clean up" → **Scan again**.
 *
 * X is the sum of the category rows below it (see [ScanResult]).
 */
@Composable
internal fun HomeHeroCard(
    result: ScanResult?,
    onScan: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier.fillMaxWidth()) {
        if (result == null) {
            Text(stringResource(R.string.home_hero_new_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(Dimens.space4))
            Text(stringResource(R.string.home_scan_cta_caption), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.height(Dimens.space16))
            PrimaryButton(
                text = stringResource(R.string.home_scan_cta),
                onClick = onScan,
                icon = Icons.Rounded.Radar,
                modifier = Modifier.fillMaxWidth(),
            )
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_last_scan_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(relativeTime(result.scannedAt), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Spacer(Modifier.height(Dimens.space8))
        val bytes = result.reclaimableBytes
        if (bytes > 0) {
            val shown = rememberCountUp(bytes.toFloat())
            Text(stringResource(R.string.home_last_scan_free_up_prefix), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = formatBytes(LocalContext.current, shown.toLong()),
                style = MaterialTheme.typography.displaySmall,
                color = colors.positive,
                maxLines = 1,
            )
            Spacer(Modifier.height(Dimens.space16))
            PrimaryButton(text = stringResource(R.string.home_review), onClick = onReview, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Dimens.space4))
            TextAction(
                text = stringResource(R.string.home_last_scan_again),
                onClick = onScan,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Text(stringResource(R.string.home_last_scan_tidy), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Spacer(Modifier.height(Dimens.space16))
            PrimaryButton(
                text = stringResource(R.string.home_last_scan_again),
                onClick = onScan,
                icon = Icons.Rounded.Radar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val GaugeSize = 200.dp
