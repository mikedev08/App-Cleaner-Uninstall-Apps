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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.jedy.appcleaner.uninstaller.core.locale.LocalAppLocale
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
import com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.LargeTitle
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.component.TopBarAction
import com.jedy.appcleaner.uninstaller.core.ui.component.listContentPadding
import com.jedy.appcleaner.uninstaller.core.ui.component.rememberIsLargeTitleCollapsed
import com.jedy.appcleaner.uninstaller.core.ui.component.rememberIsScrolled
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.history.HistoryDay
import com.jedy.appcleaner.uninstaller.feature.uninstall.RowPill
import com.jedy.appcleaner.uninstaller.feature.uninstall.formatSize
import com.jedy.appcleaner.uninstaller.feature.uninstall.sizeText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/**
 * CONTRACT (frozen signature). PRD §4 Screen 12. History is the undo (PRD §6 item 21): every row
 * is a verified removal, rendered from its pre-removal snapshot, with a way back to Play.
 *
 * The hero total is the long-term reward — everything this app has ever given back — so it sits
 * at the top in the same big green as the Result screen. Hero, day headings and rows share the
 * 20dp gutter; the top bar is the shared [AppTopBar].
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val showList = state.isLoaded && !state.isEmpty
    val scrolled = rememberIsScrolled(listState)
    val titleCollapsed = rememberIsLargeTitleCollapsed(listState)
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        HistoryTopBar(
            clearEnabled = state.totalCount > 0,
            scrolled = showList && scrolled,
            // The list has its own large title; the empty state has none, so the bar keeps its own.
            titleVisible = !showList || titleCollapsed,
            onBack = onBack,
            onClear = { confirmClear = true },
        )
        val content = Modifier
            .weight(1f)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        when {
            state.isEmpty -> HistoryEmpty(content)
            state.isLoaded -> HistoryList(
                state = state,
                listState = listState,
                modifier = content,
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

/** The shared [AppTopBar]: 48dp back and overflow, a tonal fill once the list scrolls under it. */
@Composable
private fun HistoryTopBar(
    clearEnabled: Boolean,
    scrolled: Boolean,
    titleVisible: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppTopBar(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        scrolled = scrolled,
        titleVisible = titleVisible,
        actions = {
            Box {
                TopBarAction(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.history_more_options),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(Dimens.controlRadius),
                    containerColor = AppTheme.colors.surfaceElevated,
                ) {
                    DropdownMenuItem(
                        // Clearing the list is the one destructive action here, so it is the one red.
                        text = {
                            Text(
                                stringResource(R.string.history_clear),
                                color = if (clearEnabled) AppTheme.colors.removeRed else AppTheme.colors.textMuted,
                            )
                        },
                        enabled = clearEnabled,
                        onClick = {
                            menuOpen = false
                            onClear()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onReinstall: (HistoryRow) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = listContentPadding(),
    ) {
        item(key = "title") { LargeTitle(stringResource(R.string.history_title)) }
        item(key = "summary") { HeroStatCard(state, Modifier.animateItem()) }
        state.sections.forEach { section ->
            item(key = "day_${section.date}") { DayHeader(section, Modifier.animateItem()) }
            items(section.rows, key = { it.entry.id }) { row -> HistoryItem(row, onReinstall, Modifier.animateItem()) }
        }
    }
}

/**
 * The lifetime total. The headline states the number plainly ("470 MB freed"); when some sizes
 * are APK-only estimates, the sub-line says so instead of hedging the headline (design review §6).
 */
@Composable
private fun HeroStatCard(state: HistoryUiState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
        color = colors.accentSurface,
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = Dimens.space24),
    ) {
        Text(
            text = stringResource(R.string.history_hero_freed, formatSize(context, state.totalBytes)),
            style = MaterialTheme.typography.displaySmall,
            color = colors.positive,
        )
        Spacer(Modifier.height(Dimens.space8))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            val removed = pluralStringResource(R.plurals.history_hero_removed, state.totalCount, state.totalCount)
            Text(
                text = if (state.anyEstimate) stringResource(R.string.history_hero_estimated, removed) else removed,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DayHeader(section: HistorySection, modifier: Modifier = Modifier) {
    val locale = LocalAppLocale.current
    val text = when (section.kind) {
        HistoryDay.Kind.TODAY -> stringResource(R.string.history_today)
        HistoryDay.Kind.YESTERDAY -> stringResource(R.string.history_yesterday)
        HistoryDay.Kind.OTHER -> remember(section.date, locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(section.date)
        }
    }
    SectionHeader(
        title = text,
        // Rows add half a row gap above themselves, so 8dp here makes the 12dp heading → content.
        modifier = modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space24, bottom = Dimens.space8),
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
        // One trailing pattern for every state: a pill of the same height in the same place. The
        // action is green and tappable; the two statuses are quiet and not.
        trailing = {
            Box(Modifier.padding(start = Dimens.space8)) {
                when {
                    row.isReinstalled -> StatusPill(
                        text = stringResource(R.string.history_reinstalled),
                        icon = Icons.Rounded.Check,
                    )
                    row.isFromPlay -> RowPill(
                        text = stringResource(R.string.history_reinstall),
                        onClick = { onReinstall(row) },
                        filled = false,
                    )
                    else -> StatusPill(text = stringResource(R.string.history_not_from_play))
                }
            }
        },
    )
}

/** A non-interactive [RowPill] twin (same height and shape) for a row's status. */
@Composable
private fun StatusPill(text: String, icon: ImageVector? = null) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(colors.surfaceMuted)
            .padding(horizontal = 14.dp, vertical = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Dimens.space4))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1)
    }
}

/** An inviting empty state: History is the safety net, so it should read as a promise, not a void. */
@Composable
private fun HistoryEmpty(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Dimens.space32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Calm on purpose (design review §2A): two soft rings and one icon, no decoration.
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(180.dp).clip(CircleShape).background(colors.accentSurface.copy(alpha = 0.5f)))
            Box(Modifier.size(128.dp).clip(CircleShape).background(colors.accentSurface))
            IconBadge(icon = Icons.Rounded.Restore, size = 72.dp)
        }
        Spacer(Modifier.height(Dimens.space24))
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
