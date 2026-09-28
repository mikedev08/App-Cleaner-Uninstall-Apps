package com.jedy.appcleaner.uninstaller.feature.usageaccess

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Screen 11 hero, drawn in Compose so it follows the theme tokens in light and dark: a phone
 * with a grid of apps, the forgotten ones faded, one carrying a clock — "we look at when apps
 * were last opened", said without words.
 */
@Composable
internal fun UsageAccessIllustration(modifier: Modifier = Modifier) {
    val teal = AppTheme.colors.teal
    val backdrop = AppTheme.colors.tealSurface
    val screen = AppTheme.colors.background
    val faded = AppTheme.colors.textSecondary.copy(alpha = 0.22f)
    val description = stringResource(R.string.usage_illustration_description)
    Canvas(
        modifier = modifier
            .size(width = 200.dp, height = 168.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        drawCircle(backdrop, radius = size.minDimension / 2f, center = center)

        val phoneHeight = size.height * 0.9f
        val phoneWidth = phoneHeight * 0.62f
        val left = center.x - phoneWidth / 2f
        val top = center.y - phoneHeight / 2f
        val corner = CornerRadius(14.dp.toPx())
        drawRoundRect(screen, topLeft = Offset(left, top), size = Size(phoneWidth, phoneHeight), cornerRadius = corner)
        drawRoundRect(
            teal,
            topLeft = Offset(left, top),
            size = Size(phoneWidth, phoneHeight),
            cornerRadius = corner,
            style = Stroke(width = 3.dp.toPx()),
        )
        // Earpiece.
        drawRoundRect(
            teal.copy(alpha = 0.5f),
            topLeft = Offset(center.x - phoneWidth * 0.12f, top + phoneHeight * 0.05f),
            size = Size(phoneWidth * 0.24f, 3.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )

        val pad = phoneWidth * 0.14f
        val gap = phoneWidth * 0.08f
        val tile = (phoneWidth - 2 * pad - 2 * gap) / 3f
        val gridTop = top + phoneHeight * 0.16f
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                val index = row * 3 + column
                val origin = Offset(left + pad + column * (tile + gap), gridTop + row * (tile + gap))
                val color = when (index) {
                    in FADED_TILES -> faded
                    else -> teal.copy(alpha = if (index % 2 == 0) 1f else 0.65f)
                }
                drawRoundRect(color, topLeft = origin, size = Size(tile, tile), cornerRadius = CornerRadius(tile * 0.28f))
                if (index == CLOCK_TILE) {
                    val radius = tile * 0.3f
                    val clockCenter = Offset(origin.x + tile - radius * 0.4f, origin.y + radius * 0.4f)
                    drawCircle(screen, radius = radius, center = clockCenter)
                    drawCircle(teal, radius = radius, center = clockCenter, style = Stroke(width = 1.5.dp.toPx()))
                    val hand = 1.5.dp.toPx()
                    drawLine(teal, clockCenter, clockCenter + Offset(0f, -radius * 0.6f), strokeWidth = hand, cap = StrokeCap.Round)
                    drawLine(teal, clockCenter, clockCenter + Offset(radius * 0.45f, 0f), strokeWidth = hand, cap = StrokeCap.Round)
                }
            }
        }
        // A dock line under the grid.
        drawRoundRect(
            faded,
            topLeft = Offset(left + pad, top + phoneHeight * 0.84f),
            size = Size(phoneWidth - 2 * pad, 4.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

/**
 * PRD §4 Screen 11: "a small animated hint showing the toggle to flip". A replica of the
 * system row — our icon, name, and a switch that a pointer taps on, in a slow loop — so the
 * user recognises what to look for on a Settings page we don't control.
 */
@Composable
internal fun ToggleHint(modifier: Modifier = Modifier) {
    var switchedOn by remember { mutableStateOf(false) }
    var pressing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            switchedOn = false
            delay(IDLE_MILLIS)
            pressing = true
            delay(PRESS_MILLIS)
            pressing = false
            switchedOn = true
            delay(ON_MILLIS)
        }
    }
    val pointerAlpha by animateFloatAsState(if (switchedOn) 0f else 1f, tween(250), label = "pointerAlpha")
    val pointerScale by animateFloatAsState(if (pressing) 0.8f else 1f, tween(150), label = "pointerScale")
    val description = stringResource(R.string.usage_hint_description)
    val packageName = LocalContext.current.packageName

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.usage_hint_caption),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
            shape = RoundedCornerShape(Dimens.cardRadius),
            color = AppTheme.colors.surface,
            border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
        ) {
            Row(
                modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutterLarge, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName, size = 36.dp)
                Spacer(Modifier.width(Dimens.gutterSmall))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.usage_hint_toggle),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }
                Box {
                    Switch(
                        checked = switchedOn,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppTheme.colors.onTeal,
                            checkedTrackColor = AppTheme.colors.teal,
                            uncheckedThumbColor = AppTheme.colors.textSecondary,
                            uncheckedTrackColor = AppTheme.colors.surfaceMuted,
                            uncheckedBorderColor = AppTheme.colors.textSecondary,
                        ),
                    )
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = AppTheme.colors.teal,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 10.dp, y = 18.dp)
                            .size(28.dp)
                            .scale(pointerScale)
                            .alpha(pointerAlpha),
                    )
                }
            }
        }
    }
}

private val FADED_TILES = setOf(1, 5, 6)
private const val CLOCK_TILE = 5
private const val IDLE_MILLIS = 1_100L
private const val PRESS_MILLIS = 250L
private const val ON_MILLIS = 1_700L
