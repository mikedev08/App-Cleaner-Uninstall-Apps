package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
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
    val current = state.currentRow()

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.gutterSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.uninstall_progress_title),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Dimens.gutter))
            ProgressRing(progress = state.progress, current = current, position = state.position, total = state.total)
            Spacer(Modifier.height(Dimens.gutter))
            AnimatedContent(
                targetState = current?.item?.label.orEmpty(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "currentLabel",
            ) { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.uninstall_confirm_expectation),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        AnimatedVisibility(visible = state.stalled) { StalledCard(onShowAgain) }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.rows, key = { it.item.id }) { row -> QueueItemRow(row, Modifier.animateItem()) }
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            if (state.isStopping) {
                Text(
                    text = stringResource(R.string.uninstall_stopping),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                val enabled = state.isLoaded && !state.isFinished
                Text(
                    text = stringResource(R.string.uninstall_stop),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) AppTheme.colors.textSecondary else AppTheme.colors.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.chipRadius))
                        .clickable(enabled = enabled, onClick = onStop)
                        .padding(horizontal = 32.dp, vertical = 18.dp),
                )
            }
        }
    }
}

/** The app Android is asking about now; else the next in line; else the last (all done). */
private fun ProgressUiState.currentRow(): QueueRow? =
    rows.firstOrNull { it.state == ItemState.IN_PROGRESS }
        ?: rows.firstOrNull { it.state == ItemState.WAITING }
        ?: rows.lastOrNull()

/**
 * The hero: a full ring filling with finished apps, the current app's icon large in the middle
 * (swapping with a scale + fade as the queue advances) and "2 of 5" under it. The ring starts at
 * 12 o'clock and runs clockwise in LTR, counter-clockwise in RTL (PRD §6 item 23).
 */
@Composable
private fun ProgressRing(progress: Float, current: QueueRow?, position: Int, total: Int) {
    val colors = AppTheme.colors
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "ring",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                colors.gaugeTrack, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
            )
            if (animated > 0f) {
                drawArc(
                    colors.accent, startAngle = -90f, sweepAngle = (if (rtl) -360f else 360f) * animated,
                    useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = current?.item,
                contentKey = { it?.id },
                transitionSpec = {
                    (scaleIn(tween(320), initialScale = 0.7f) + fadeIn(tween(240))) togetherWith
                        (scaleOut(tween(200), targetScale = 1.15f) + fadeOut(tween(160)))
                },
                contentAlignment = Alignment.Center,
                label = "currentIcon",
            ) { item ->
                if (item != null) {
                    SnapshotAppIcon(iconPath = item.iconPath, packageName = item.packageName, size = 84.dp)
                } else {
                    Spacer(Modifier.size(84.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.uninstall_progress_of, position, total),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun QueueItemRow(row: QueueRow, modifier: Modifier = Modifier) {
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
        modifier = modifier,
        icon = { SnapshotAppIcon(iconPath = row.item.iconPath, packageName = row.item.packageName, size = 44.dp) },
        trailing = { AnimatedStateIcon(row.state, Modifier.padding(start = 8.dp)) },
    )
}

/** An OEM dialog closed without reporting (PRD §6 item 7): a way forward that is not a timeout. */
@Composable
private fun StalledCard(onShowAgain: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.space4),
        color = AppTheme.colors.warningSurface,
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.VisibilityOff, contentDescription = null,
                // Amber is a fill, never text or an icon on its own surface (design review §3.3).
                tint = AppTheme.colors.onWarningSurface, modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.uninstall_stalled_title),
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.onWarningSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            RowPill(text = stringResource(R.string.uninstall_stalled_action), onClick = onShowAgain)
        }
    }
}

@Composable
private fun StopDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surfaceElevated,
        title = { Text(stringResource(R.string.uninstall_stop_title), color = AppTheme.colors.textPrimary) },
        text = { Text(stringResource(R.string.uninstall_stop_body), color = AppTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.uninstall_stop), color = AppTheme.colors.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.uninstall_stop_keep_going), color = AppTheme.colors.accentText)
            }
        },
    )
}
