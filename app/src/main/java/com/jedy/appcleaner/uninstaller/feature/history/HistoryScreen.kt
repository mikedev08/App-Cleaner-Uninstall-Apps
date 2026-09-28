package com.jedy.appcleaner.uninstaller.feature.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.data.history.HistoryDay
import com.jedy.appcleaner.uninstaller.feature.uninstall.RowPill
import com.jedy.appcleaner.uninstaller.feature.uninstall.sizeText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/**
 * CONTRACT (frozen signature). PRD §4 Screen 12. History is the undo (PRD §6 item 21): every row
 * is a verified removal, rendered from its pre-removal snapshot, with a way back to Play.
 *
 * The hero total is the long-term reward — everything this app has ever given back — so it sits
 * at the top in the same big green as the Result screen.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        HistoryTopBar(
            clearEnabled = state.totalCount > 0,
            onBack = onBack,
            onClear = { confirmClear = true },
        )
        when {
            state.isEmpty -> HistoryEmpty(Modifier.weight(1f))
            state.isLoaded -> HistoryList(
                state = state,
                modifier = Modifier.weight(1f),
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
            containerColor = AppTheme.colors.surfaceElevated,
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
private fun HistoryTopBar(clearEnabled: Boolean, onBack: () -> Unit, onClear: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = AppTheme.colors.textPrimary,
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.history_more_options),
                    tint = AppTheme.colors.textPrimary,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(Dimens.controlRadius),
                containerColor = AppTheme.colors.surfaceElevated,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_clear), color = AppTheme.colors.textPrimary) },
                    enabled = clearEnabled,
                    onClick = {
                        menuOpen = false
                        onClear()
                    },
                )
            }
        }
    }
}

@Composable
private fun LargeTitle() {
    Text(
        text = stringResource(R.string.history_title),
        style = MaterialTheme.typography.headlineLarge,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(bottom = Dimens.gutterSmall),
    )
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    onReinstall: (HistoryRow) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottom.calculateBottomPadding() + Dimens.gutter),
    ) {
        item(key = "title") { LargeTitle() }
        item(key = "summary") { HeroStatCard(state, Modifier.animateItem()) }
        state.sections.forEach { section ->
            item(key = "day_${section.date}") { DayHeader(section, Modifier.animateItem()) }
            items(section.rows, key = { it.entry.id }) { row -> HistoryItem(row, onReinstall, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun HeroStatCard(state: HistoryUiState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
        color = colors.accentSurface,
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = Dimens.gutterLarge),
    ) {
        Text(
            text = stringResource(R.string.history_hero_freed, sizeText(state.totalBytes, state.anyEstimate)),
            style = MaterialTheme.typography.displaySmall,
            color = colors.accentText,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = pluralStringResource(R.plurals.history_hero_removed, state.totalCount, state.totalCount),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DayHeader(section: HistorySection, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val text = when (section.kind) {
        HistoryDay.Kind.TODAY -> stringResource(R.string.history_today)
        HistoryDay.Kind.YESTERDAY -> stringResource(R.string.history_yesterday)
        HistoryDay.Kind.OTHER -> remember(section.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(section.date)
        }
    }
    SectionHeader(
        title = text,
        modifier = modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.gutterLarge, bottom = 8.dp),
    )
}

@Composable
private fun HistoryItem(row: HistoryRow, onReinstall: (HistoryRow) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = row.entry
    val time = remember(entry.removedAt, context) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.removedAt))
    }
    AppRow(
        packageName = entry.packageName,
        label = entry.label,
        meta = stringResource(R.string.history_meta, sizeText(entry.bytes, entry.bytesIsEstimate), time),
        modifier = modifier,
        icon = { SnapshotAppIcon(iconPath = entry.iconPath, packageName = entry.packageName) },
        trailing = {
            Box(Modifier.padding(start = 8.dp)) {
                when {
                    row.isReinstalled -> SeverityChip(
                        text = stringResource(R.string.history_reinstalled),
                        severity = Severity.OK,
                        icon = Icons.Rounded.Check,
                    )
                    row.isFromPlay -> RowPill(
                        text = stringResource(R.string.history_reinstall),
                        onClick = { onReinstall(row) },
                        filled = false,
                    )
                    else -> Text(
                        text = stringResource(R.string.history_not_from_play),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.textMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }
        },
    )
}

/** An inviting empty state: History is the safety net, so it should read as a promise, not a void. */
@Composable
private fun HistoryEmpty(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Dimens.gutterLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(180.dp).clip(CircleShape).background(colors.accentSurface.copy(alpha = 0.5f)))
            Box(Modifier.size(128.dp).clip(CircleShape).background(colors.accentSurface))
            IconBadge(icon = Icons.Rounded.Restore, size = 72.dp)
            // Two small orbiting "apps" hint at what will land here.
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 18.dp).size(30.dp)
                    .clip(RoundedCornerShape(10.dp)).background(colors.accent),
            )
            Box(
                Modifier.align(Alignment.BottomStart).padding(bottom = 26.dp, start = 16.dp).size(22.dp)
                    .clip(RoundedCornerShape(8.dp)).background(colors.premiumGold),
            )
            Icon(
                Icons.Rounded.History, contentDescription = null, tint = colors.textMuted,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp, end = 24.dp).size(20.dp),
            )
        }
        Spacer(Modifier.height(Dimens.gutterLarge))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(64.dp))
    }
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
