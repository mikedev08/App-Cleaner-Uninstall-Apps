package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.color
import com.jedy.appcleaner.uninstaller.core.ui.theme.contentColor
import com.jedy.appcleaner.uninstaller.core.ui.theme.surface

/*
 * Design system v2.1 kit (design review, Sept 2026). Every screen builds from these so the app
 * reads as one product: soft cards with a 1dp border, pill buttons, bold numbers in textPrimary,
 * colour only on indicators. Color.kt lists the colour rules these components enforce.
 */

/**
 * The one filled CTA per screen. [destructive] paints it Remove Red: Uninstall / Remove only,
 * never a positive action like "Free up 1.1 GB" (that stays green).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    val container = when {
        !enabled -> colors.surfaceMuted
        destructive -> colors.removeRed
        else -> colors.accent
    }
    val content = when {
        !enabled -> colors.textMuted
        destructive -> colors.onRemoveRed
        else -> colors.onAccent
    }
    PillSurface(
        onClick = onClick, enabled = enabled, container = container,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tonal secondary action. Stack it [Dimens.stackedButtonGap] below a [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    PillSurface(onClick = onClick, enabled = true, container = colors.surfaceMuted, modifier = modifier.heightIn(min = Dimens.buttonHeight)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun PillSurface(
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A soft rounded card. Pass [onClick] to make the whole card tappable.
 *
 * Design review §3.2.6: a plain `surface` card gets a 1dp `border` outline so it separates from
 * the background in both themes. Tinted cards (accent / warning / premium surfaces) default to no
 * border; pass [border] to override. Place cards at [Dimens.gutter], [Dimens.cardGap] apart.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = AppTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(Dimens.gutter),
    border: BorderStroke? = if (color == AppTheme.colors.surface) BorderStroke(Dimens.hairline, AppTheme.colors.border) else null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Section title with an optional trailing text action ("See all"). Place it at [Dimens.gutter]
 * with [Dimens.headingToContent] below it and [Dimens.sectionGap] above it.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.accentText,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

/**
 * The hero storage ring. The sweep animates in from 0, and its colour follows [severity] —
 * green, amber from 75% full, red from 90% — so a nearly full phone *looks* nearly full.
 * [center] draws inside the ring (typically the big percentage and a caption).
 */
@Composable
fun StorageGauge(
    usedFraction: Float,
    severity: Severity,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 20.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(usedFraction) {
        sweep.animateTo(usedFraction.coerceIn(0f, 1f), tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    val arcColor by animateColorAsState(severity.color, label = "gauge")
    val track = AppTheme.colors.gaugeTrack
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (sweep.value > 0f) {
                drawArc(arcColor, startAngle = 135f, sweepAngle = 270f * sweep.value, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/**
 * A dashboard tile: icon badge, label, big value in textPrimary. [locked] shows the quiet
 * [ProBadge]. [caption] is neutral text: severity never colours text (design review §3.3).
 */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    severity: Severity = Severity.OK,
    caption: String? = null,
    locked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, severity = severity)
            Spacer(Modifier.weight(1f))
            if (locked) ProBadge()
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.textPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary, maxLines = 2)
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, maxLines = 1)
        }
    }
}

/** Rounded-square icon on a severity-tinted surface, tinted with [Severity.contentColor]. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, severity: Severity = Severity.OK, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(severity.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = severity.contentColor, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The one premium marker (design review §5). Quiet by default: `premiumGoldText` on
 * `premiumGoldSurface`, text only, no lock glyph. Show it once per locked row, at the trailing
 * edge or under the title, never squeezed into a title line or a segment label (segments use
 * [LockIcon]).
 *
 * [strong] = the full gold fill, for the **single** upsell spot (the Settings Pro card) and
 * nowhere else, so the green primary action stays the loudest thing on screen.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier, text: String = "PRO", strong: Boolean = false) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (strong) colors.onPremiumGold else colors.premiumGoldText,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(if (strong) colors.premiumGold else colors.premiumGoldSurface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The small lock for a Pro-only option *inside* a control: a locked segment, a sort-menu entry,
 * a small button. Neutral [tint] by default (gold belongs to [ProBadge]); pass the colour of the
 * text beside it. Rows use [ProBadge] instead, so each locked thing shows exactly one marker.
 */
@Composable
fun LockIcon(
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textSecondary,
    size: Dp = 14.dp,
    contentDescription: String? = stringResource(R.string.cd_pro_feature),
) {
    Icon(Icons.Outlined.Lock, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/**
 * Small tinted chip ("Not opened in 3 months", "Reinstalled", "Large"). Background is
 * [Severity.surface]; text and icon are [Severity.contentColor], never amber or red text.
 *
 * It measures at its intrinsic width on one line (`softWrap = false`) so its unit is never cut
 * off: [AppRow] gives the chip its full width first and shrinks the subtitle instead. Use
 * [Severity.WARNING] for a "Large" chip (it matches the Large bar), never [Severity.DANGER],
 * which is for destructive states. At most one chip per row fits a 360dp phone.
 */
@Composable
fun SeverityChip(text: String, severity: Severity, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val fg = severity.contentColor
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(severity.surface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Thin rounded bar showing [fraction] of the biggest item in the list. Two colours only (design
 * review §3.3): `accent` normally, `sizeBarLarge` when the app is [large] (`LargeApps.isLarge`).
 * The bar carries the meaning; the size text beside it stays textPrimary. It fills its width, so
 * give it a fixed-width slot (as [AppRow] does) to line the right ends up down a list.
 */
@Composable
fun SizeBar(fraction: Float, modifier: Modifier = Modifier, large: Boolean = false, height: Dp = 6.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0.02f, 1f), tween(700), label = "sizebar")
    val fill = if (large) AppTheme.colors.sizeBarLarge else AppTheme.colors.accent
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(AppTheme.colors.gaugeTrack),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(fill))
    }
}

/**
 * Round selection check (replaces the square Material checkbox in lists) in a 48dp touch target.
 * Unchecked is a 2dp `textMuted` ring, so it shows on a card (design review §5).
 */
@Composable
fun RoundCheck(checked: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val colors = AppTheme.colors
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, label = "check")
    val ring by animateColorAsState(if (checked) colors.accent else colors.textMuted, label = "checkRing")
    Box(
        modifier
            .size(maxOf(size + 16.dp, Dimens.minTouchTarget))
            .clip(CircleShape)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(size * 0.66f))
        }
    }
}

/**
 * Big number + caption block used on heroes and results ("3.4 GB" / "can be freed"). Good news
 * passes `color = AppTheme.colors.positive`; never a severity colour.
 */
@Composable
fun BigNumber(value: String, caption: String?, modifier: Modifier = Modifier, color: Color = AppTheme.colors.textPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = color, textAlign = TextAlign.Center)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}
