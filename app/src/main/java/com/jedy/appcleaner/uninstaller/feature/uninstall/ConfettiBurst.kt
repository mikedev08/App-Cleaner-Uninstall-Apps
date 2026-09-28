package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val angle: Float,
    val speed: Float,
    val width: Float,
    val height: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean,
)

/**
 * A single short burst (~900 ms) of brand-coloured confetti from the gauge, for the moment the
 * freed space lands. Drawn on one Canvas and animated in the draw phase, so it never recomposes
 * the screen. The caller decides whether it plays: never with reduced motion, never twice.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, particleCount: Int = 70, durationMillis: Int = 900) {
    val colors = AppTheme.colors
    val palette = listOf(colors.accent, colors.accentText, colors.premiumGold, colors.warning, colors.accentSurface)
    val particles = remember(palette) {
        val random = Random(particleCount)
        List(particleCount) {
            Particle(
                // Mostly upward, fanning out: a burst, not a fountain.
                angle = (-Math.PI / 2 + (random.nextFloat() - 0.5f) * Math.PI * 1.5).toFloat(),
                speed = 0.35f + random.nextFloat() * 0.55f,
                width = 6f + random.nextFloat() * 6f,
                height = 10f + random.nextFloat() * 8f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                color = palette[random.nextInt(palette.size)],
                round = random.nextFloat() < 0.3f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.45f)
        val reach = size.minDimension * 0.75f
        // Ease-out travel with a touch of gravity, fading over the last half.
        val travel = 1f - (1f - t) * (1f - t)
        val alpha = if (t < 0.5f) 1f else 1f - (t - 0.5f) * 2f
        particles.forEach { p ->
            val x = origin.x + cos(p.angle) * p.speed * reach * travel
            val y = origin.y + sin(p.angle) * p.speed * reach * travel + t * t * reach * 0.6f
            val center = Offset(x, y)
            rotate(p.spin * t, pivot = center) {
                if (p.round) {
                    drawCircle(p.color, radius = p.width / 2 * density / 2, center = center, alpha = alpha)
                } else {
                    val w = p.width * density / 2
                    val h = p.height * density / 2
                    drawRoundRect(
                        color = p.color,
                        topLeft = Offset(x - w / 2, y - h / 2),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 3),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}
