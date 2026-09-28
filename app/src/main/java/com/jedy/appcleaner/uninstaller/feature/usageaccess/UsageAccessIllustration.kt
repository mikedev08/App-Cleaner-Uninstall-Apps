package com.jedy.appcleaner.uninstaller.feature.usageaccess

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Screen 11 hero, drawn in Compose so it follows the theme tokens in light and dark: a phone of
 * green app tiles on a soft halo, the forgotten apps faded, with a clock ("we look at when apps
 * were last opened") and a lock ("and it stays here") floating beside it. The badges bob gently
 * so the screen feels alive without a single word of copy; they sit by Alignment, so they swap
 * sides in RTL.
 */
@Composable
internal fun UsageAccessHero(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val description = stringResource(R.string.usage_illustration_description)
    val bob by rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroBob",
    )
    Box(
        modifier
            .size(width = 280.dp, height = 232.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 280.dp, height = 232.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(colors.accent.copy(alpha = 0.08f), radius = radius, center = center)
            drawCircle(colors.accentSurface, radius = radius * 0.78f, center = center)

            val phoneHeight = size.height * 0.82f
            val phoneWidth = phoneHeight * 0.56f
            val left = center.x - phoneWidth / 2f
            val top = center.y - phoneHeight / 2f
            val corner = CornerRadius(22.dp.toPx())
            drawRoundRect(colors.surfaceElevated, Offset(left, top), Size(phoneWidth, phoneHeight), corner)
            drawRoundRect(colors.accent, Offset(left, top), Size(phoneWidth, phoneHeight), corner, style = Stroke(4.dp.toPx()))
            // Camera pill.
            drawRoundRect(
                colors.accent.copy(alpha = 0.5f),
                topLeft = Offset(center.x - phoneWidth * 0.14f, top + phoneHeight * 0.05f),
                size = Size(phoneWidth * 0.28f, 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )

            val pad = phoneWidth * 0.15f
            val gap = phoneWidth * 0.08f
            val tile = (phoneWidth - 2 * pad - 2 * gap) / 3f
            val gridTop = top + phoneHeight * 0.15f
            for (row in 0 until GRID_ROWS) {
                for (column in 0 until 3) {
                    val index = row * 3 + column
                    val color = when (index) {
                        in FADED_TILES -> colors.textMuted.copy(alpha = 0.28f)
                        else -> colors.accent.copy(alpha = TILE_ALPHAS[index % TILE_ALPHAS.size])
                    }
                    drawRoundRect(
                        color,
                        topLeft = Offset(left + pad + column * (tile + gap), gridTop + row * (tile + gap)),
                        size = Size(tile, tile),
                        cornerRadius = CornerRadius(tile * 0.3f),
                    )
                }
            }
            // Home indicator.
            drawRoundRect(
                colors.textMuted.copy(alpha = 0.4f),
                topLeft = Offset(center.x - phoneWidth * 0.18f, top + phoneHeight * 0.92f),
                size = Size(phoneWidth * 0.36f, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        HeroBadge(
            icon = Icons.Rounded.Schedule,
            container = colors.textPrimary,
            tint = colors.background,
            size = 52.dp,
            modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(34.dp.roundToPx(), (30 + bob * BOB_DP).dp.roundToPx()) },
        )
        HeroBadge(
            icon = Icons.Rounded.Lock,
            container = colors.accent,
            tint = colors.onAccent,
            size = 60.dp,
            modifier = Modifier.align(Alignment.BottomEnd).offset { IntOffset((-30).dp.roundToPx(), (-26 - bob * BOB_DP).dp.roundToPx()) },
        )
    }
}

@Composable
private fun HeroBadge(icon: ImageVector, container: Color, tint: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(AppTheme.colors.background)
            .padding(4.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * PRD §4 Screen 11: "a small animated hint showing the toggle to flip". A replica of the system
 * row — our icon, name, and a switch that a pointer taps on, in a slow loop — so the user
 * recognises what to look for on a Settings page we don't control. The switch is drawn by hand
 * (not Material's) so it matches the v2 pill language; its thumb offset mirrors in RTL exactly
 * like the system switch does.
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
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        AppCard(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, size = 40.dp)
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
                Spacer(Modifier.width(Dimens.gutterSmall))
                Box {
                    HintSwitch(checked = switchedOn)
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 20.dp)
                            .size(30.dp)
                            .scale(pointerScale)
                            .alpha(pointerAlpha),
                    )
                }
            }
        }
    }
}

@Composable
private fun HintSwitch(checked: Boolean) {
    val colors = AppTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.textMuted.copy(alpha = 0.45f), label = "hintTrack")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp, tween(220), label = "hintThumb")
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private val FADED_TILES = setOf(1, 5, 6, 10)
private val TILE_ALPHAS = floatArrayOf(1f, 0.7f, 0.85f, 0.55f)
private const val GRID_ROWS = 4
private const val BOB_MILLIS = 2_400
private const val BOB_DP = 4f
private const val IDLE_MILLIS = 1_100L
private const val PRESS_MILLIS = 250L
private const val ON_MILLIS = 1_700L
