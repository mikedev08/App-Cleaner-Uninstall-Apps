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
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.BigNumber
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.feature.home.Category
import com.jedy.appcleaner.uninstaller.feature.home.CategoryCard
import com.jedy.appcleaner.uninstaller.feature.home.HomeCategories
import com.jedy.appcleaner.uninstaller.feature.home.rememberCountUp
import com.jedy.appcleaner.uninstaller.feature.home.rememberPercentFormat

/**
 * The result, the one place with the full breakdown (design review §2.6): the headline counting
 * up in green, once; the before → after bar with its legend; and the Unused · Large · Cache rows
 * in one grouped card — the same rows as Home, each its whole category. The headline
 * ([ScanResult.reclaimableBytes]) counts every app once, and a plain line under the rows says so
 * when an app is in two of them; a row with nothing in it is a quiet line, never a chevron to an
 * empty list.
 */
@Composable
internal fun ScanResultContent(
    result: ScanResult,
    isPremium: Boolean,
    onOpen: (Category) -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val headlineBytes = result.reclaimableBytes
    val shown = rememberCountUp(headlineBytes.toFloat(), durationMillis = 1_400)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.scan_result_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.positive,
        )
        Spacer(Modifier.height(Dimens.space12))
        if (headlineBytes > 0) {
            BigNumber(
                value = formatBytes(context, shown.toLong()),
                caption = stringResource(R.string.scan_result_could_free),
                color = colors.positive,
            )
        } else {
            IconBadge(icon = Icons.Rounded.Spa, size = 72.dp)
            Spacer(Modifier.height(Dimens.space16))
            Text(stringResource(R.string.scan_result_tidy_title), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.scan_result_tidy_body), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (headlineBytes > 0 && result.storage.totalBytes > 0) {
            Spacer(Modifier.height(Dimens.space24))
            BeforeAfter(result)
        }
        Spacer(Modifier.height(Dimens.space24))
        CategoryCard(
            rows = HomeCategories.rows(result, isPremium),
            onOpen = onOpen,
            onAllowAccess = onAllowAccess,
            overlapCount = HomeCategories.overlapCount(result),
        )
        if (result.sizesAreEstimates) {
            Spacer(Modifier.height(Dimens.space12))
            Text(
                stringResource(R.string.scan_estimates_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The storage bar: "Now" (a light green, today's fill) and "After cleanup" (solid green, what
 * stays once the headline is freed), with a legend so the two tones explain themselves. The
 * solid part slides back from today's fill to the after-cleanup fill.
 */
@Composable
private fun BeforeAfter(result: ScanResult) {
    val colors = AppTheme.colors
    val percent = rememberPercentFormat()
    val before = ScanMath.usedFraction(result.storage)
    val after = ScanMath.usedFractionAfter(result)
    val nowColor = colors.accent.copy(alpha = 0.35f)
    val afterColor = colors.accent
    val slide = remember { Animatable(before) }
    LaunchedEffect(after) { slide.animateTo(after, tween(durationMillis = 1_200, delayMillis = 500, easing = FastOutSlowInEasing)) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(colors.gaugeTrack)) {
            Box(Modifier.fillMaxWidth(before.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(nowColor))
            Box(Modifier.fillMaxWidth(slide.value.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(afterColor))
        }
        Spacer(Modifier.height(Dimens.space12))
        // Stacked, so both labels stay whole at 360dp in every language.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space4)) {
            LegendItem(nowColor, stringResource(R.string.scan_legend_now, percent(before)))
            LegendItem(afterColor, stringResource(R.string.scan_legend_after, percent(after)))
        }
    }
}

@Composable
private fun LegendItem(swatch: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(swatch))
        Spacer(Modifier.width(Dimens.space8))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary, maxLines = 1)
    }
}
