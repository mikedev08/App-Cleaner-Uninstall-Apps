package com.jedy.appcleaner.uninstaller.feature.insights

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.component.PremiumCrown
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences

/** "14 apps · 3.4 GB" — digits and units follow the in-app locale. */
@Composable
internal fun countAndSize(count: Int, bytes: Long): String = stringResource(
    R.string.insights_count_and_size,
    pluralStringResource(R.plurals.insights_apps_count, count, count),
    formatBytes(LocalContext.current, bytes),
)

/** PRD §4 Screen 5: 30 · 60 · 90 days. Persisted, and shared with the reminder worker. */
@Composable
internal fun ThresholdChips(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppPreferences.UNUSED_THRESHOLDS.forEach { days ->
            val isSelected = days == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(days) },
                label = { Text(pluralStringResource(R.plurals.insights_days, days, days)) },
                shape = RoundedCornerShape(Dimens.chipRadius),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AppTheme.colors.background,
                    labelColor = AppTheme.colors.textSecondary,
                    selectedContainerColor = AppTheme.colors.tealSurface,
                    selectedLabelColor = AppTheme.colors.teal,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = AppTheme.colors.border,
                    selectedBorderColor = AppTheme.colors.teal,
                ),
            )
        }
    }
}

/** State (a): the inline "Allow access" card. Opens the disclosure (Screen 11), never Settings directly. */
@Composable
internal fun AccessCard(
    icon: ImageVector,
    title: String,
    body: String,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.gutter),
        shape = RoundedCornerShape(Dimens.cardRadius),
        color = AppTheme.colors.surface,
        border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
    ) {
        EmptyState(
            icon = icon,
            title = title,
            body = body,
            action = {
                TealButton(text = stringResource(R.string.insights_allow_access), onClick = onRequestAccess)
            },
        )
    }
}

@Composable
internal fun TealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
        shape = RoundedCornerShape(Dimens.controlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.teal,
            contentColor = AppTheme.colors.onTeal,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/**
 * State (b): the real finding in clear above redacted rows — "the number is the pitch"
 * (PRD §0 decision 3). Rows are never real text: labels are drawn as bars, so nothing is
 * readable on any API level or through a screen reader. On API 31+ the preview (with the real,
 * unrecognisable icons) is additionally blurred; below 31, where blur is a no-op, icons become
 * solid placeholders instead.
 */
@Composable
internal fun LockedTeaser(
    headline: String,
    caption: String?,
    body: String,
    previewPackages: List<String>,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumCrown(size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = caption, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(Dimens.gutterSmall))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Spacer(Modifier.height(Dimens.gutterSmall))
        // The CTA sits above the preview so it is on screen on every phone height; below the
        // rows it fell under the fold, hiding the one action this state exists for.
        TealButton(
            text = stringResource(R.string.insights_unlock_trial),
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.gutter))
        RedactedPreview(
            packages = previewPackages,
            rows = previewPackages.size.coerceIn(MIN_PREVIEW_ROWS, MAX_PREVIEW_ROWS),
            description = stringResource(R.string.insights_locked_rows_description),
        )
    }
}

@Composable
private fun RedactedPreview(packages: List<String>, rows: Int, description: String) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(Dimens.hairline, AppTheme.colors.border, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .then(if (canBlur) Modifier.blur(10.dp) else Modifier)
                .padding(vertical = 4.dp),
        ) {
            repeat(rows) { index ->
                RedactedRow(packageName = packages.getOrNull(index)?.takeIf { canBlur }, index = index)
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to AppTheme.colors.surface,
                    )
                )
        )
    }
}

