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
import androidx.compose.ui.text.style.TextOverflow
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
 * - **Fixed height.** Every row is [Dimens.appRowHeight] whether or not it has chips: chips share
 *   the subtitle line instead of adding one.
 * - **Chips never truncate.** The chip is measured first, at its intrinsic width; the subtitle
 *   ellipsizes into what is left and is dropped if that is too narrow to read. Keep it to one chip.
 * - **Aligned right edges.** The size text is end-aligned on the title line and the bar spans the
 *   text column, whose width is fixed by the fixed-width check column — so size ends and bar ends
 *   line up down the whole list, whatever the size label says.
 * - **Neutral sizes.** [trailingText] is always textPrimary. Only the bar is coloured, by [large].
 * - **One gutter.** [outerPadding] defaults to [AppRowDefaults.ListPadding] (the 20dp page gutter),
 *   so place rows full-width in a list and do **not** add horizontal padding at the call site.
 *
 * @param meta the subtitle ("Installed Mar 2025"). Shrinks before the chip does.
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (trailingText != null) {
                    Spacer(Modifier.width(Dimens.space8))
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
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
 * Subtitle + chip on one line, 20dp tall with or without the chip. The chip is measured first at
 * its intrinsic width; the subtitle gets the rest and is skipped when under 32dp (an "In…" stub
 * reads worse than nothing).
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chips != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { chips() }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val chip = measurables.getOrNull(1)?.measure(loose)
        val gap = if (chip != null && meta.isNotEmpty()) 8.dp.roundToPx() else 0
        val left = constraints.maxWidth - (chip?.width ?: 0) - gap
        val text = if (meta.isNotEmpty() && (chip == null || left >= 32.dp.roundToPx())) {
            measurables[0].measure(Constraints(maxWidth = left.coerceAtLeast(0)))
        } else {
            null
        }
        val height = maxOf(constraints.minHeight, text?.height ?: 0, chip?.height ?: 0)
        layout(constraints.maxWidth, height) {
            var x = 0
            if (text != null) {
                text.placeRelative(0, (height - text.height) / 2)
                x = text.width + gap
            }
            chip?.placeRelative(x, (height - chip.height) / 2)
        }
    }
}
