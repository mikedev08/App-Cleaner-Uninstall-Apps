package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.feature.history.SnapshotAppIcon

/**
 * CONTRACT (frozen signature). PRD §4 Screen 9: full screen, underneath Android's dialogs.
 *
 * The screen's lifecycle *is* the engine's foreground gate (PRD Feature 2): RESUMED lets the queue
 * ask for the next app, PAUSED (Android's dialog on top) keeps it going, STOPPED (the user left)
 * lets the current dialog finish and then waits.
 */
@Composable
fun UninstallProgressScreen(
    batchId: Long,
    onFinished: (batchId: Long) -> Unit,
) {
    val viewModel: UninstallProgressViewModel = hiltViewModel()
    LaunchedEffect(batchId) { viewModel.bind(batchId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPaused()
                Lifecycle.Event.ON_STOP -> viewModel.onStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onStopped()
        }
    }

    // Android's own confirmation dialog, started from this (foreground) activity.
    val context = LocalContext.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val confirmation = state.confirmation
    LaunchedEffect(confirmation, visible) {
        if (confirmation != null && visible) viewModel.launchConfirmation(context, confirmation)
    }

    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) latestOnFinished(batchId)
    }

    var confirmStop by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !state.isFinished) { confirmStop = true }

    ProgressContent(
        state = state,
        onStop = { confirmStop = true },
        onShowAgain = viewModel::onShowAgain,
    )

    if (confirmStop) {
        StopDialog(
            onConfirm = {
                confirmStop = false
                viewModel.onStop()
            },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    onStop: () -> Unit,
    onShowAgain: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.position) {
        if (state.position > 0) listState.animateScrollToItem((state.position - 2).coerceAtLeast(0))
    }
    val progress by animateFloatAsState(targetValue = state.progress, label = "uninstallProgress")

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier.padding(horizontal = Dimens.gutterLarge, vertical = Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.uninstall_progress_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.uninstall_progress_count, state.position, state.total),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            // Fills from the start edge, so it mirrors in Arabic and Hebrew (PRD §6 item 23).
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = AppTheme.colors.teal,
                trackColor = AppTheme.colors.surfaceMuted,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                text = stringResource(R.string.uninstall_confirm_expectation),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }

        AnimatedVisibility(visible = state.stalled) { StalledCard(onShowAgain) }

        HorizontalDivider(color = AppTheme.colors.border)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.rows, key = { it.item.id }) { row -> QueueItemRow(row) }
        }
        HorizontalDivider(color = AppTheme.colors.border)

        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (state.isStopping) {
                Text(
                    text = stringResource(R.string.uninstall_stopping),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                TextButton(onClick = onStop, enabled = state.isLoaded && !state.isFinished) {
                    Text(stringResource(R.string.uninstall_stop), color = AppTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(row: QueueRow) {
    val meta = when (row.state) {
        ItemState.WAITING -> stringResource(R.string.uninstall_state_waiting)
        ItemState.IN_PROGRESS -> stringResource(R.string.uninstall_state_confirming)
        ItemState.REMOVED -> stringResource(R.string.uninstall_state_removed)
        ItemState.SKIPPED -> stringResource(R.string.uninstall_state_skipped)
        ItemState.FAILED -> stringResource(R.string.uninstall_state_failed)
        ItemState.ALREADY_REMOVED -> stringResource(R.string.uninstall_state_already_removed)
    }
    AppRow(
        packageName = row.item.packageName,
        label = row.item.label,
        meta = meta,
        icon = { SnapshotAppIcon(iconPath = row.item.iconPath, packageName = row.item.packageName) },
        trailing = { StateIcon(row.state) },
    )
}

@Composable
private fun StateIcon(state: ItemState) {
    val modifier = Modifier.padding(start = 8.dp).size(22.dp)
    when (state) {
        ItemState.WAITING ->
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = AppTheme.colors.textSecondary.copy(alpha = 0.5f), modifier = modifier)
        ItemState.IN_PROGRESS ->
            CircularProgressIndicator(color = AppTheme.colors.teal, strokeWidth = 2.dp, modifier = modifier.padding(2.dp))
        ItemState.REMOVED ->
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AppTheme.colors.teal, modifier = modifier)
        ItemState.SKIPPED ->
            Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = modifier)
        ItemState.FAILED ->
            Icon(Icons.Rounded.Error, contentDescription = null, tint = AppTheme.colors.removeRed, modifier = modifier)
        ItemState.ALREADY_REMOVED ->
            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = modifier)
    }
}

/** An OEM dialog closed without reporting (PRD §6 item 7): a way forward that is not a timeout. */
@Composable
private fun StalledCard(onShowAgain: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.surface,
        border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
    ) {
        Row(
            Modifier.padding(start = Dimens.gutter, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.uninstall_stalled_title),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAgain) {
                Text(stringResource(R.string.uninstall_stalled_action), color = AppTheme.colors.teal)
            }
        }
    }
}

@Composable
private fun StopDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.background,
        title = { Text(stringResource(R.string.uninstall_stop_title), color = AppTheme.colors.textPrimary) },
        text = { Text(stringResource(R.string.uninstall_stop_body), color = AppTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.uninstall_stop), color = AppTheme.colors.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.uninstall_stop_keep_going), color = AppTheme.colors.teal)
            }
        },
    )
}
