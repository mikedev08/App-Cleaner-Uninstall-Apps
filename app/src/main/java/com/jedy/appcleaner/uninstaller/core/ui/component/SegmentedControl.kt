package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/** One option of a [SegmentedControl]. [locked] = Pro-only for this user: a small lock, no pill. */
@Immutable
data class SegmentOption(val label: String, val locked: Boolean = false)

/**
 * The one segmented control (design review §5): Apps filters, Settings choices, 30 / 60 / 90 days,
 * Total / Cache. It enforces:
 * - **equal-width segments**, whatever the labels say;
 * - a **sliding green thumb** that is **always on a segment**. [selectedIndex] is clamped into
 *   range, so the control is never shown empty; a free user on a Pro-only control still sees the
 *   current (free) value selected, and the Pro values carry the lock;
 * - **one lock treatment**: a locked segment shows only a small [LockIcon] after its label, never
 *   a PRO pill squeezed inside.
 *
 * Tapping a locked segment still calls [onSelect]; the caller routes it to the paywall and leaves
 * [selectedIndex] unchanged. The thumb's offset mirrors in RTL along with the row.
 */
@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.minTouchTarget,
) {
    if (options.isEmpty()) return
    val colors = AppTheme.colors
    val selected = selectedIndex.coerceIn(0, options.lastIndex)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceMuted)
            .padding(Dimens.space4),
    ) {
        val segment = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            segment * selected,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentThumb",
        )
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.accent),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                val content by animateColorAsState(
                    if (isSelected) colors.onAccent else colors.textSecondary, label = "segmentText",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = Dimens.space8),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (option.locked) {
                        Spacer(Modifier.width(4.dp))
                        LockIcon(tint = content, size = 13.dp)
                    }
                }
            }
        }
    }
}
