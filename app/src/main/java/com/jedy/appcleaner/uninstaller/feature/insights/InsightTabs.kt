package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.feature.apps.QuietChip

/** Room under the last row for the floating Selection Bar, when the caller passes no padding. */
private val DefaultContentPadding = PaddingValues(bottom = Dimens.selectionBarHeight + Dimens.gutter)

/**
 * CONTRACT (frozen signatures; [contentPadding] is an optional addition). PRD §4 Screens 5–6.
 * The Apps screen embeds these as the Unused, Large and Cache filter bodies. They read/write
 * SelectionStore directly.
 *
 * Each is a LazyColumn: give it bounded height (e.g. `Modifier.weight(1f)`), never a
 * verticalScroll parent. [contentPadding]'s bottom should clear the navigation bar and, while it
 * floats, the Selection Bar.
 *
 * @param searchQuery the Apps screen's inline search text; filter rows by label and package name.
 * @param onRequestAccess open the Usage Access disclosure.
 * @param onUnlock open the paywall (Unused only: Large and Cache are free and have no lock).
 * @param onOpenDetails open the App Details sheet for a package.
 */
@Composable
fun UnusedTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onUnlock: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = DefaultContentPadding,
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
        contentPadding = contentPadding,
    ) {
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
                        caption = pluralStringResource(R.plurals.insights_unused_teaser_caption, days, days),
                        loss = stringResource(R.string.insights_unused_teaser_loss, formatBytes(context, content.totalBytes)),
                        body = stringResource(R.string.insights_unused_teaser_body),
                        preview = content.preview,
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

/**
 * The Large filter (free): exactly the apps `LargeApps.isLarge` counts on Home and Scan, with
 * their stacked App / Data / Cache bars and a legend in the summary. Without Usage Access the same
 * apps are listed by APK size, under a prompt to allow access for the full sizes.
 */
@Composable
fun LargeTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = DefaultContentPadding,
) {
    val viewModel: LargeTabViewModel = hiltViewModel()
    val content by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val current = content
    val visible = remember(current, searchQuery) {
        (current as? LargeContent.Listed)?.rows?.let { InsightSort.filter(it, searchQuery) }.orEmpty()
    }
    val largest = remember(current) {
        (current as? LargeContent.Listed)?.rows?.maxOfOrNull { it.bytes }?.takeIf { it > 0 } ?: 1L
    }
    val context = LocalContext.current
    val threshold = formatBytes(context, LargeApps.THRESHOLD_BYTES)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        when (current) {
            // Large never asks for access instead of its list; kept for the shared shape.
            LargeContent.NoAccess -> largeAccessItem(onRequestAccess)
            LargeContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_large), Modifier.animateItem())
            }
            LargeContent.Unavailable -> unavailableItem()
            is LargeContent.Listed -> if (current.rows.isEmpty()) {
                if (current.needsAccess) largeAccessItem(onRequestAccess)
                item(key = "empty") { LargeEmpty(threshold, Modifier.animateItem()) }
            } else {
                // APK sizes only: offer the full sizes above the (same) list.
                if (current.needsAccess) largeAccessItem(onRequestAccess)
                item(key = "summary") {
                    val count = rememberCountUp(current.rows.size.toLong()).toInt()
                    val bytes = rememberCountUp(current.totalBytes)
                    SummaryCard(
                        number = countAndSize(count, bytes),
                        caption = stringResource(R.string.insights_large_caption, threshold),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                        modifier = Modifier.animateItem(),
                        extra = current.breakdown?.let { breakdown ->
                            {
                                Spacer(Modifier.height(Dimens.space16))
                                StackedSizeBar(breakdown, lengthFraction = 1f)
                                Spacer(Modifier.height(Dimens.space12))
                                BreakdownLegend(breakdown)
                            }
                        },
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    LargeAppRow(
                        row = row,
                        measuring = current.measuring,
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

/**
 * The Cache filter (design review §2.6): apps by cache size, the one part of an app's footprint
 * a user can reclaim without uninstalling. It replaced the Large tab's Total / Cache toggle. Free
 * like Large; without Usage Access it asks for access (never the paywall). It shares
 * [LargeTabViewModel].
 */
@Composable
fun CacheTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = DefaultContentPadding,
) {
    val viewModel: LargeTabViewModel = hiltViewModel()
    val content by viewModel.cacheState.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val current = content
    val visible = remember(current, searchQuery) {
        (current as? LargeContent.Listed)?.rows?.let { InsightSort.filter(it, searchQuery) }.orEmpty()
    }
    val largest = remember(current) {
        (current as? LargeContent.Listed)?.rows?.maxOfOrNull { it.size?.cacheBytes ?: 0L }?.takeIf { it > 0 } ?: 1L
    }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        when (current) {
            LargeContent.NoAccess -> item(key = "access") {
                AccessCard(
                    icon = Icons.Rounded.SdStorage,
                    title = stringResource(R.string.insights_cache_access_title),
                    body = stringResource(R.string.insights_large_access_body),
                    onRequestAccess = onRequestAccess,
                    modifier = Modifier.animateItem(),
                )
            }
            LargeContent.Loading -> item(key = "loading") {
                InsightsLoading(stringResource(R.string.insights_loading_large), Modifier.animateItem())
            }
            LargeContent.Unavailable -> unavailableItem()
            is LargeContent.Listed -> if (current.rows.isEmpty()) {
                item(key = "empty") { CacheEmpty(Modifier.animateItem()) }
            } else {
                item(key = "summary") {
                    val bytes = rememberCountUp(current.totalBytes)
                    SummaryCard(
                        number = formatBytes(context, bytes),
                        caption = pluralStringResource(R.plurals.insights_cache_caption, current.rows.size, current.rows.size),
                        visiblePackages = visible.map { it.app.packageName },
                        selected = selected,
                        onSelectAll = viewModel::onSelectAll,
                        modifier = Modifier.animateItem(),
                        extra = { CacheHint() },
                    )
                }
                noMatchItem(visible.isEmpty(), searchQuery)
                items(visible, key = { it.app.packageName }) { row ->
                    CacheAppRow(
                        row = row,
                        largestCacheBytes = largest,
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

/**
 * An unused app: when it was last opened (meta), how long it has sat idle (a quiet chip, never
 * clipped) and how much it holds (size bar, Large-coloured when `LargeApps.isLarge`).
 */
@Composable
private fun UnusedAppRow(
    row: UnusedRow,
    dates: InsightsDateFormatter,
    now: Long,
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
        chips = { QuietChip(idle, icon = Icons.Outlined.Schedule) },
    )
}

/**
 * A large app: the stacked App / Data / Cache bar against the largest app and the total in
 * neutral text. An unmeasured app (Large by its APK size alone) gets a plain bar and says so.
 */
@Composable
private fun LargeAppRow(
    row: LargeRow,
    measuring: Boolean,
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
        else -> stringResource(R.string.apps_details_apk_caption)
    }
    AppRow(
        packageName = row.app.packageName,
        label = row.app.label,
        meta = meta,
        modifier = modifier,
        selected = selected,
        onToggleSelected = onToggle,
        onClick = onOpenDetails,
        trailingText = formatBytes(context, row.bytes),
        large = true,
        sizeFraction = row.bytes.toFloat() / largestBytes,
        bar = size?.let {
            { StackedSizeBar(size = it, lengthFraction = row.bytes.toFloat() / largestBytes) }
        },
    )
}

/** A Cache-filter row: the cache as the size, the app's total as context. */
@Composable
private fun CacheAppRow(
    row: LargeRow,
    largestCacheBytes: Long,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cache = row.size?.cacheBytes ?: 0L
    AppRow(
        packageName = row.app.packageName,
        label = row.app.label,
        meta = stringResource(R.string.insights_cache_row_meta, formatBytes(context, row.bytes)),
        modifier = modifier,
        selected = selected,
        onToggleSelected = onToggle,
        onClick = onOpenDetails,
        trailingText = formatBytes(context, cache),
        sizeFraction = cache.toFloat() / largestCacheBytes,
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

@Composable
private fun LargeEmpty(threshold: String, modifier: Modifier = Modifier) {
    CelebrationCard(
        title = stringResource(R.string.insights_large_none_title),
        body = stringResource(R.string.insights_large_none_body, threshold),
        modifier = modifier,
    )
}

@Composable
private fun CacheEmpty(modifier: Modifier = Modifier) {
    CelebrationCard(
        title = stringResource(R.string.insights_cache_none_title),
        body = stringResource(R.string.insights_unused_empty_body),
        modifier = modifier,
    )
}

/** Large's Usage Access prompt: the full app + data + cache sizes need it (never Pro). */
private fun LazyListScope.largeAccessItem(onRequestAccess: () -> Unit) {
    item(key = "access") {
        AccessCard(
            icon = Icons.Rounded.SdStorage,
            title = stringResource(R.string.insights_large_access_title),
            body = stringResource(R.string.insights_large_access_body),
            onRequestAccess = onRequestAccess,
            modifier = Modifier.animateItem(),
        )
    }
}

private fun LazyListScope.unavailableItem() {
    item(key = "unavailable") {
        EmptyState(
            icon = Icons.Rounded.Storage,
            title = stringResource(R.string.insights_large_empty_title),
            body = stringResource(R.string.insights_large_empty_body),
            modifier = Modifier.animateItem(),
        )
    }
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
