package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.color
import com.jedy.appcleaner.uninstaller.core.ui.theme.surface

/*
 * Design system v2 kit. Every screen builds from these so the app reads as one product:
 * big soft cards (no borders, no shadows), pill buttons, bold numbers, severity colour.
 */

/** The one filled CTA per screen. [destructive] paints it Remove Red (uninstall actions only). */
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

/** Tonal secondary action. */
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

/** A soft rounded card. Pass [onClick] to make the whole card tappable. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = AppTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(Dimens.gutter),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Section title with an optional trailing text action ("See all"). */
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

/** A dashboard tile: icon badge, label, big value. [locked] shows the gold lock (premium). */
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
            Text(caption, style = MaterialTheme.typography.bodySmall, color = severity.color, maxLines = 1)
        }
    }
}

/** Rounded-square icon on a severity-tinted surface. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, severity: Severity = Severity.OK, size: Dp = 40.dp) {
    val tint = if (severity == Severity.OK) AppTheme.colors.accentText else severity.color
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(severity.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Gold "PRO" pill with a lock — the only premium marker. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, text: String = "PRO") {
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(AppTheme.colors.premiumGold)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppTheme.colors.onPremiumGold, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.onPremiumGold)
    }
}

/** Small tinted chip: "Space hog", "Not opened in 3 months", "Keyboard". */
@Composable
fun SeverityChip(text: String, severity: Severity, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val fg = if (severity == Severity.OK) AppTheme.colors.accentText else severity.color
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** Thin rounded bar showing [fraction] of the biggest item; colour follows [severity]. */
@Composable
fun SizeBar(fraction: Float, severity: Severity, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0.02f, 1f), tween(700), label = "sizebar")
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(AppTheme.colors.gaugeTrack),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(severity.color))
    }
}

/** Round selection check (replaces the square Material checkbox in lists). */
@Composable
fun RoundCheck(checked: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val colors = AppTheme.colors
    val bg by animateColorAsState(if (checked) colors.accent else Color.Transparent, label = "check")
    Box(
        modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .then(
                    if (checked) Modifier
                    else Modifier.background(colors.surfaceMuted, CircleShape).padding(2.dp).background(colors.background, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(size * 0.66f))
        }
    }
}

/** Big number + caption block used on heroes and results ("3.4 GB" / "can be freed"). */
@Composable
fun BigNumber(value: String, caption: String?, modifier: Modifier = Modifier, color: Color = AppTheme.colors.textPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = color, textAlign = TextAlign.Center)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}
