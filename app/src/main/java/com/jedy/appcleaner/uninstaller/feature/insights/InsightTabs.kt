package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences

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
    val visible = remember(content, searchQuery) {
        (content as? UnusedContent.Unlocked)?.rows?.let { InsightSort.filter(it, searchQuery) }.orEmpty()
    }
    val largest = remember(content) {
        (content as? UnusedContent.Unlocked)?.rows?.maxOfOrNull { it.bytes }?.takeIf { it > 0 } ?: 1L
    }
    val context = LocalContext.current
    val days = state.thresholdDays

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.selectionBarHeight + Dimens.gutter),
    ) {
        if (content != UnusedContent.NoAccess) {
            item(key = "threshold") {
                ThresholdControl(days, viewModel::onThresholdSelected, Modifier.animateItem())
            }
        }
        when (content) {
            UnusedContent.NoAccess -> item(key = "access") {
                AccessCard(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.insights_unused_access_title),
                    body = stringResource(R.string.insights_unused_access_body),
                    onRequestAccess = onRequestAccess,
                    modifier = Modifier.animateItem(),
                )
            }
            UnusedContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_unused), Modifier.animateItem())
            }
            is UnusedContent.Locked -> if (content.count == 0) {
                item(key = "empty") { UnusedEmpty(days, Modifier.animateItem()) }
            } else {
                item(key = "teaser") {
                    val count = rememberCountUp(content.count.toLong()).toInt()
                    val bytes = rememberCountUp(content.totalBytes)
                    LockedTeaser(
                        number = countAndSize(count, bytes),
                        severity = InsightSeverity.unused(content.totalBytes, content.deviceTotalBytes, days, content.now),
                        caption = pluralStringResource(R.plurals.insights_unused_teaser_caption, days, days),
                        loss = stringResource(R.string.insights_unused_teaser_loss, formatBytes(context, content.totalBytes)),
                        body = stringResource(R.string.insights_unused_teaser_body),
                        preview = content.preview,
                        deviceTotalBytes = content.deviceTotalBytes,
                        onUnlock = onUnlock,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            is UnusedContent.Unlocked -> if (content.rows.isEmpty()) {
                item(key = "empty") { UnusedEmpty(days, Modifier.animateItem()) }
            } else {
                item(key = "summary") {
                    val count = rememberCountUp(content.rows.size.toLong()).toInt()
                    val bytes = rememberCountUp(content.totalBytes)
                    SummaryCard(
                        number = countAndSize(count, bytes),
                        severity = InsightSeverity.unused(content.totalBytes, content.deviceTotalBytes, days, content.now),
                        caption = pluralStringResource(R.plurals.insights_unused_teaser_caption, days, days),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                        modifier = Modifier.animateItem(),
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    UnusedAppRow(
                        row = row,
                        dates = dates,
                        now = content.now,
                        deviceTotalBytes = content.deviceTotalBytes,
                        largestBytes = largest,
                        selected = row.app.packageName in selected,
                        onToggle = { viewModel.onToggle(row.app.packageName) },
                        onOpenDetails = { onOpenDetails(row.app.packageName) },
                        modifier = Modifier.animateItem(),
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
        (current as? LargeContent.Unlocked)?.rows?.maxOfOrNull { it.size?.totalBytes ?: 0L }?.takeIf { it > 0 } ?: 1L
    }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.selectionBarHeight + Dimens.gutter),
    ) {
        when (current) {
            LargeContent.NoAccess -> item(key = "access") {
                AccessCard(
                    icon = Icons.Rounded.SdStorage,
                    title = stringResource(R.string.insights_large_access_title),
                    body = stringResource(R.string.insights_large_access_body),
                    onRequestAccess = onRequestAccess,
                    modifier = Modifier.animateItem(),
                )
            }
            LargeContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_large), Modifier.animateItem())
            }
            LargeContent.Unavailable -> item(key = "unavailable") {
                EmptyState(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.insights_large_empty_title),
                    body = stringResource(R.string.insights_large_empty_body),
                    modifier = Modifier.animateItem(),
                )
            }
            is LargeContent.Locked -> item(key = "teaser") {
                val bytes = rememberCountUp(current.topBytes)
                LockedTeaser(
                    number = formatBytes(context, bytes),
                    severity = SeverityRules.appSize(current.topBytes, current.deviceTotalBytes),
                    caption = pluralStringResource(R.plurals.insights_large_teaser_caption, current.topCount, current.topCount),
                    loss = percentOf(current.topBytes, current.deviceTotalBytes)?.let {
                        stringResource(R.string.insights_large_teaser_loss, it)
                    },
                    body = stringResource(R.string.insights_large_teaser_body),
                    preview = current.preview,
                    deviceTotalBytes = current.deviceTotalBytes,
                    onUnlock = onUnlock,
                    modifier = Modifier.animateItem(),
                )
            }
            is LargeContent.Unlocked -> {
                item(key = "summary") {
                    val bytes = rememberCountUp(current.totalBytes)
                    val measured = remember(current) { current.rows.count { it.size != null } }
                    SummaryCard(
                        number = formatBytes(context, bytes),
                        severity = SeverityRules.appSize(current.totalBytes, current.deviceTotalBytes),
                        caption = pluralStringResource(R.plurals.insights_large_total_caption, measured, measured),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                        modifier = Modifier.animateItem(),
                        extra = current.breakdown?.let { breakdown ->
                            {
                                Spacer(Modifier.height(16.dp))
                                StackedSizeBar(breakdown, lengthFraction = 1f)
                                Spacer(Modifier.height(12.dp))
                                BreakdownLegend(breakdown)
                            }
                        },
                    )
                }
                if (current.cache.apps > 0) {
                    item(key = "cache") {
                        CacheCallout(
                            cache = current.cache,
                            deviceTotalBytes = current.deviceTotalBytes,
                            onSortByCache = if (current.sort == LargeSort.CACHE) null else {
                                { viewModel.onSortSelected(LargeSort.CACHE) }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "sort") {
                    PillSegmented(
                        options = listOf(stringResource(R.string.insights_sort_total), stringResource(R.string.insights_legend_cache)),
                        selectedIndex = current.sort.ordinal,
                        onSelect = { viewModel.onSortSelected(LargeSort.entries[it]) },
                        modifier = Modifier.animateItem().padding(horizontal = Dimens.gutter, vertical = 8.dp),
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    LargeAppRow(
                        row = row,
                        measuring = current.measuring,
                        deviceTotalBytes = current.deviceTotalBytes,
                        largestBytes = largest,
                        selected = row.app.packageName in selected,
                        onToggle = { viewModel.onToggle(row.app.packageName) },
                        onOpenDetails = { onOpenDetails(row.app.packageName) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** PRD §4 Screen 5: 30 · 60 · 90 days. Persisted, and shared with the reminder worker. */
@Composable
private fun ThresholdControl(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val thresholds = AppPreferences.UNUSED_THRESHOLDS
    PillSegmented(
        options = thresholds.map { pluralStringResource(R.plurals.insights_days, it, it) },
        selectedIndex = thresholds.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelected(thresholds[it]) },
        modifier = modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp),
    )
}

/**
 * An unused app: when it was last opened (meta), how long it has sat idle (chip, coloured by
 * SeverityRules.idle) and how much it holds (size bar and bold size, by SeverityRules.appSize).
 */
@Composable
private fun UnusedAppRow(
    row: UnusedRow,
    dates: InsightsDateFormatter,
    now: Long,
    deviceTotalBytes: Long,
    largestBytes: Long,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastUsedAt = row.lastUsedAt
    val meta = if (lastUsedAt != null) {
        stringResource(R.string.insights_last_opened_date, remember(lastUsedAt, dates) { dates.monthYear(lastUsedAt) })
    } else {
        stringResource(R.string.insights_not_opened_since, remember(row.notOpenedSince, dates) { dates.monthYear(row.notOpenedSince) })
    }
    val idle = if (lastUsedAt != null) {
        stringResource(R.string.insights_idle_for, remember(lastUsedAt, now, dates) { dates.duration(lastUsedAt, now) })
    } else {
        stringResource(R.string.insights_no_use_on_record)
    }
    AppRow(
        packageName = row.app.packageName,
        label = row.app.label,
        meta = meta,
        modifier = modifier,
        selected = selected,
        onToggleSelected = onToggle,
        onClick = onOpenDetails,
        trailingText = formatBytes(LocalContext.current, row.bytes),
        large = LargeApps.isLarge(row.bytes),
        sizeFraction = row.bytes.toFloat() / largestBytes,
        chips = { SeverityChip(idle, SeverityRules.idle(lastUsedAt, now), icon = Icons.Rounded.Schedule) },
    )
}

/**
 * A large app: the stacked app / data / cache bar against the largest app, the total in its
 * severity colour, and a cache chip when the cache alone is big enough to matter.
 */
@Composable
private fun LargeAppRow(
    row: LargeRow,
    measuring: Boolean,
    deviceTotalBytes: Long,
    largestBytes: Long,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val size = row.size
    val meta = when {
        size != null -> stringResource(
            R.string.insights_size_breakdown,
            formatBytes(context, size.appBytes),
            formatBytes(context, size.dataBytes),
            formatBytes(context, size.cacheBytes),
        )
        measuring -> stringResource(R.string.insights_size_measuring)
        // PRD §6 item 10: an unmounted volume is "unavailable", never 0 B.
        else -> stringResource(R.string.insights_size_unavailable)
    }
    val cacheSeverity = size?.let { InsightSeverity.cache(it.cacheBytes, deviceTotalBytes) } ?: Severity.OK
    val cacheChip: (@Composable () -> Unit)? = if (size != null && cacheSeverity != Severity.OK) {
        { SeverityChip(stringResource(R.string.insights_cache_chip, formatBytes(context, size.cacheBytes)), cacheSeverity) }
    } else {
        null
    }
    AppRow(
        packageName = row.app.packageName,
        label = row.app.label,
        meta = meta,
        modifier = modifier,
        selected = selected,
        onToggleSelected = onToggle,
        onClick = onOpenDetails,
        trailingText = size?.let { formatBytes(context, it.totalBytes) },
        large = size != null && LargeApps.isLarge(size.totalBytes),
        chips = cacheChip,
        bar = size?.let {
            {
                StackedSizeBar(
                    size = it,
                    lengthFraction = it.totalBytes.toFloat() / largestBytes,
                )
            }
        },
    )
}

@Composable
private fun UnusedEmpty(thresholdDays: Int, modifier: Modifier = Modifier) {
    CelebrationCard(
        title = pluralStringResource(R.plurals.insights_unused_empty, thresholdDays, thresholdDays),
        body = stringResource(R.string.insights_unused_empty_body),
        modifier = modifier,
    )
}

private fun LazyListScope.noMatchItem(show: Boolean, query: String) {
    if (!show) return
    item(key = "no_match") {
        EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.insights_no_match, query.trim()),
            modifier = Modifier.animateItem(),
        )
    }
}
