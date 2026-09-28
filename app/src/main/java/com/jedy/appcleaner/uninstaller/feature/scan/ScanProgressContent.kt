package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.feature.home.rememberPercentFormat

/**
 * The scanning phase: a large ring whose green arc fills as real steps complete, a soft sweep
 * rotating over it so the screen visibly works, the percentage in the middle, and the step list
 * turning pending → spinning → green check.
 */
@Composable
internal fun ScanProgressContent(phase: ScanPhase.Scanning, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.scan_scanning_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        ScanRing(fraction = phase.fraction)
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanStage.entries.forEach { stage ->
                StepRow(label = stringResource(stage.labelRes()), status = phase.steps[stage] ?: StepStatus.PENDING)
            }
        }
    }
}

@Composable
private fun ScanRing(fraction: Float, size: Dp = 240.dp, stroke: Dp = 18.dp) {
    val colors = AppTheme.colors
    val percent = rememberPercentFormat()
    val progress by animateFloatAsState(fraction, tween(durationMillis = 700, easing = FastOutSlowInEasing), label = "scanRing")
    val spin = rememberInfiniteTransition(label = "scanSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "scanAngle")
    val pulse by spin.animateFloat(0.92f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scanPulse")
    val track = colors.gaugeTrack
    val accent = colors.accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Soft halo that breathes behind the ring.
        Box(
            Modifier
                .size(size * pulse)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colors.accentSurface, Color.Transparent))),
        )
        Canvas(Modifier.size(size)) {
            val px = stroke.toPx()
            val inset = px / 2
            val arc = Size(this.size.width - px, this.size.height - px)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(px))
            drawArc(
                accent, -90f, 360f * progress, useCenter = false, topLeft = Offset(inset, inset), size = arc,
                style = Stroke(px, cap = StrokeCap.Round),
            )
        }
        // The rotating sweep: a short gradient comet travelling round the track.
        Canvas(Modifier.size(size).rotate(angle)) {
            val px = stroke.toPx()
            val inset = px / 2
            val arc = Size(this.size.width - px, this.size.height - px)
            drawArc(
                // Sweep gradients start at 3 o'clock, like drawArc: fade in over the arc's 108°.
                brush = Brush.sweepGradient(0f to Color.Transparent, 0.3f to accent.copy(alpha = 0.6f)),
                startAngle = 0f, sweepAngle = 108f, useCenter = false, topLeft = Offset(inset, inset), size = arc,
                style = Stroke(px, cap = StrokeCap.Butt),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(percent(progress), style = MaterialTheme.typography.displayLarge, color = colors.textPrimary)
        }
    }
}

@Composable
private fun StepRow(label: String, status: StepStatus) {
    val colors = AppTheme.colors
    val active = status != StepStatus.PENDING
    val container by animateColorAsState(if (status == StepStatus.RUNNING) colors.surface else colors.background, label = "stepBg")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
            label = "stepIcon",
        ) { current ->
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                when (current) {
                    StepStatus.PENDING -> Box(Modifier.size(22.dp).clip(CircleShape).background(colors.surfaceMuted))
                    StepStatus.RUNNING -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.accent,
                        trackColor = colors.gaugeTrack,
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    StepStatus.DONE -> Box(
                        Modifier.size(28.dp).clip(CircleShape).background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp)) }
                    StepStatus.SKIPPED -> Box(
                        Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceMuted),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Remove, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) colors.textPrimary else colors.textMuted,
            )
            if (status == StepStatus.SKIPPED) {
                Text(stringResource(R.string.scan_step_skipped), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@StringRes
private fun ScanStage.labelRes(): Int = when (this) {
    ScanStage.APPS -> R.string.scan_step_apps
    ScanStage.USAGE -> R.string.scan_step_usage
    ScanStage.STORAGE -> R.string.scan_step_storage
}
