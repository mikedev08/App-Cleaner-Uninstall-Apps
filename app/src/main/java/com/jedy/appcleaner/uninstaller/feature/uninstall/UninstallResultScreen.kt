package com.jedy.appcleaner.uninstaller.feature.uninstall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.uninstall.BatchSummary
import com.jedy.appcleaner.uninstaller.data.uninstall.FailureReason
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.NotRemovedItem
import com.jedy.appcleaner.uninstaller.feature.history.SnapshotAppIcon

/**
 * CONTRACT (frozen signature). PRD §4 Screen 10. [onRetry] receives a new batch (the "Try again"
 * apps) to run through the progress screen. No ads in this build.
 */
@Composable
fun UninstallResultScreen(
    batchId: Long,
    onDone: () -> Unit,
    onOpenUnused: () -> Unit,
    onRetry: (newBatchId: Long) -> Unit,
) {
    val viewModel: UninstallResultViewModel = hiltViewModel()
    LaunchedEffect(batchId) { viewModel.bind(batchId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(viewModel) { viewModel.retried.collect { latestOnRetry(it) } }
    BackHandler(onBack = onDone)

    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val summary = state.summary
            if (summary != null) {
                Spacer(Modifier.height(40.dp))
                FreedHeader(summary)
                Notes(summary)
                val notRemoved = summary.notRemoved
                if (notRemoved.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.gutterLarge))
                    NotRemovedSection(
                        items = notRemoved,
                        retryEnabled = !state.isRetrying,
                        onRetry = viewModel::retry,
                        onOpenSecuritySettings = { openSecuritySettings(context) },
                    )
                }
                if (state.teaser.count > 0) {
                    Spacer(Modifier.height(Dimens.gutter))
                    UnusedTeaserCard(state.teaser, onOpenUnused)
                }
                Spacer(Modifier.height(Dimens.gutter))
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutter, vertical = Dimens.gutterSmall)
                .height(Dimens.buttonHeight),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.onAccent),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}

/** The count-up (PRD §1: one of only two motions in the app). Runs once, not on every rotation. */
@Composable
private fun FreedHeader(summary: BatchSummary) {
    val removed = summary.removedCount
    val target = summary.freedBytes
    var counted by rememberSaveable(summary.batch.batchId) { mutableStateOf(false) }
    val animated = remember(summary.batch.batchId) { Animatable(if (counted) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (counted) {
            animated.snapTo(target.toFloat())
        } else {
            animated.animateTo(target.toFloat(), tween(durationMillis = 1_200, easing = FastOutSlowInEasing))
            counted = true
        }
    }
    val shownBytes = if (counted) target else animated.value.toLong()

    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .background(if (removed > 0) AppTheme.colors.accentSurface else AppTheme.colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (removed > 0) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (removed > 0) AppTheme.colors.accent else AppTheme.colors.textSecondary,
            modifier = Modifier.size(38.dp),
        )
    }
    Spacer(Modifier.height(Dimens.gutter))
    if (removed > 0) {
        Text(
            text = stringResource(R.string.uninstall_result_freed, sizeText(shownBytes, summary.freedIsEstimate)),
            style = MaterialTheme.typography.displaySmall,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = pluralStringResource(R.plurals.uninstall_result_removed, removed, removed),
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            text = stringResource(R.string.uninstall_result_none_title),
            style = MaterialTheme.typography.headlineSmall,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.uninstall_result_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Notes(summary: BatchSummary) {
    val notAttempted = summary.notRemoved.any { it.state == ItemState.WAITING || it.state == ItemState.IN_PROGRESS }
    if (summary.batch.stoppedEarly && notAttempted) {
        Spacer(Modifier.height(Dimens.gutterSmall))
        Text(
            text = stringResource(R.string.uninstall_result_stopped_note),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    val already = summary.alreadyRemovedCount
    if (already > 0) {
        Spacer(Modifier.height(Dimens.gutterSmall))
        Text(
            text = pluralStringResource(R.plurals.uninstall_result_already_removed, already, already),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotRemovedSection(
    items: List<NotRemovedItem>,
    retryEnabled: Boolean,
    onRetry: (List<String>) -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.surface,
        border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Dimens.gutter, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.uninstall_result_not_removed, items.size, items.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.uninstall_cd_hide_details else R.string.uninstall_cd_show_details,
                    ),
                    tint = AppTheme.colors.textSecondary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = AppTheme.colors.border)
                    items.forEach { entry ->
                        NotRemovedRow(
                            entry = entry,
                            retryEnabled = retryEnabled,
                            onRetry = { onRetry(listOf(entry.item.packageName)) },
                            onOpenSecuritySettings = onOpenSecuritySettings,
                        )
                    }
                    val retryable = items.filter { it.canRetry }
                    if (retryable.size >= 2) {
                        HorizontalDivider(color = AppTheme.colors.border)
                        TextButton(
                            onClick = { onRetry(retryable.map { it.item.packageName }) },
                            enabled = retryEnabled,
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                        ) {
                            Text(stringResource(R.string.uninstall_retry_all), color = AppTheme.colors.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotRemovedRow(
    entry: NotRemovedItem,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    AppRow(
        packageName = entry.item.packageName,
        label = entry.item.label,
        meta = entry.reasonText(),
        icon = { SnapshotAppIcon(iconPath = entry.item.iconPath, packageName = entry.item.packageName) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                if (entry.reason == FailureReason.DEVICE_ADMIN) {
                    TextButton(onClick = onOpenSecuritySettings) {
                        Text(stringResource(R.string.action_open_settings), color = AppTheme.colors.accent)
                    }
                }
                // Never for BLOCKED: retrying cannot succeed (PRD §6 item 2).
                if (entry.canRetry) {
                    TextButton(onClick = onRetry, enabled = retryEnabled) {
                        Text(stringResource(R.string.action_retry), color = AppTheme.colors.accent)
                    }
                }
            }
        },
    )
}

@Composable
private fun UnusedTeaserCard(teaser: UnusedTeaser, onOpenUnused: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.accentSurface,
        onClick = onOpenUnused,
    ) {
        Row(
            Modifier.padding(start = Dimens.gutter, end = 4.dp, top = Dimens.gutterSmall, bottom = Dimens.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = AppTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.uninstall_result_teaser, teaser.count, teaser.count, teaser.thresholdDays,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                )
                TextButton(onClick = onOpenUnused, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.uninstall_result_teaser_action), color = AppTheme.colors.accent)
                }
            }
        }
    }
}

/** PRD §6 item 1: device admins are turned off from the Security page. */
private fun openSecuritySettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next, more generic page.
        }
    }
}
