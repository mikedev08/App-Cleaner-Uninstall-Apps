package com.jedy.appcleaner.uninstaller.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppColors
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * PRD §3 hero illustrations: flat teal-on-white (teal-on-slate in dark), drawn in Compose rather
 * than shipped as assets so they stay crisp at any density and follow the theme tokens.
 *
 * Only slide 2 moves, and only once each time it is shown: the bar shrinking *is* the message
 * ("see what's eating your storage"), whereas PRD §1 keeps motion to state changes.
 */

/** Slide 1: a phone holding a grid of app icons, several of them ticked for removal. */
@Composable
fun AppGridIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Canvas(modifier) {
        val phone = phoneRect(size)
        drawPhone(phone, colors)

        val columns = 3
        val rows = 4
        val inset = phone.width * 0.14f
        val gap = phone.width * 0.09f
        val tile = (phone.width - inset * 2 - gap * (columns - 1)) / columns
        val gridTop = phone.top + phone.height * 0.14f
        // Mirrored in RTL so the "ticked" pattern reads from the start edge like the real list.
        val ticked = setOf(1, 3, 4, 8, 10)
        repeat(rows * columns) { index ->
            val col = index % columns
            val row = index / columns
            val drawCol = if (layoutDirection == LayoutDirection.Rtl) columns - 1 - col else col
            val topLeft = Offset(
                phone.left + inset + drawCol * (tile + gap),
                gridTop + row * (tile + gap),
            )
            drawAppTile(topLeft, tile, tileTint(colors, index), colors.onAccent)
            if (index in ticked) drawTick(topLeft, tile, colors)
        }
    }
}

/**
 * Slide 2: a storage bar that starts nearly full and red, then shrinks to a calm teal as three
 * app icons lift off it.
 *
 * @param active true while this slide is the current page; each activation replays the change.
 */
@Composable
fun StorageBarIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            delay(350)
            progress.animateTo(1f, tween(durationMillis = 1300, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val p = progress.value
        val cardWidth = size.width * 0.86f
        val cardHeight = size.height * 0.42f
        val card = Rect(
            offset = Offset((size.width - cardWidth) / 2f, size.height * 0.46f),
            size = Size(cardWidth, cardHeight),
        )
        val radius = CornerRadius(size.minDimension * 0.06f)
        drawRoundRect(colors.surface, card.topLeft, card.size, radius)
        drawRoundRect(colors.border, card.topLeft, card.size, radius, style = Stroke(size.minDimension * 0.008f))

        val pad = cardWidth * 0.08f
        // Title placeholder line.
        drawLine(
            color = colors.textSecondary.copy(alpha = 0.35f),
            start = Offset(card.left + pad, card.top + cardHeight * 0.24f),
            end = Offset(card.left + pad + cardWidth * 0.36f, card.top + cardHeight * 0.24f),
            strokeWidth = cardHeight * 0.07f,
            cap = StrokeCap.Round,
        )

        // The bar: track + fill. Fill grows from the start edge, so it mirrors in RTL (PRD §6 D).
        val barTop = card.top + cardHeight * 0.44f
        val barHeight = cardHeight * 0.2f
        val barWidth = cardWidth - pad * 2
        val barRadius = CornerRadius(barHeight / 2f)
        drawRoundRect(colors.storageFree, Offset(card.left + pad, barTop), Size(barWidth, barHeight), barRadius)
        val fraction = 0.94f + (0.4f - 0.94f) * p
        val fillWidth = barWidth * fraction
        val fillLeft = if (layoutDirection == LayoutDirection.Rtl) card.left + pad + barWidth - fillWidth else card.left + pad
        drawRoundRect(lerp(colors.removeRed, colors.accent, p), Offset(fillLeft, barTop), Size(fillWidth, barHeight), barRadius)

        // Legend placeholders.
        listOf(0.0f, 0.42f).forEachIndexed { i, x ->
            val dotCenter = Offset(card.left + pad + barWidth * x + barHeight * 0.3f, card.top + cardHeight * 0.8f)
            drawCircle(if (i == 0) lerp(colors.removeRed, colors.accent, p) else colors.storageFree, barHeight * 0.3f, dotCenter)
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.3f),
                start = Offset(dotCenter.x + barHeight * 0.7f, dotCenter.y),
                end = Offset(dotCenter.x + barWidth * 0.26f, dotCenter.y),
                strokeWidth = cardHeight * 0.06f,
                cap = StrokeCap.Round,
            )
        }

        // Three apps lifting away from the bar as it shrinks.
        val tile = size.minDimension * 0.17f
        listOf(0.2f, 0.5f, 0.8f).forEachIndexed { i, x ->
            val stagger = ((p - i * 0.12f) / 0.76f).coerceIn(0f, 1f)
            val baseTop = card.top - tile * 1.25f
            val topLeft = Offset(size.width * x - tile / 2f, baseTop - stagger * tile * 1.1f)
            drawAppTile(topLeft, tile, tileTint(colors, i + 1).copy(alpha = 1f - stagger * 0.85f), colors.onAccent)
        }
    }
}

