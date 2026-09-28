package com.jedy.appcleaner.uninstaller.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/*
 * Settings-only building blocks on top of the v2 kit. They live in this package rather than in
 * core/ui because only Settings needs them today; promote one to the kit when a second screen does.
 *
 * Rows inside a group are separated by space, never by divider lines: the soft card already says
 * "these belong together", and a line per row is exactly the 2020 look the redesign removes.
 */

/** A titled group: kit [SectionHeader] above one soft [AppCard] holding the rows. */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = title, modifier = Modifier.padding(start = 4.dp, top = Dimens.gutterLarge, bottom = 10.dp))
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            content = content,
        )
    }
}

/**
 * IconBadge + label (+ optional subtitle) + value / trailing + chevron. The chevron is
 * auto-mirrored so it points "forward" in Arabic and Hebrew too.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    titleBadge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon = icon)
        RowText(title = title, subtitle = subtitle, badge = titleBadge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.6f, fill = false),
            )
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
            )
        }
    }
}

/**
 * A row whose whole surface toggles [AppSwitch] (one big touch target, announced as a switch),
 * rather than a tiny switch the user has to hit exactly.
 */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleBadge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon = icon)
        RowText(title = title, subtitle = subtitle, badge = titleBadge, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked)
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, badge: (@Composable () -> Unit)?, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                badge()
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The v2 switch: a spring-green pill with a white thumb that springs across, a check inside
 * when on. Drawn by hand because the stock Material switch (outlined track, shrinking thumb) is
 * one of the most recognisably "default" controls there is. Purely visual: the parent row owns
 * the toggle semantics and the click, so there is a single focus target.
 * `offset` is layout-direction aware, so the thumb travels right-to-left in RTL on its own.
 */
@Composable
internal fun AppSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val track by animateColorAsState(
        if (checked) colors.accent else colors.textMuted.copy(alpha = 0.38f),
        tween(200),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "switchThumb",
    )
    Box(
        modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val checkAlpha by animateColorAsState(
                if (checked) colors.accentText else Color.Transparent,
                tween(160),
                label = "switchCheck",
            )
            Icon(Icons.Rounded.Check, contentDescription = null, tint = checkAlpha, modifier = Modifier.size(15.dp))
        }
    }
}

/** One option of a [PillSegmentedControl]. [locked] adds a small gold lock (premium only). */
internal data class Segment(val label: String, val locked: Boolean = false)

/**
 * Inline pill segmented control (Theme, size mode, reminder threshold): every option is visible
 * and one tap away, where the old radio dialog hid them behind a second screen. The selection is
 * a green pill that slides between segments. [selectedIndex] = -1 shows none selected (a locked,
 * premium-only control for a free user).
 */
@Composable
internal fun PillSegmentedControl(
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceMuted)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / segments.size
        val indicatorOffset by animateDpAsState(
            segmentWidth * selectedIndex.coerceAtLeast(0),
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentIndicator",
        )
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                val content by animateColorAsState(
                    if (selected) colors.onAccent else colors.textSecondary,
                    tween(200),
                    label = "segmentText",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (segment.locked) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.premiumGold,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A labelled [PillSegmentedControl] inside a group card, indented under its row's icon. */
@Composable
internal fun SegmentedSettingRow(
    icon: ImageVector,
    title: String,
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    subtitle: String? = null,
    titleBadge: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon = icon)
            RowText(title = title, subtitle = subtitle, badge = titleBadge, modifier = Modifier.weight(1f))
        }
        PillSegmentedControl(
            segments = segments,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Usage Access state: a green "Allowed" tick, or a small green "Allow" pill that asks for it. */
@Composable
internal fun UsageAccessStatus(granted: Boolean) {
    val colors = AppTheme.colors
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.settings_usage_access_granted),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentText,
            )
        }
    } else {
        SmallPill(text = stringResource(R.string.settings_usage_access_allow), onClick = null)
    }
}

/** Compact filled pill for an inline fix ("Allow", "Allow notifications"). */
@Composable
internal fun SmallPill(text: String, onClick: (() -> Unit)?) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onAccent,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.accent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * An inline problem with its fix (notifications off, reminders missing Usage Access), inset as a
 * muted tile inside the group. Deliberately neutral, not amber: severity colour is reserved for
 * the user's real storage numbers.
 */
@Composable
internal fun NoticeTile(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(Dimens.controlRadius))
            .background(colors.surfaceMuted)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        SmallPill(text = action, onClick = onAction)
    }
}
