package com.jedy.appcleaner.uninstaller.feature.usageaccess

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * CONTRACT (frozen signature). PRD §4 Screen 11 — the prominent disclosure Play's User Data
 * policy requires before sending anyone to the Usage Access page: what is read, why, and that it
 * never leaves the phone, in plain words and before the system screen.
 *
 * Calls [onClose] on "Not now", and on its own once access is detected as granted (re-checked on
 * every resume, i.e. when the user comes back from Settings).
 */
@Composable
fun UsageAccessScreen(
    trigger: UsageAccessTrigger,
    onClose: () -> Unit,
) {
    val viewModel: UsageAccessViewModel = hiltViewModel()
    val context = LocalContext.current
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(trigger) { viewModel.onShown(trigger) }
    LaunchedEffect(viewModel) { viewModel.close.collect { currentOnClose() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    UsageAccessContent(
        onContinue = { openUsageAccessSettings(context, viewModel) },
        onNotNow = onClose,
    )
}

/**
 * PRD §6 item 13: try the package-specific page; if the OEM refuses it, fall back to the plain
 * list. Whenever the list is what opens, a 2-second hint says what to look for.
 */
private fun openUsageAccessSettings(context: Context, viewModel: UsageAccessViewModel) {
    val target = viewModel.onContinue()
    var onFallbackPage = target.usesFallbackPage
    var opened = context.startSafely(target.intent)
    if (!opened && !target.usesFallbackPage) {
        opened = context.startSafely(viewModel.fallbackIntent())
        onFallbackPage = true
    }
    when {
        !opened -> {
            viewModel.onSettingsUnavailable()
            Toast.makeText(context, context.getString(R.string.usage_settings_unavailable), Toast.LENGTH_LONG).show()
        }
        onFallbackPage ->
            Toast.makeText(context, context.getString(R.string.usage_fallback_toast), Toast.LENGTH_SHORT).show()
    }
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
private fun UsageAccessContent(onContinue: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            UsageAccessIllustration()
            Spacer(Modifier.height(Dimens.gutterLarge))
            Text(
                text = stringResource(R.string.usage_title),
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Dimens.gutterLarge))
            DisclosureLine(Icons.Rounded.Visibility, stringResource(R.string.usage_what_label), stringResource(R.string.usage_what_body))
            DisclosureLine(Icons.Rounded.Insights, stringResource(R.string.usage_why_label), stringResource(R.string.usage_why_body))
            DisclosureLine(Icons.Rounded.Lock, stringResource(R.string.usage_where_label), stringResource(R.string.usage_where_body))
            Spacer(Modifier.height(Dimens.gutterLarge))
            ToggleHint()
            Spacer(Modifier.height(Dimens.gutterLarge))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutterLarge, vertical = Dimens.gutter),
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.buttonHeight),
                shape = RoundedCornerShape(Dimens.controlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.teal,
                    contentColor = AppTheme.colors.onTeal,
                ),
            ) {
                Text(stringResource(R.string.usage_continue), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_not_now),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DisclosureLine(icon: ImageVector, label: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.tealSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.teal, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
