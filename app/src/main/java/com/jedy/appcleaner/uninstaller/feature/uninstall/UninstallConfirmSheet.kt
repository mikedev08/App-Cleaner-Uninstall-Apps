package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = AppTheme.colors.background,
        contentColor = AppTheme.colors.textPrimary,
        dragHandle = { DragHandle() },
    ) {
        ConfirmSheetContent(
            state = state,
            onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            onConfirm = { viewModel.confirm(sourceTab) },
        )
    }
}

@Composable
private fun DragHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 44.dp, height = 5.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceMuted),
    )
}

@Composable
private fun ConfirmSheetContent(
    state: ConfirmUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val count = state.apps.size
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.gutter)) {
        Column(Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp)) {
            Text(
                text = pluralStringResource(R.plurals.uninstall_confirm_headline, count, count),
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            FreeLine(sizeText(state.totalBytes, state.isEstimate))
        }
        Spacer(Modifier.height(Dimens.gutterSmall))
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.apps, key = { it.packageName }) { app ->
                ConfirmAppCard(app, Modifier.animateItem())
            }
        }
        Row(
            Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.gutter),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Info, contentDescription = null, tint = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 1.dp).size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.uninstall_confirm_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                // PRD §6 item 21: app data is not recoverable, and the sheet doesn't pretend otherwise.
                Text(
                    text = stringResource(R.string.uninstall_confirm_data_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = Dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
        ) {
            SecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = { if (!state.isStarting) onCancel() },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = pluralStringResource(R.plurals.uninstall_confirm_button, count, count),
                onClick = onConfirm,
                enabled = count > 0 && !state.isStarting,
                destructive = true,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

/**
 * "You'll free **about 455 MB**": the translated sentence keeps its own word order (the size can
 * sit anywhere, including in RTL), and only the size is set big. Freed space is good news, so it
 * is green (`positive`); red belongs to the Uninstall button alone (design review §3.3).
 */
@Composable
private fun FreeLine(size: String) {
    val template = stringResource(R.string.uninstall_confirm_free)
    val placeholder = "%1\$s"
    val at = template.indexOf(placeholder)
    val big = MaterialTheme.typography.headlineLarge.toSpanStyle().copy(color = AppTheme.colors.positive)
    val text = buildAnnotatedString {
        if (at < 0) {
            withStyle(big) { append(size) }
        } else {
            append(template.substring(0, at))
            withStyle(big) { append(size) }
            append(template.substring(at + placeholder.length))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(lineHeight = MaterialTheme.typography.headlineLarge.lineHeight),
        color = AppTheme.colors.textSecondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmAppCard(app: ConfirmApp, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val bytes = app.bytes
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter)
            .clip(shape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, shape)
            .padding(horizontal = Dimens.space12, vertical = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.warnings.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    app.warnings.forEach { warning ->
                        SeverityChip(
                            text = stringResource(warning.chipRes()),
                            severity = warning.severity,
                            icon = Icons.Rounded.WarningAmber,
                        )
                    }
                }
                // The sentence explains the chip ("you'll need to pick another"); the first is enough.
                Text(
                    text = stringResource(app.warnings.first().textRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (bytes != null) sizeText(bytes, isEstimate = false) else stringResource(R.string.uninstall_size_unavailable),
            style = MaterialTheme.typography.titleSmall,
            // Sizes are neutral text (design review §3.3).
            color = if (bytes == null) colors.textMuted else colors.textPrimary,
            maxLines = 1,
        )
    }
}
