package com.jedy.appcleaner.uninstaller.feature.uninstall

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.data.uninstall.AppWarning
import com.jedy.appcleaner.uninstaller.data.uninstall.FailureReason
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.NotRemovedItem

/** "2.4 GB", or "about 2.4 GB" when the number is APK-only (PRD Feature 2). */
@Composable
fun sizeText(bytes: Long, isEstimate: Boolean): String {
    val context = LocalContext.current
    val formatted = formatSize(context, bytes)
    return if (isEstimate) stringResource(R.string.uninstall_size_about, formatted) else formatted
}

/**
 * [formatBytes], except that 901–999 MB reads "970 MB" rather than the platform's "0.97 GB"
 * (see [ResultMath.subGigabyteMegabytes]). ICU keeps the unit and digits localized.
 */
fun formatSize(context: Context, bytes: Long): String {
    val mb = ResultMath.subGigabyteMegabytes(bytes) ?: return formatBytes(context, bytes)
    val locale = context.resources.configuration.locales[0]
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).format(Measure(mb, MeasureUnit.MEGABYTE))
}

/** The full sentence: what removing this app will change. Shown under the chip. */
@StringRes
fun AppWarning.textRes(): Int = when (this) {
    AppWarning.DEVICE_ADMIN -> R.string.uninstall_warning_device_admin
    AppWarning.KEYBOARD -> R.string.uninstall_warning_keyboard
    AppWarning.LAUNCHER -> R.string.uninstall_warning_launcher
    AppWarning.SMS -> R.string.uninstall_warning_sms
    AppWarning.DIALER -> R.string.uninstall_warning_dialer
    AppWarning.ALWAYS_RUNNING -> R.string.uninstall_warning_always_running
}

/** The short chip label ("Your keyboard"); a chip is one line, so the sentence lives beside it. */
@StringRes
fun AppWarning.chipRes(): Int = when (this) {
    AppWarning.DEVICE_ADMIN -> R.string.uninstall_chip_device_admin
    AppWarning.KEYBOARD -> R.string.uninstall_chip_keyboard
    AppWarning.LAUNCHER -> R.string.uninstall_chip_launcher
    AppWarning.SMS -> R.string.uninstall_chip_sms
    AppWarning.DIALER -> R.string.uninstall_chip_dialer
    AppWarning.ALWAYS_RUNNING -> R.string.uninstall_chip_always_running
}

/**
 * Warn, don't block (PRD §6 item 9). A device admin is red because Android will refuse the removal
 * until it is deactivated; the rest are amber — they work, but change how the phone behaves.
 */
val AppWarning.severity: Severity
    get() = if (this == AppWarning.DEVICE_ADMIN) Severity.DANGER else Severity.WARNING

/** PRD §6 items 1–3, 6: the reason line under each "not removed" app. */
@Composable
fun NotRemovedItem.reasonText(): String = when (state) {
    ItemState.SKIPPED -> stringResource(R.string.uninstall_reason_cancelled)
    ItemState.WAITING, ItemState.IN_PROGRESS -> stringResource(R.string.uninstall_reason_not_attempted)
    else -> when (reason) {
        FailureReason.BLOCKED -> stringResource(R.string.uninstall_reason_blocked)
        FailureReason.DEVICE_ADMIN -> stringResource(R.string.uninstall_reason_device_admin)
        FailureReason.NOT_REMOVED_AFTER_SUCCESS -> stringResource(R.string.uninstall_reason_not_removed)
        FailureReason.STATUS_CODE_OTHER, null ->
            stringResource(R.string.uninstall_reason_other, item.statusCode ?: 0)
    }
}

/**
 * A compact pill for in-row actions ("Reinstall", "Try again"). The kit's [PrimaryButton] is a
 * 58dp screen CTA; inside a 76dp card row it would crowd the name, so rows get this smaller sibling
 * in the same shape language.
 */
@Composable
fun RowPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val colors = AppTheme.colors
    val container = when {
        !enabled -> colors.surfaceMuted
        filled -> colors.accent
        else -> colors.accentSurface
    }
    val content = when {
        !enabled -> colors.textMuted
        filled -> colors.onAccent
        else -> colors.accentText
    }
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/**
 * The queue item's state, morphing (scale + fade) as it moves waiting → spinner → check, so the
 * list visibly *does* something each time Android's dialog closes.
 */
@Composable
fun AnimatedStateIcon(state: ItemState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (scaleIn(tween(260), initialScale = 0.5f) + fadeIn(tween(200))) togetherWith
                (scaleOut(tween(160), targetScale = 0.5f) + fadeOut(tween(160)))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier.size(28.dp),
        label = "stateIcon",
    ) { current ->
        val iconModifier = Modifier.size(26.dp)
        when (current) {
            ItemState.WAITING ->
                Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = colors.textMuted, modifier = iconModifier)
            ItemState.IN_PROGRESS ->
                CircularProgressIndicator(
                    color = colors.accent, trackColor = colors.gaugeTrack, strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp),
                )
            ItemState.REMOVED ->
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accentText, modifier = iconModifier)
            ItemState.SKIPPED ->
                Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null, tint = colors.textMuted, modifier = iconModifier)
            ItemState.FAILED ->
                Icon(Icons.Rounded.Error, contentDescription = null, tint = colors.danger, modifier = iconModifier)
            ItemState.ALREADY_REMOVED ->
                Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = colors.textMuted, modifier = iconModifier)
        }
    }
}

/**
 * "Remove animations" in Accessibility sets the animator scale to 0. Compose's own tweens honour
 * it, but hand-rolled effects (the confetti, the staged gauge drop) must check it themselves.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
