package com.jedy.appcleaner.uninstaller.feature.usageaccess

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Opens the Usage access page in Settings (PRD §6 "OEM without the package-specific Usage Access
 * page"): the `package:` page where the phone offers one, else the plain list.
 *
 * The "Find App Cleaner in the list and turn it on" hint shows on *every* launch: on Android 14+
 * Samsung, Xiaomi and Pixel accept the `package:` URI but open the general list anyway, and
 * nothing tells us which one appeared. On a phone that did open our own toggle, the hint is
 * still true enough to be harmless.
 *
 * @return true when Settings opened; the [UsageAccessRoundTrip] then watches for the return.
 */
fun launchUsageAccessSettings(context: Context, roundTrip: UsageAccessRoundTrip, trigger: UsageAccessTrigger): Boolean {
    val target = roundTrip.settingsIntent()
    var usedFallback = target.usesFallbackPage
    var opened = context.startSafely(target.intent)
    if (!opened && !usedFallback) {
        opened = context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        usedFallback = true
    }
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.usage_settings_unavailable), Toast.LENGTH_LONG).show()
        return false
    }
    roundTrip.onSettingsOpened(trigger, usedFallback)
    // LENGTH_SHORT is the 2-second overlay the PRD asks for; it shows on top of Settings.
    Toast.makeText(context, context.getString(R.string.usage_fallback_toast), Toast.LENGTH_SHORT).show()
    return true
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@HiltViewModel
class UsageAccessStillOffViewModel @Inject constructor(
    val roundTrip: UsageAccessRoundTrip,
) : ViewModel() {
    val stillOff: StateFlow<UsageAccessTrigger?> = roundTrip.stillOff
}

/**
 * The gentle card a screen shows when the user came back from Settings without turning Usage
 * access on — only on the screen they left from ([triggers]), and gone as soon as access is on.
 * "Try again" goes straight back to Settings: the disclosure was already shown on this trip.
 *
 * @param otherwise what the slot shows the rest of the time (e.g. the usual "Allow access" card).
 */
@Composable
fun UsageAccessStillOffCard(
    triggers: Set<UsageAccessTrigger>,
    modifier: Modifier = Modifier,
    otherwise: @Composable () -> Unit = {},
) {
    val viewModel: UsageAccessStillOffViewModel = hiltViewModel()
    val stillOff by viewModel.stillOff.collectAsStateWithLifecycle()
    val trigger = stillOff?.takeIf { it in triggers }
    if (trigger == null) {
        otherwise()
        return
    }
    val context = LocalContext.current
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon = Icons.Outlined.ToggleOff, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.usage_still_off_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.usage_still_off_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            text = stringResource(R.string.action_retry),
            onClick = { launchUsageAccessSettings(context, viewModel.roundTrip, trigger) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