@Composable
private fun RedactedRow(packageName: String?, index: Int) {
    val ink = AppTheme.colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.appRowHeight)
            .padding(horizontal = Dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (packageName != null) {
            AppIcon(packageName)
        } else {
            Box(
                Modifier
                    .size(Dimens.appIconSize)
                    .clip(RoundedCornerShape(Dimens.appIconSize / 4))
                    .background(AppTheme.colors.surfaceMuted)
            )
        }
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bar(widthFraction = LABEL_WIDTHS[index % LABEL_WIDTHS.size], height = 12, color = ink.copy(alpha = 0.35f))
            Bar(widthFraction = META_WIDTHS[index % META_WIDTHS.size], height = 10, color = ink.copy(alpha = 0.2f))
        }
        Spacer(Modifier.width(Dimens.gutterSmall))
        Box(
            Modifier
                .width(44.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(ink.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun Bar(widthFraction: Float, height: Int, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp)
            .clip(RoundedCornerShape((height / 2).dp))
            .background(color)
    )
}

/**
 * List header with the tab's summary and the "Select all 14" control (PRD §4 Screen 4/5). The
 * tri-state box sits in the same column as the rows' checkboxes. Selection covers the rows
 * currently visible under the search filter; nothing hidden is ever added silently.
 */
@Composable
internal fun SelectAllHeader(
    summary: String,
    visiblePackages: List<String>,
    selected: Set<String>,
    onSelectAll: (packages: List<String>, select: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val selectedCount = visiblePackages.count { it in selected }
    val state = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == visiblePackages.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = Dimens.gutter, end = Dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            extra?.invoke()
        }
        if (visiblePackages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .toggleable(
                        value = state == ToggleableState.On,
                        role = Role.Checkbox,
                        onValueChange = { onSelectAll(visiblePackages, state != ToggleableState.On) },
                    )
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.insights_select_all, visiblePackages.size, visiblePackages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.teal,
                )
                TriStateCheckbox(
                    state = state,
                    onClick = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppTheme.colors.teal,
                        checkmarkColor = AppTheme.colors.onTeal,
                        uncheckedColor = AppTheme.colors.textSecondary,
                    ),
                )
            }
        }
    }
}

/** Colors of the three size segments, shared by the bar and its legend. */
private data class SegmentColors(val app: Color, val data: Color, val cache: Color)

@Composable
private fun segmentColors() = SegmentColors(
    app = AppTheme.colors.teal,
    data = AppTheme.colors.teal.copy(alpha = 0.45f),
    cache = AppTheme.colors.storageOther,
)

/**
 * PRD §4 Screen 6: the thin three-part bar (app / data / cache). Its length is the app's share of
 * the largest app, so the ranking reads at a glance; a Row with weights mirrors itself in RTL.
 */
@Composable
internal fun SizeBar(size: AppSize, largestBytes: Long, modifier: Modifier = Modifier) {
    val total = size.totalBytes
    val colors = segmentColors()
    val length = if (largestBytes > 0) (total.toFloat() / largestBytes).coerceIn(0.03f, 1f) else 1f
    Box(
        modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppTheme.colors.surfaceMuted)
    ) {
        Row(Modifier.fillMaxWidth(length).fillMaxHeight()) {
            listOf(size.appBytes to colors.app, size.dataBytes to colors.data, size.cacheBytes to colors.cache)
                .filter { it.first > 0 && total > 0 }
                .forEach { (bytes, color) ->
                    Box(
                        Modifier
                            .weight(bytes.toFloat() / total)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
        }
    }
}

@Composable
internal fun SizeLegend(modifier: Modifier = Modifier) {
    val colors = segmentColors()
    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(colors.app, stringResource(R.string.insights_legend_app))
        LegendItem(colors.data, stringResource(R.string.insights_legend_data))
        LegendItem(colors.cache, stringResource(R.string.insights_legend_cache))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
    }
}

@Composable
internal fun InsightsLoading(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = Dimens.gutterLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
    ) {
        CircularProgressIndicator(
            color = AppTheme.colors.teal,
            trackColor = AppTheme.colors.surfaceMuted,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MIN_PREVIEW_ROWS = 3
private const val MAX_PREVIEW_ROWS = 5
private val LABEL_WIDTHS = floatArrayOf(0.62f, 0.45f, 0.72f, 0.5f, 0.58f)
private val META_WIDTHS = floatArrayOf(0.4f, 0.52f, 0.34f, 0.46f, 0.38f)
