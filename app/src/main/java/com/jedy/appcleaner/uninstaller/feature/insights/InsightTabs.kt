package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * CONTRACT (frozen signatures). PRD §4 Screens 5–6. Home embeds these as the Unused and Large
 * tab bodies. They read/write SelectionStore directly.
 *
 * Each is a LazyColumn: give it bounded height (e.g. `Modifier.weight(1f)`), never a
 * verticalScroll parent. The bottom content padding clears Home's Selection Bar.
 *
 * @param searchQuery Home's inline search text; filter rows by label and package name.
 * @param onRequestAccess open the Usage Access disclosure.
 * @param onUnlock open the paywall.
 * @param onOpenDetails open Home's App Details sheet for a package.
 */
@Composable
fun UnusedTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onUnlock: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UnusedTabViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val content = state.content
    val teaserVisible = content is UnusedContent.Locked && content.count > 0
    LaunchedEffect(teaserVisible) { if (teaserVisible) viewModel.onTeaserViewed() }

    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { InsightsDateFormatter(locale) }
    val now = remember(content) { System.currentTimeMillis() }
    val visible = remember(content, searchQuery) {
        (content as? UnusedContent.Unlocked)?.rows?.let { InsightSort.filter(it, searchQuery) }.orEmpty()
    }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.selectionBarHeight + Dimens.gutter),
    ) {
        if (content != UnusedContent.NoAccess) {
            item(key = "chips") { ThresholdChips(state.thresholdDays, viewModel::onThresholdSelected) }
        }
        when (content) {
            UnusedContent.NoAccess -> item(key = "access") {
                AccessCard(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.insights_unused_access_title),
                    body = stringResource(R.string.insights_unused_access_body),
                    onRequestAccess = onRequestAccess,
                )
            }
            UnusedContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_unused))
            }
            is UnusedContent.Locked -> item(key = "teaser") {
                if (content.count == 0) {
                    UnusedEmpty(state.thresholdDays)
                } else {
                    LockedTeaser(
                        headline = countAndSize(content.count, content.totalBytes),
                        caption = pluralStringResource(
                            R.plurals.insights_unused_teaser_caption, state.thresholdDays, state.thresholdDays,
                        ),
                        body = stringResource(R.string.insights_unused_teaser_body),
                        previewPackages = content.previewPackages,
                        onUnlock = onUnlock,
                    )
                }
            }
            is UnusedContent.Unlocked -> if (content.rows.isEmpty()) {
                item(key = "empty") { UnusedEmpty(state.thresholdDays) }
            } else {
                item(key = "header") {
                    SelectAllHeader(
                        summary = countAndSize(content.rows.size, content.totalBytes),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    val lastUsedAt = row.lastUsedAt
                    val meta = if (lastUsedAt != null) {
                        stringResource(R.string.insights_last_opened, remember(lastUsedAt, dates, now) { dates.ago(lastUsedAt, now) })
                    } else {
                        stringResource(R.string.insights_not_opened_since, remember(row.notOpenedSince, dates) { dates.monthYear(row.notOpenedSince) })
                    }
                    AppRow(
                        packageName = row.app.packageName,
                        label = row.app.label,
                        meta = meta,
                        selected = row.app.packageName in selected,
                        onToggleSelected = { viewModel.onToggle(row.app.packageName) },
                        onClick = { onOpenDetails(row.app.packageName) },
                        trailingText = formatBytes(context, row.bytes),
                    )
                }
            }
        }
    }
}

@Composable
fun LargeTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onUnlock: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LargeTabViewModel = hiltViewModel()
    val content by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val teaserVisible = content is LargeContent.Locked
    LaunchedEffect(teaserVisible) { if (teaserVisible) viewModel.onTeaserViewed() }

    val current = content
    val visible = remember(current, searchQuery) {
        (current as? LargeContent.Unlocked)?.rows?.let { InsightSort.filter(it, searchQuery) }.orEmpty()
    }
    val largest = remember(current) {
        (current as? LargeContent.Unlocked)?.rows?.maxOfOrNull { it.size?.totalBytes ?: 0L } ?: 0L
    }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.selectionBarHeight + Dimens.gutter),
    ) {
        when (current) {
            LargeContent.NoAccess -> item(key = "access") {
                AccessCard(
                    icon = Icons.Rounded.SdStorage,
                    title = stringResource(R.string.insights_large_access_title),
                    body = stringResource(R.string.insights_large_access_body),
                    onRequestAccess = onRequestAccess,
                )
            }
            LargeContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_large))
            }
            LargeContent.Unavailable -> item(key = "unavailable") {
                EmptyState(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.insights_large_empty_title),
                    body = stringResource(R.string.insights_large_empty_body),
                )
            }
            is LargeContent.Locked -> item(key = "teaser") {
                LockedTeaser(
                    headline = pluralStringResource(
                        R.plurals.insights_large_teaser_headline,
                        current.topCount,
                        current.topCount,
                        formatBytes(context, current.topBytes),
                    ),
                    caption = null,
                    body = stringResource(R.string.insights_large_teaser_body),
                    previewPackages = current.previewPackages,
                    onUnlock = onUnlock,
                )
            }
            is LargeContent.Unlocked -> {
                item(key = "header") {
                    SelectAllHeader(
                        summary = countAndSize(current.rows.size, current.totalBytes),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                        extra = { SizeLegend() },
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    val size = row.size
                    val meta = when {
                        size != null -> stringResource(
                            R.string.insights_size_breakdown,
                            formatBytes(context, size.appBytes),
                            formatBytes(context, size.dataBytes),
                            formatBytes(context, size.cacheBytes),
                        )
                        current.measuring -> stringResource(R.string.insights_size_measuring)
                        // PRD §6 item 10: an unmounted volume is "unavailable", never 0 B.
                        else -> stringResource(R.string.insights_size_unavailable)
                    }
                    AppRow(
                        packageName = row.app.packageName,
                        label = row.app.label,
                        meta = meta,
                        selected = row.app.packageName in selected,
                        onToggleSelected = { viewModel.onToggle(row.app.packageName) },
                        onClick = { onOpenDetails(row.app.packageName) },
                        trailingText = size?.let { formatBytes(context, it.totalBytes) },
                        badge = size?.let { { SizeBar(it, largest) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnusedEmpty(thresholdDays: Int) {
    EmptyState(
        icon = Icons.Rounded.CheckCircle,
        title = pluralStringResource(R.plurals.insights_unused_empty, thresholdDays, thresholdDays),
    )
}

private fun LazyListScope.noMatchItem(show: Boolean, query: String) {
    if (!show) return
    item(key = "no_match") {
        EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.insights_no_match, query.trim()),
        )
    }
}
