package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.BigNumber
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.core.ui.theme.color
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.feature.home.rememberCountUp
import com.jedy.appcleaner.uninstaller.feature.home.rememberPercentFormat

/**
 * The result: the headline figure counting up in severity colour, a loss-framed line, the
 * before → after bar and the three buckets. Everything shown is a field of [ScanResult]; with
 * no Usage Access the headline honestly switches to what the space hogs take.
 */
@Composable
internal fun ScanResultContent(
    result: ScanResult,
    isPremium: Boolean,
    onOpenUnused: () -> Unit,
    onOpenHogs: () -> Unit,
    onOpenCache: () -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val total = result.storage.totalBytes
    val full = result.hasUsageAccess
    val headlineBytes = if (full) result.reclaimableBytes else result.largeBytes
    val severity = if (full) SeverityRules.appSize(headlineBytes, total) else if (result.largeCount > 0) Severity.DANGER else Severity.OK
    val shown = rememberCountUp(headlineBytes.toFloat(), durationMillis = 1_400)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.scan_result_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accentText,
        )
        Spacer(Modifier.height(12.dp))
        if (headlineBytes > 0) {
            BigNumber(
                value = formatBytes(context, shown.toLong()),
                caption = stringResource(if (full) R.string.scan_result_could_free else R.string.scan_result_hogs_take),
                color = if (severity == Severity.OK) colors.textPrimary else severity.color,
            )
        } else {
            IconBadge(icon = Icons.Rounded.Spa, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.scan_result_tidy_title), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.scan_result_tidy_body), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (full && result.unusedCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.scan_result_loss,
                    result.unusedCount,
                    result.unusedCount,
                    result.thresholdDays,
                    formatBytes(context, result.unusedBytes),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        if (full && result.reclaimableBytes > 0 && total > 0) {
            Spacer(Modifier.height(24.dp))
            BeforeAfter(result)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BucketCard(
                icon = Icons.Rounded.HourglassBottom,
                title = stringResource(R.string.scan_bucket_unused),
                meta = if (full) countAndSize(result.unusedCount, result.unusedBytes) else stringResource(R.string.scan_bucket_needs_access),
                severity = if (full && result.unusedCount > 0) Severity.DANGER else Severity.OK,
                locked = full && !isPremium,
                onClick = if (full) onOpenUnused else onAllowAccess,
            )
            BucketCard(
                icon = Icons.Rounded.LocalFireDepartment,
                title = stringResource(R.string.scan_bucket_hogs),
                meta = countAndSize(result.largeCount, result.largeBytes),
                severity = if (result.largeCount > 0) Severity.DANGER else Severity.OK,
                locked = false,
                onClick = onOpenHogs,
            )
            BucketCard(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.scan_bucket_cache),
                meta = if (full) formatBytes(context, result.cacheBytes) else stringResource(R.string.scan_bucket_needs_access),
                severity = if (full) SeverityRules.appSize(result.cacheBytes, total) else Severity.OK,
                locked = full && !isPremium,
                onClick = if (full) onOpenCache else onAllowAccess,
            )
        }
        if (result.sizesAreEstimates) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.scan_estimates_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun countAndSize(count: Int, bytes: Long): String = stringResource(
    R.string.scan_bucket_meta,
    pluralStringResource(R.plurals.home_list_count, count, count),
    formatBytes(LocalContext.current, bytes),
)

/**
 * "72% full → 48% after cleanup": the bar starts at today's fill and slides back to the
 * after-cleanup fill, leaving the reclaimable slice visible in severity colour.
 */
@Composable
private fun BeforeAfter(result: ScanResult) {
    val colors = AppTheme.colors
    val percent = rememberPercentFormat()
    val before = ScanMath.usedFraction(result.storage)
    val after = ScanMath.usedFractionAfter(result)
    val severity = SeverityRules.storage(before)
    val slide = remember { Animatable(before) }
    LaunchedEffect(after) { slide.animateTo(after, tween(durationMillis = 1_200, delayMillis = 500, easing = FastOutSlowInEasing)) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.scan_result_before_after, percent(before), percent(after)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(colors.gaugeTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(before.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background((if (severity == Severity.OK) colors.storageOther else severity.color).copy(alpha = 0.45f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(slide.value.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun BucketCard(
    icon: ImageVector,
    title: String,
    meta: String,
    severity: Severity,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, severity = severity, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (severity == Severity.OK) colors.textSecondary else severity.color,
                )
            }
            if (locked) ProBadge()
            else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        }
    }
}