/** Slide 3: a calendar with dusty, faded app icons nobody has opened in months. */
@Composable
fun ForgottenAppsIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Canvas(modifier) {
        val calWidth = size.width * 0.72f
        val calHeight = size.height * 0.8f
        val cal = Rect(Offset((size.width - calWidth) / 2f, size.height * 0.12f), Size(calWidth, calHeight))
        val radius = CornerRadius(size.minDimension * 0.06f)
        val stroke = size.minDimension * 0.012f

        drawRoundRect(colors.surface, cal.topLeft, cal.size, radius)
        // Header band.
        val headerHeight = calHeight * 0.2f
        val header = Path().apply {
            addRoundRect(
                RoundRect(
                    left = cal.left, top = cal.top, right = cal.right, bottom = cal.top + headerHeight,
                    topLeftCornerRadius = radius, topRightCornerRadius = radius,
                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                )
            )
        }
        drawPath(header, colors.accent)
        drawRoundRect(colors.accent, cal.topLeft, cal.size, radius, style = Stroke(stroke))
        // Binder rings.
        listOf(0.28f, 0.72f).forEach { x ->
            drawLine(
                color = colors.textPrimary.copy(alpha = 0.55f),
                start = Offset(cal.left + calWidth * x, cal.top - calHeight * 0.05f),
                end = Offset(cal.left + calWidth * x, cal.top + headerHeight * 0.35f),
                strokeWidth = stroke * 1.6f,
                cap = StrokeCap.Round,
            )
        }

        // Day grid.
        val cols = 7
        val rows = 5
        val pad = calWidth * 0.07f
        val cellGap = calWidth * 0.025f
        val cell = (calWidth - pad * 2 - cellGap * (cols - 1)) / cols
        val gridTop = cal.top + headerHeight + pad * 0.8f
        repeat(cols * rows) { i ->
            val c = i % cols
            val r = i / cols
            drawRoundRect(
                color = colors.border,
                topLeft = Offset(cal.left + pad + c * (cell + cellGap), gridTop + r * (cell + cellGap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.25f),
            )
        }

        // Faded app icons scattered over the month, with a little dust on each.
        val tile = cell * 2.1f
        val spots = listOf(Offset(0.12f, 0.08f), Offset(0.56f, 0.3f), Offset(0.2f, 0.62f), Offset(0.66f, 0.72f))
        val gridHeight = rows * cell + (rows - 1) * cellGap
        spots.forEachIndexed { i, spot ->
            val topLeft = Offset(
                cal.left + pad + (calWidth - pad * 2 - tile) * spot.x,
                gridTop + (gridHeight - tile) * spot.y,
            )
            drawAppTile(topLeft, tile, colors.storageOther.copy(alpha = 0.55f), colors.background)
            drawCircle(colors.textSecondary.copy(alpha = 0.3f), tile * 0.05f, topLeft + Offset(tile * 0.78f, tile * 0.18f))
            drawCircle(colors.textSecondary.copy(alpha = 0.22f), tile * 0.035f, topLeft + Offset(tile * 0.24f, tile * 0.8f))
            if (i % 2 == 0) {
                drawCircle(colors.textSecondary.copy(alpha = 0.18f), tile * 0.04f, topLeft + Offset(tile * 1.08f, tile * 0.52f))
            }
        }
    }
}

// ---------------------------------------------------------------- drawing helpers

private fun DrawScope.phoneRect(canvas: Size): Rect {
    val height = canvas.height * 0.94f
    val width = (height * 0.56f).coerceAtMost(canvas.width * 0.7f)
    return Rect(Offset((canvas.width - width) / 2f, (canvas.height - height) / 2f), Size(width, height))
}

private fun DrawScope.drawPhone(phone: Rect, colors: AppColors) {
    val radius = CornerRadius(phone.width * 0.14f)
    drawRoundRect(colors.surface, phone.topLeft, phone.size, radius)
    drawRoundRect(colors.accent, phone.topLeft, phone.size, radius, style = Stroke(phone.width * 0.035f))
    // Speaker slot.
    drawLine(
        color = colors.accent.copy(alpha = 0.6f),
        start = Offset(phone.center.x - phone.width * 0.1f, phone.top + phone.height * 0.055f),
        end = Offset(phone.center.x + phone.width * 0.1f, phone.top + phone.height * 0.055f),
        strokeWidth = phone.width * 0.03f,
        cap = StrokeCap.Round,
    )
}

/** A rounded-square "app icon" with a simple glyph dot, so it reads as an icon, not a box. */
private fun DrawScope.drawAppTile(topLeft: Offset, tile: Float, color: Color, glyph: Color) {
    drawRoundRect(color, topLeft, Size(tile, tile), CornerRadius(tile * 0.26f))
    drawCircle(glyph.copy(alpha = 0.55f * color.alpha), tile * 0.16f, topLeft + Offset(tile / 2f, tile / 2f))
}

private fun DrawScope.drawTick(topLeft: Offset, tile: Float, colors: AppColors) {
    // Selection ring around the tile, then the check badge on its top-end corner.
    drawRoundRect(
        color = colors.accent,
        topLeft = topLeft - Offset(tile * 0.08f, tile * 0.08f),
        size = Size(tile * 1.16f, tile * 1.16f),
        cornerRadius = CornerRadius(tile * 0.32f),
        style = Stroke(tile * 0.06f),
    )
    val badgeX = if (layoutDirection == LayoutDirection.Rtl) topLeft.x + tile * 0.04f else topLeft.x + tile * 0.96f
    val center = Offset(badgeX, topLeft.y + tile * 0.04f)
    val r = tile * 0.24f
    drawCircle(colors.background, r * 1.18f, center)
    drawCircle(colors.accent, r, center)
    val check = Path().apply {
        moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
        lineTo(center.x - r * 0.1f, center.y + r * 0.36f)
        lineTo(center.x + r * 0.48f, center.y - r * 0.3f)
    }
    drawPath(check, colors.onAccent, style = Stroke(r * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A few teal intensities plus the neutral, so the grid looks like many different apps. */
private fun tileTint(colors: AppColors, index: Int): Color = when (index % 4) {
    0 -> colors.accent
    1 -> colors.accent.copy(alpha = 0.55f)
    2 -> colors.storageOther
    else -> colors.accent.copy(alpha = 0.3f)
}
