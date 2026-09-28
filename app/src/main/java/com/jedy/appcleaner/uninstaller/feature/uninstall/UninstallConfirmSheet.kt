package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * CONTRACT (frozen signature). PRD §4 Screen 8: the last checkpoint before anything is removed
 * (PRD §6 item 21 — there is no undo, History is the recovery path).
 *
 * On confirm it snapshots and queues the batch, then calls [onStarted]; the caller navigates to
 * the Progress screen and stops showing this sheet. [onDismiss] is only for Cancel / swipe-away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallConfirmSheet(
    packages: List<String>,
    sourceTab: HomeTab,
    onDismiss: () -> Unit,
    onStarted: (batchId: Long) -> Unit,
) {
    val viewModel: UninstallConfirmViewModel = hiltViewModel()
    LaunchedEffect(packages) { viewModel.setPackages(packages) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnStarted by rememberUpdatedState(onStarted)
    LaunchedEffect(viewModel) { viewModel.started.collect { latestOnStarted(it) } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { if (!state.isStarting) onDismiss() },
        sheetState = sheetState,
        containerColor = AppTheme.colors.background,
        contentColor = AppTheme.colors.textPrimary,
    ) {
        ConfirmSheetContent(
            state = state,
            onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            onConfirm = { viewModel.confirm(sourceTab) },
        )
    }
}

@Composable
private fun ConfirmSheetContent(
    state: ConfirmUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val count = state.apps.size
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.gutter)) {
        Column(Modifier.padding(horizontal = Dimens.gutterLarge), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.uninstall_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.uninstall_confirm_summary, count, count, sizeText(state.totalBytes, state.isEstimate),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(Dimens.gutterSmall))
        HorizontalDivider(color = AppTheme.colors.border)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            items(state.apps, key = { it.packageName }) { app -> ConfirmAppRow(app) }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        Column(
            Modifier.padding(horizontal = Dimens.gutterLarge, vertical = Dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.uninstall_confirm_expectation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                )
            }
            // PRD §6 item 21: app data is not recoverable, and the sheet doesn't pretend otherwise.
            Text(
                text = stringResource(R.string.uninstall_confirm_data_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.gutterLarge, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !state.isStarting,
                modifier = Modifier.weight(1f).height(Dimens.buttonHeight),
            ) {
                Text(stringResource(R.string.action_cancel), color = AppTheme.colors.textPrimary)
            }
            Button(
                onClick = onConfirm,
                enabled = count > 0 && !state.isStarting,
                modifier = Modifier.weight(1.4f).height(Dimens.buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.removeRed,
                    contentColor = AppTheme.colors.onRemoveRed,
                    disabledContainerColor = AppTheme.colors.removeRed.copy(alpha = 0.5f),
                    disabledContentColor = AppTheme.colors.onRemoveRed,
                ),
            ) {
                if (state.isStarting) {
                    CircularProgressIndicator(
                        color = AppTheme.colors.onRemoveRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(pluralStringResource(R.plurals.uninstall_confirm_button, count, count))
                }
            }
        }
    }
}

@Composable
private fun ConfirmAppRow(app: ConfirmApp) {
    AppRow(
        packageName = app.packageName,
        label = app.label,
        meta = app.bytes?.let { sizeText(it, isEstimate = false) } ?: stringResource(R.string.uninstall_size_unavailable),
        badge = if (app.warnings.isEmpty()) null else {
            { app.warnings.forEach { WarningChip(stringResource(it.textRes())) } }
        },
    )
}
