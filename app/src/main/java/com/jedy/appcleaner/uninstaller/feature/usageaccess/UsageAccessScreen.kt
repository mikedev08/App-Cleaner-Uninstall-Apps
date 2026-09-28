package com.jedy.appcleaner.uninstaller.feature.usageaccess

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
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

/**
 * Trust is built in order: a friendly picture, one plain sentence, then the three facts a
 * cautious user wants before touching a system permission (what, why, where it goes), and only
 * then the hint and the action. The CTA is pinned below the scroll so it never scrolls away.
 */
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
                .padding(horizontal = Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            UsageAccessHero()
            Spacer(Modifier.height(Dimens.gutter))
            Text(
                text = stringResource(R.string.usage_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.usage_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(Dimens.space24))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                TrustRow(Icons.Rounded.Visibility, stringResource(R.string.usage_what_label), stringResource(R.string.usage_what_body))
                Spacer(Modifier.height(Dimens.space16))
                TrustRow(Icons.Rounded.Insights, stringResource(R.string.usage_why_label), stringResource(R.string.usage_why_body))
                Spacer(Modifier.height(Dimens.space16))
                TrustRow(Icons.Rounded.VerifiedUser, stringResource(R.string.usage_where_label), stringResource(R.string.usage_where_body))
            }
            Spacer(Modifier.height(Dimens.space24))
            ToggleHint()
            Spacer(Modifier.height(Dimens.gutter))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 8.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.usage_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .clickable(role = Role.Button, onClick = onNotNow),
                contentAlignment = Alignment.Center,
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
private fun TrustRow(icon: ImageVector, label: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon = icon, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
