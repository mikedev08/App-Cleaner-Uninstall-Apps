package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/** Layout constants of [AppRow], for screens that draw a row-shaped placeholder or header. */
object AppRowDefaults {
    /**
     * Where a row sits in a full-width list: the 20dp page gutter left and right, half a
     * [Dimens.rowGap] above and below (so stacked rows are 8dp apart). Pass
     * `PaddingValues(0.dp)` when the row is already inside a padded container.
     */
    val ListPadding = PaddingValues(horizontal = Dimens.gutter, vertical = Dimens.rowGap / 2)
    val Shape = RoundedCornerShape(20.dp)
}

/**
 * The list row shared by every app list (design review §4 / §5 / 2A). Each row is its own soft
 * outlined card:
 *
 * ```
 * [icon]  Label ……………………………… 515 MB   (○)
 *         Installed Mar 2025 · [chip]
 *         ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 * ```
 *
 * Rules it enforces, so callers don't have to:
 * - **Full words, never "…".** The app name wraps to a second line when it needs one, and the row
 *   grows to fit; [Dimens.appRowHeight] is only the minimum height. Beyond two lines the name is
 *   clipped (no ellipsis), which only a pathological label ever reaches.
 * - **Chips never truncate, neither does the subtitle.** When subtitle + chip fit whole on one
 *   line they share it; otherwise the chip moves to its own line under the subtitle. Keep it to
 *   one chip.
 * - **Aligned right edges.** The size text is end-aligned on the title line and the bar spans the
 *   text column, whose width is fixed by the fixed-width check column — so size ends and bar ends
 *   line up down the whole list, whatever the size label says.
 * - **Neutral sizes.** [trailingText] is always textPrimary. Only the bar is coloured, by [large].
 * - **One gutter.** [outerPadding] defaults to [AppRowDefaults.ListPadding] (the 20dp page gutter),
 *   so place rows full-width in a list and do **not** add horizontal padding at the call site.
 *
 * @param meta the subtitle ("Installed Mar 2025"). Wraps rather than ellipsizes.
 * @param trailingText the size ("515 MB"), always textPrimary.
 * @param large `LargeApps.isLarge(bestKnownBytes)`: paints the default bar `sizeBarLarge`.
 * @param sizeFraction 0..1 of the largest app in the list for the default [SizeBar]; null = no bar.
 * @param bar replaces the default bar with custom bar-line content (e.g. a stacked app/data/cache
 *   bar). It gets the full text-column width and should be about 6–8dp tall.
 * @param chips one [SeverityChip], drawn on the subtitle line.
 * @param trailing custom trailing content (a pill, a state icon) before the check.
 * @param icon defaults to the live launcher icon; History passes its snapshot instead.
 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    meta: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onToggleSelected: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingText: String? = null,
    large: Boolean = false,
    sizeFraction: Float? = null,
    icon: @Composable () -> Unit = { AppIcon(packageName) },
    bar: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    outerPadding: PaddingValues = AppRowDefaults.ListPadding,
) {
    val colors = AppTheme.colors
    val container by animateColorAsState(
        if (selected == true) colors.accentSurface else colors.surface, label = "rowbg",
    )
    val outline by animateColorAsState(
        if (selected == true) colors.accent.copy(alpha = 0.45f) else colors.border, label = "rowborder",
    )
    val shape = AppRowDefaults.Shape
    Row(
        modifier = modifier
            .padding(outerPadding)
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(Dimens.hairline, outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Dimens.appRowHeight)
            .padding(start = Dimens.space12, end = Dimens.space4, top = Dimens.space8, bottom = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(Dimens.space12))
        Column(Modifier.weight(1f)) {
            Row {
                // Up to two lines, never "…": a long name wraps and the row grows.
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                if (trailingText != null) {
                    Spacer(Modifier.width(Dimens.space8))
                    // Sits on the name's first line, whether or not the name wraps.
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            MetaLine(meta = meta, chips = chips)
            if (bar != null || sizeFraction != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (bar != null) bar() else SizeBar(fraction = sizeFraction ?: 0f, large = large)
                }
            }
        }
        trailing?.invoke()
        if (selected != null) {
            RoundCheck(checked = selected, onToggle = onToggleSelected)
        } else {
            Spacer(Modifier.width(Dimens.space8))
        }
    }
}

/**
 * Subtitle + chip. They share one line when both fit whole; otherwise the chip goes on its own
 * line under the subtitle. Neither is ever ellipsized or dropped ("Install…" looks broken), and a
 * subtitle too long for the row wraps.
 */
@Composable
private fun MetaLine(meta: String, chips: (@Composable () -> Unit)?) {
    val colors = AppTheme.colors
    Layout(
        modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
        content = {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (chips != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { chips() }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val chip = measurables.getOrNull(1)?.measure(loose)
        val text = if (meta.isEmpty()) null else measurables[0]
        val gap = 8.dp.roundToPx()
        val oneLine = chip == null || text == null ||
            text.maxIntrinsicWidth(Constraints.Infinity) + gap + chip.width <= width
        if (oneLine) {
            val metaPlaceable = text?.measure(
                Constraints(maxWidth = (width - (chip?.let { it.width + gap } ?: 0)).coerceAtLeast(0)),
            )
            val height = maxOf(constraints.minHeight, metaPlaceable?.height ?: 0, chip?.height ?: 0)
            layout(width, height) {
                var x = 0
                if (metaPlaceable != null) {
                    metaPlaceable.placeRelative(0, (height - metaPlaceable.height) / 2)
                    x = metaPlaceable.width + gap
                }
                chip?.placeRelative(x, (height - chip.height) / 2)
            }
        } else {
            // Subtitle first (wrapping if it must), then the chip on a line of its own.
            val metaPlaceable = text!!.measure(loose)
            val lineGap = 4.dp.roundToPx()
            val height = maxOf(constraints.minHeight, metaPlaceable.height + lineGap + chip!!.height)
            layout(width, height) {
                metaPlaceable.placeRelative(0, 0)
                chip.placeRelative(0, metaPlaceable.height + lineGap)
            }
        }
    }
}
