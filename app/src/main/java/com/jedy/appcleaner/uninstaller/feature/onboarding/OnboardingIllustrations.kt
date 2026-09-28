package com.jedy.appcleaner.uninstaller.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.StorageGauge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppColors
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

/*
 * PRD §3 hero illustrations, v2 (Sept 2026). Drawn in Compose rather than shipped as assets so
 * they stay crisp at any density and follow the light/dark tokens.
 *
 * The 2026 look: bold, chunky shapes; depth from *layered tints* (a tinted plate offset behind
 * each object, soft discs behind the scene) instead of drop shadows, which the v2 kit bans; and
 * items that spring in one after another. The pager only composes visible pages, so each slide's
 * entrance plays whenever it is swiped into view, and plays *during* the swipe rather than after.
 * Anything positional mirrors in RTL so the scenes read from the start edge like the real UI.
 */

/** A spring-driven 0→1 value per item, started one after another when the slide appears. */
@Composable
private fun rememberStaggeredEntrance(
    count: Int,
    startDelayMs: Long = 120,
    staggerMs: Long = 60,
): List<Animatable<Float, AnimationVector1D>> {
    val items = remember { List(count) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        items.forEachIndexed { index, item ->
            launch {
                delay(startDelayMs + index * staggerMs)
                item.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    return items
}

// ---------------------------------------------------------------- slide 1

private const val GRID = 3
private val selectedTiles = listOf(0, 4, 5, 7)

/** Slide 1: a board of app tiles pops in, then four of them get ticked for removal. */
@Composable
fun AppGridIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val tiles = rememberStaggeredEntrance(GRID * GRID, startDelayMs = 100, staggerMs = 55)
    val checks = rememberStaggeredEntrance(selectedTiles.size, startDelayMs = 850, staggerMs = 160)
    Canvas(modifier) {
        val d = size.minDimension
        drawBackdrop(colors, d)

        val boardSize = d * 0.72f
        val board = Rect(Offset(center.x - boardSize / 2f, center.y - boardSize / 2f), Size(boardSize, boardSize))
        drawLayeredCard(board, CornerRadius(boardSize * 0.14f), colors, d)

        val pad = boardSize * 0.11f
        val gap = boardSize * 0.07f
        val tile = (boardSize - pad * 2 - gap * (GRID - 1)) / GRID
        repeat(GRID * GRID) { index ->
            val col = index % GRID
            val row = index / GRID
            val drawCol = if (layoutDirection == LayoutDirection.Rtl) GRID - 1 - col else col
            val topLeft = Offset(board.left + pad + drawCol * (tile + gap), board.top + pad + row * (tile + gap))
            val grow = tiles[index].value
            val checkIndex = selectedTiles.indexOf(index)
            val tick = if (checkIndex >= 0) checks[checkIndex].value.coerceIn(0f, 1f) else 0f
            withTransform({ scale(grow, grow, pivot = topLeft + Offset(tile / 2f, tile / 2f)) }) {
                drawAppTile(topLeft, tile, tileTint(colors, index).copy(alpha = 1f - 0.4f * tick), glyph = index % 3)
            }
            if (checkIndex >= 0 && checks[checkIndex].value > 0f) drawCheckBadge(topLeft, tile, checks[checkIndex].value, colors)
        }
    }
}

/** Greens and a neutral, so the board reads as many different apps without borrowing severity. */
private fun tileTint(colors: AppColors, index: Int): Color = when (index % 5) {
    0 -> colors.accent
    1 -> colors.storageOther
    2 -> colors.accentText
    3 -> lerp(colors.accent, colors.storageOther, 0.55f)
    else -> colors.accent.copy(alpha = 0.6f)
}

private fun DrawScope.drawCheckBadge(tileTopLeft: Offset, tile: Float, progress: Float, colors: AppColors) {
    val endX = if (layoutDirection == LayoutDirection.Rtl) tileTopLeft.x + tile * 0.06f else tileTopLeft.x + tile * 0.94f
    val center = Offset(endX, tileTopLeft.y + tile * 0.06f)
    val r = tile * 0.27f
    withTransform({ scale(progress, progress, pivot = center) }) {
        drawCircle(colors.surfaceElevated, r * 1.22f, center)
        drawCircle(colors.accent, r, center)
        val check = Path().apply {
            moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
            lineTo(center.x - r * 0.1f, center.y + r * 0.36f)
            lineTo(center.x + r * 0.48f, center.y - r * 0.3f)
        }
        drawPath(check, colors.onAccent, style = Stroke(r * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- slide 2

/**
 * Slide 2: the real [StorageGauge] sweeping up to 80%, which [SeverityRules.storage] rates amber:
 * the same ring and colour the user meets on Home, so onboarding foreshadows the app's core
 * moment instead of drawing a lookalike. The percentage counts up in step with the sweep.
 */
@Composable
fun StorageGaugeIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val fraction = 0.8f
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(220)
        target = fraction
    }
    // Same duration and easing as the gauge's own sweep.
    val shown by animateFloatAsState(target, tween(durationMillis = 1100, easing = FastOutSlowInEasing), label = "gaugeCount")
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val floaters = rememberStaggeredEntrance(3, startDelayMs = 500, staggerMs = 140)
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobValue",
    )

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = min(maxWidth, maxHeight)
        val gaugeSize = d * 0.74f
        val stroke = d * 0.07f
        Canvas(Modifier.matchParentSize()) {
            val px = size.minDimension
            drawCircle(colors.warningSurface, px * 0.5f, center)
            drawCircle(colors.warningSurface.copy(alpha = 0.6f), px * 0.62f, center, style = Stroke(px * 0.02f))
            // Inner plate: the ring sits on a raised disc, depth by tint.
            drawCircle(colors.surfaceElevated, gaugeSize.toPx() / 2f - stroke.toPx() * 1.1f, center)
        }
        StorageGauge(
            usedFraction = target,
            severity = SeverityRules.storage(fraction),
            size = gaugeSize,
            strokeWidth = stroke,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    text = percent.format(shown.toDouble()),
                    // The illustration shrinks on short screens; keep the number inside the ring.
                    style = if (gaugeSize < 180.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.onboarding_gauge_caption),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                )
            }
        }
        // App tiles drifting around the ring: the "apps" that fill the storage.
        Canvas(Modifier.matchParentSize()) {
            val px = size.minDimension
            val tile = px * 0.14f
            val spots = listOf(Offset(-0.4f, -0.34f), Offset(0.42f, -0.06f), Offset(-0.3f, 0.4f))
            val tints = listOf(colors.accent, colors.warning, colors.storageOther)
            spots.forEachIndexed { i, spot ->
                val grow = floaters[i].value
                val dir = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                val drift = bob * px * 0.018f * (if (i % 2 == 0) 1f else -1f)
                val c = center + Offset(spot.x * px * dir, spot.y * px + drift)
                val topLeft = c - Offset(tile / 2f, tile / 2f)
                withTransform({
                    scale(grow, grow, pivot = c)
                    rotate(degrees = (i - 1) * 9f * dir, pivot = c)
                }) {
                    drawRoundRect(tints[i].copy(alpha = 0.3f), topLeft + Offset(tile * 0.12f * dir, tile * 0.14f), Size(tile, tile), CornerRadius(tile * 0.3f))
                    drawAppTile(topLeft, tile, tints[i], glyph = i)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- slide 3

/** Slide 3: a month calendar where faded, dusty app icons settle onto the days nobody opened them. */
@Composable
fun ForgottenAppsIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val spots = remember { listOf(Pair(1, 0), Pair(4, 1), Pair(2, 2), Pair(5, 3)) } // (column, row)
    val icons = rememberStaggeredEntrance(spots.size, startDelayMs = 350, staggerMs = 170)
    val page = rememberStaggeredEntrance(1, startDelayMs = 0)
    Canvas(modifier) {
        val d = size.minDimension
        val rtl = layoutDirection == LayoutDirection.Rtl
        drawBackdrop(colors, d)

        val calW = d * 0.8f
        val calH = d * 0.74f
        val cal = Rect(Offset(center.x - calW / 2f, center.y - calH / 2f + d * 0.02f), Size(calW, calH))
        val radius = CornerRadius(calW * 0.1f)

        // Last month's page, tilted behind: two layered tints instead of a shadow.
        val tilt = page[0].value.coerceIn(0f, 1.2f) * (if (rtl) 7f else -7f)
        rotate(tilt, pivot = cal.center) {
            drawRoundRect(colors.accent.copy(alpha = 0.28f), cal.topLeft, cal.size, radius)
        }
        drawRoundRect(colors.surfaceElevated, cal.topLeft, cal.size, radius)

        // Header band with a "month" line and the binder rings.
        val headerH = calH * 0.22f
        val header = Path().apply {
            addRoundRect(
                RoundRect(
                    left = cal.left, top = cal.top, right = cal.right, bottom = cal.top + headerH,
                    topLeftCornerRadius = radius, topRightCornerRadius = radius,
                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                )
            )
        }
        drawPath(header, colors.accent)
        val lineStart = if (rtl) cal.right - calW * 0.12f else cal.left + calW * 0.12f
        val lineEnd = if (rtl) lineStart - calW * 0.34f else lineStart + calW * 0.34f
        drawLine(Color.White.copy(alpha = 0.9f), Offset(lineStart, cal.top + headerH * 0.56f), Offset(lineEnd, cal.top + headerH * 0.56f), headerH * 0.16f, StrokeCap.Round)
        listOf(0.3f, 0.7f).forEach { x ->
            val ringX = cal.left + calW * x
            drawRoundRect(
                colors.surfaceElevated,
                Offset(ringX - calW * 0.03f, cal.top - headerH * 0.28f),
                Size(calW * 0.06f, headerH * 0.56f),
                CornerRadius(calW * 0.03f),
            )
        }

        // Day grid: 7 × 4, today picked out in green.
        val cols = 7
        val rows = 4
        val pad = calW * 0.08f
        val gap = calW * 0.028f
        val cell = (calW - pad * 2 - gap * (cols - 1)) / cols
        val gridTop = cal.top + headerH + pad * 0.8f
        fun cellTopLeft(col: Int, row: Int): Offset {
            val c = if (rtl) cols - 1 - col else col
            return Offset(cal.left + pad + c * (cell + gap), gridTop + row * (cell + gap))
        }
        repeat(cols * rows) { i ->
            val isToday = i == 3 * cols + 6
            drawRoundRect(
                color = if (isToday) colors.accent else colors.surfaceMuted,
                topLeft = cellTopLeft(i % cols, i / cols),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.32f),
            )
        }

        // The forgotten apps: bigger than a day, desaturated, a little dust on each.
        val tile = cell * 1.55f
        spots.forEachIndexed { i, (col, row) ->
            val p = icons[i].value
            val a = p.coerceIn(0f, 1f)
            val cellOrigin = cellTopLeft(col, row)
            val topLeft = cellOrigin + Offset((cell - tile) / 2f, (cell - tile) / 2f - (1f - p) * tile * 0.5f)
            drawAppTile(topLeft, tile, colors.storageOther.copy(alpha = 0.55f * a), glyph = i)
            drawCircle(colors.textMuted.copy(alpha = 0.55f * a), tile * 0.06f, topLeft + Offset(tile * 0.8f, tile * 0.16f))
            drawCircle(colors.textMuted.copy(alpha = 0.4f * a), tile * 0.04f, topLeft + Offset(tile * 0.2f, tile * 0.84f))
        }
    }
}

// ---------------------------------------------------------------- shared drawing

/** Soft layered discs behind every scene, so all three slides share one visual language. */
private fun DrawScope.drawBackdrop(colors: AppColors, d: Float) {
    val dir = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    drawCircle(colors.accentSurface, d * 0.5f, center)
    drawCircle(colors.accent.copy(alpha = 0.22f), d * 0.14f, center + Offset(d * 0.36f * dir, -d * 0.34f))
    drawCircle(colors.accent.copy(alpha = 0.4f), d * 0.06f, center + Offset(-d * 0.42f * dir, d * 0.3f))
}

/** A card with a green-tinted plate offset behind it: depth by tint, not shadow. */
private fun DrawScope.drawLayeredCard(card: Rect, radius: CornerRadius, colors: AppColors, d: Float) {
    val dir = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    drawRoundRect(colors.accent.copy(alpha = 0.35f), card.topLeft + Offset(d * 0.035f * dir, d * 0.045f), card.size, radius)
    drawRoundRect(colors.surfaceElevated, card.topLeft, card.size, radius)
}

/** A chunky rounded-square "app icon" with one of three simple white glyphs. */
private fun DrawScope.drawAppTile(topLeft: Offset, tile: Float, color: Color, glyph: Int, glyphAlpha: Float = 0.92f) {
    drawRoundRect(color, topLeft, Size(tile, tile), CornerRadius(tile * 0.3f))
    val c = topLeft + Offset(tile / 2f, tile / 2f)
    // The glyph fades with its tile, so a dimmed (ticked or forgotten) app dims as one piece.
    val white = Color.White.copy(alpha = glyphAlpha * color.alpha)
    when (glyph % 3) {
        0 -> drawCircle(white, tile * 0.17f, c)
        1 -> drawRoundRect(white, c - Offset(tile * 0.16f, tile * 0.16f), Size(tile * 0.32f, tile * 0.32f), CornerRadius(tile * 0.08f))
        else -> drawCircle(white, tile * 0.15f, c, style = Stroke(tile * 0.08f))
    }
}
