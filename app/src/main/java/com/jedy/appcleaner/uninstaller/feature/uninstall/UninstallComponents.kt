package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.data.uninstall.AppWarning
import com.jedy.appcleaner.uninstaller.data.uninstall.FailureReason
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.NotRemovedItem

/** "2.4 GB", or "about 2.4 GB" when the number is APK-only (PRD Feature 2). */
@Composable
fun sizeText(bytes: Long, isEstimate: Boolean): String {
    val formatted = formatBytes(LocalContext.current, bytes)
    return if (isEstimate) stringResource(R.string.uninstall_size_about, formatted) else formatted
}

@StringRes
fun AppWarning.textRes(): Int = when (this) {
    AppWarning.DEVICE_ADMIN -> R.string.uninstall_warning_device_admin
    AppWarning.KEYBOARD -> R.string.uninstall_warning_keyboard
    AppWarning.LAUNCHER -> R.string.uninstall_warning_launcher
    AppWarning.SMS -> R.string.uninstall_warning_sms
    AppWarning.DIALER -> R.string.uninstall_warning_dialer
    AppWarning.ALWAYS_RUNNING -> R.string.uninstall_warning_always_running
}

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
 * Warn, don't block (PRD §6 item 9). Red because the chip is about what removing this app will
 * break — it lives only on the destructive Confirm Sheet.
 */
@Composable
fun WarningChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .background(AppTheme.colors.removeRedSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = AppTheme.colors.removeRed,
            modifier = Modifier.size(14.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.removeRed)
    }
}
