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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.component.SegmentOption
import com.jedy.appcleaner.uninstaller.core.ui.component.SegmentedControl
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/*
 * Settings-only building blocks on top of the v2 kit. They live in this package rather than in
 * core/ui because only Settings needs them today; promote one to the kit when a second screen does.
 *
 * Rows inside a group are separated by space, never by divider lines: the soft card already says
 * "these belong together", and a line per row is exactly the 2020 look the redesign removes.
 */

/**
 * A titled group: kit [SectionHeader] on the 20dp gutter, then one soft [AppCard] holding the rows.
 * [lead] is an optional card shown between the heading and the rows (the Pro card in
 * Subscription). Spacing follows the scale: 32dp above the heading, 12dp from heading to content.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    topSpacing: Dp = Dimens.sectionGap,
    lead: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(top = topSpacing, bottom = Dimens.headingToContent),
        )
        if (lead != null) {
            lead()
            Spacer(Modifier.height(Dimens.cardGap))
        }
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            content = content,
        )
    }
}

/**
 * IconBadge + label (+ optional status line and subtitle) + value / trailing + chevron. The
 * chevron is auto-mirrored so it points "forward" in Arabic and Hebrew too.
 *
 * The trailing edge is the same on every row: [value] is sized to its text (never given a weight,
 * which used to leave a gap before the chevron), so every chevron sits on the card's end padding.
 * [status] sits under the title (Usage Access "Allowed", a PRO badge) instead of beside it, so
 * the title and description keep the row's full width at 360dp.
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
    status: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = RowPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        IconBadge(icon = icon)
        RowText(title = title, subtitle = subtitle, status = status, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 140.dp),
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
    status: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = RowPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        IconBadge(icon = icon)
        RowText(title = title, subtitle = subtitle, status = status, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked)
    }
}

private val RowPadding = Dimens.space16
private val RowGap = 14.dp

@Composable
private fun RowText(title: String, subtitle: String?, status: (@Composable () -> Unit)?, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        if (status != null) {
            Box(Modifier.padding(top = Dimens.space4)) { status() }
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

/**
 * A labelled kit [SegmentedControl] inside a group card, under its row's icon and title. The
 * control always shows the current value; Pro-only options carry the kit's lock.
 */
@Composable
internal fun SegmentedSettingRow(
    icon: ImageVector,
    title: String,
    segments: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    subtitle: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = RowPadding, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
            IconBadge(icon = icon)
            RowText(title = title, subtitle = subtitle, status = null, modifier = Modifier.weight(1f))
        }
        SegmentedControl(
            options = segments,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.padding(top = Dimens.space12),
        )
    }
}

/**
 * Usage Access state, shown under the row title: a green "Allowed" tick, or a neutral
 * "Not allowed" (the whole row is the way to allow it, and ends in a chevron).
 */
@Composable
internal fun UsageAccessStatus(granted: Boolean) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (granted) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Dimens.space4))
        Text(
            text = stringResource(
                if (granted) R.string.settings_usage_access_granted else R.string.settings_usage_access_denied,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) colors.accentText else colors.textSecondary,
        )
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
