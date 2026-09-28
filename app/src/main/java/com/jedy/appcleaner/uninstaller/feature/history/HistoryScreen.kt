package com.jedy.appcleaner.uninstaller.feature.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.history.HistoryDay
import com.jedy.appcleaner.uninstaller.feature.uninstall.sizeText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/**
 * CONTRACT (frozen signature). PRD §4 Screen 12. History is the undo (PRD §6 item 21): every row
 * is a verified removal, rendered from its pre-removal snapshot, with a way back to Play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.history_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_clear)) },
                                enabled = state.totalCount > 0,
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background,
                    titleContentColor = AppTheme.colors.textPrimary,
                    navigationIconContentColor = AppTheme.colors.textPrimary,
                    actionIconContentColor = AppTheme.colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        when {
            state.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                )
            }
            state.isLoaded -> HistoryList(
                state = state,
                contentPadding = padding,
                onReinstall = { row ->
                    viewModel.onReinstallTapped(row.entry)
                    if (!openPlayListing(context, row.entry.packageName)) {
                        Toast.makeText(context, R.string.history_store_unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = AppTheme.colors.background,
            title = { Text(stringResource(R.string.history_clear_title), color = AppTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.history_clear_body), color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) {
                    Text(stringResource(R.string.history_clear_confirm), color = AppTheme.colors.removeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel), color = AppTheme.colors.textPrimary)
                }
            },
        )
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    contentPadding: PaddingValues,
    onReinstall: (HistoryRow) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "summary") { SummaryCard(state) }
        state.sections.forEach { section ->
            item(key = "day_${section.date}") { DayHeader(section) }
            items(section.rows, key = { it.entry.id }) { row -> HistoryItem(row, onReinstall) }
        }
    }
}

@Composable
private fun SummaryCard(state: HistoryUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.surface,
        border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.history_summary, state.totalCount, state.totalCount, sizeText(state.totalBytes, state.anyEstimate),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(Dimens.gutter),
        )
    }
}

@Composable
private fun DayHeader(section: HistorySection) {
    val locale = LocalConfiguration.current.locales[0]
    val text = when (section.kind) {
        HistoryDay.Kind.TODAY -> stringResource(R.string.history_today)
        HistoryDay.Kind.YESTERDAY -> stringResource(R.string.history_yesterday)
        HistoryDay.Kind.OTHER -> remember(section.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(section.date)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.gutter, bottom = 4.dp),
    )
}

@Composable
private fun HistoryItem(row: HistoryRow, onReinstall: (HistoryRow) -> Unit) {
    val context = LocalContext.current
    val entry = row.entry
    val time = remember(entry.removedAt, context) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.removedAt))
    }
    AppRow(
        packageName = entry.packageName,
        label = entry.label,
        meta = stringResource(R.string.history_meta, sizeText(entry.bytes, entry.bytesIsEstimate), time),
        icon = { SnapshotAppIcon(iconPath = entry.iconPath, packageName = entry.packageName) },
        trailing = {
            when {
                row.isReinstalled -> ReinstalledTag()
                row.isFromPlay -> OutlinedButton(
                    onClick = { onReinstall(row) },
                    border = BorderStroke(Dimens.hairline, AppTheme.colors.accent),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.history_reinstall), color = AppTheme.colors.accent)
                }
                else -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.history_not_from_play))
                }
            }
        },
    )
}

@Composable
private fun ReinstalledTag() {
    Text(
        text = stringResource(R.string.history_reinstalled),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.accent,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(AppTheme.colors.accentSurface, RoundedCornerShape(Dimens.chipRadius))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** PRD Feature 4: the Play listing, with the web listing as the fallback when there is no Play Store. */
private fun openPlayListing(context: Context, packageName: String): Boolean {
    val uris = listOf(
        "market://details?id=$packageName",
        "https://play.google.com/store/apps/details?id=$packageName",
    )
    for (uri in uris) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall through to the web listing.
        }
    }
    return false
}
