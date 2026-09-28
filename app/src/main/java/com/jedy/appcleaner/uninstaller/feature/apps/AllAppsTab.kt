package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRowDefaults
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The All tab (PRD §4 Screen 4, Feature 1): every user app as a card row with a stable key and
 * `animateItem`, so a 500-app list stays smooth (§6 item 8) and re-sorts glide instead of jump.
 * Sorted by size, the Large apps (`LargeApps.isLarge`, the rule Home and Scan count with) get
 * their own "Large" section with its real total. Pull to refresh forces a rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllAppsTab(
    state: AppsUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    headerConnection: NestedScrollConnection,
    onRefresh: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    if (state.isInitialLoading) {
        ShimmerList(contentPadding)
        return
    }
    val colors = AppTheme.colors
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.surfaceElevated,
                color = colors.accent,
            )
        },
    ) {
        val monthYear = rememberMonthYearFormat()
        val sectioned = state.sections != null
        val row: @Composable (AppsRow) -> Unit = { item ->
            AppListRow(
                row = item,
                selected = item.packageName in state.selected,
                // The "Large" heading already says it; a chip on every row under it is noise.
                showLargeChip = !sectioned,
                sortOrder = state.sortOrder,
                usageKnown = state.hasUsageAccess,
                monthYear = monthYear,
                onToggleSelected = onToggleSelected,
                onOpenDetails = onOpenDetails,
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            // Between the list and the pull-to-refresh box, so the title comes back before the
            // refresh indicator takes the pull.
            modifier = Modifier.fillMaxSize().nestedScroll(headerConnection),
        ) {
            if (state.showInventoryIncomplete && state.query.isBlank()) {
                item(key = "incomplete", contentType = "card") {
                    InventoryIncompleteCard(Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp))
                }
            }
            if (state.rows.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    if (state.query.isNotBlank()) {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.home_search_empty, state.query.trim()),
                            body = stringResource(R.string.home_search_empty_body),
                        )
                    } else {
                        EmptyState(icon = Icons.Rounded.Apps, title = stringResource(R.string.home_empty_title))
                    }
                }
                return@LazyColumn
            }
            item(key = "header", contentType = "header") {
                ListHeader(
                    // The title already carries the library count; only a search result needs its own.
                    count = state.rows.size.takeIf { state.query.isNotBlank() },
                    allSelected = state.allVisibleSelected,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    modifier = Modifier.animateItem(),
                )
            }
            val sections = state.sections
            if (sections == null) {
                rows(state.rows, row)
            } else {
                item(key = "section_large", contentType = "section") {
                    SectionTitle(
                        icon = HomeTab.LARGE.icon(),
                        title = stringResource(R.string.apps_section_large),
                        subtitle = stringResource(
                            R.string.apps_summary,
                            pluralStringResource(R.plurals.home_list_count, sections.large.size, sections.large.size),
                            formatBytes(LocalContext.current, sections.largeBytes),
                        ),
                        modifier = Modifier.animateItem(),
                    )
                }
                rows(sections.large, row)
                if (sections.rest.isNotEmpty()) {
                    item(key = "section_rest", contentType = "section") {
                        SectionTitle(
                            icon = HomeTab.ALL.icon(),
                            title = stringResource(R.string.apps_section_rest),
                            subtitle = null,
                            modifier = Modifier.animateItem().padding(top = Dimens.space16),
                        )
                    }
                    rows(sections.rest, row)
                }
            }
        }
    }
}

private fun LazyListScope.rows(rows: List<AppsRow>, row: @Composable (AppsRow) -> Unit) {
    items(rows, key = { it.packageName }, contentType = { "app" }) { item ->
        Box(Modifier.animateItem()) { row(item) }
    }
}

/**
 * One row. The meta line follows the sort so the row explains its own position ("Installed
 * Mar 2024", "Updated …", "Opened …"); the size sits on the right in neutral text and only the
 * bar (and the one chip) carries colour. An unknown install date (preinstalled apps report the
 * epoch) is never printed as "Jan 1970".
 */
@Composable
private fun AppListRow(
    row: AppsRow,
    selected: Boolean,
    showLargeChip: Boolean,
    sortOrder: SortOrder,
    usageKnown: Boolean,
    monthYear: (Long) -> String,
    onToggleSelected: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val context = LocalContext.current
    val installed = installedMeta(row, monthYear)
    val meta = when (sortOrder) {
        SortOrder.LAST_UPDATED -> updatedMeta(row, monthYear)
        // Without Usage Access there is no usage data at all, so claiming "no recent use" would lie.
        SortOrder.LAST_USED -> if (!usageKnown) {
            installed
        } else row.lastUsedAt?.let { stringResource(R.string.home_row_opened, monthYear(it)) }
            ?: stringResource(R.string.home_row_not_opened)
        else -> installed
    }
    val idle = row.idle
    AppRow(
        packageName = row.packageName,
        label = row.app.label,
        meta = meta,
        selected = selected,
        onToggleSelected = { onToggleSelected(row.packageName) },
        onClick = { onOpenDetails(row.packageName) },
        trailingText = formatBytes(context, row.sizeBytes),
        large = row.large,
        sizeFraction = row.sizeFraction,
        // One chip per row: how long it sat unopened beats "Large", which the bar colour shows.
        chips = when {
            idle != null -> {
                { QuietChip(idleText(idle), icon = Icons.Outlined.Schedule) }
            }
            showLargeChip && row.large -> {
                { LargeChip() }
            }
            else -> null
        },
    )
}

@Composable
private fun installedMeta(row: AppsRow, monthYear: (Long) -> String): String = when {
    AppsListLogic.isKnownDate(row.app.firstInstallTime) ->
        stringResource(R.string.home_row_installed, monthYear(row.app.firstInstallTime))
    // Preinstalled: no honest install date, but the last update usually is one.
    AppsListLogic.isKnownDate(row.app.lastUpdateTime) ->
        stringResource(R.string.home_row_updated, monthYear(row.app.lastUpdateTime))
    else -> ""
}

@Composable
private fun updatedMeta(row: AppsRow, monthYear: (Long) -> String): String =
    if (AppsListLogic.isKnownDate(row.app.lastUpdateTime)) {
        stringResource(R.string.home_row_updated, monthYear(row.app.lastUpdateTime))
    } else {
        ""
    }

@Composable
internal fun idleText(idle: IdleChip): String =
    if (idle.overAYear) stringResource(R.string.apps_chip_idle_year_short)
    else pluralStringResource(R.plurals.apps_chip_idle_months_short, idle.months, idle.months)

/**
 * Section heading: the category icon and title, and a neutral "9 apps · 3.1 GB" on one line when
 * they fit; on a narrow phone the figure drops below the title instead. A title too long for the
 * row wraps (never "…").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionTitle(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    FlowRow(
        modifier.fillMaxWidth().padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space8, bottom = Dimens.space8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = Dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Dimens.space8))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** An optional search-result count and a pill Select all / Deselect all for the *visible* rows. */
@Composable
private fun ListHeader(
    count: Int?,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = Dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count != null) {
            Text(
                text = pluralStringResource(R.plurals.home_list_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(if (allSelected) R.string.home_deselect_all else R.string.home_select_all),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accentText,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(colors.accentSurface)
                .clickable(onClick = if (allSelected) onDeselectAll else onSelectAll)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** PRD §6 item 11: shown when the scan saw fewer than five user apps. */
@Composable
private fun InventoryIncompleteCard(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier.fillMaxWidth(), color = colors.warningSurface) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.onWarningSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Dimens.gutterSmall))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_incomplete_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(stringResource(R.string.home_incomplete_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

/** PRD §4 Screen 4: soft card-shaped shimmer rows while the first-ever scan runs. */
@Composable
private fun ShimmerList(contentPadding: PaddingValues) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 850), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(top = 8.dp)) {
        repeat(SHIMMER_ROWS) { ShimmerRow(alpha) }
    }
}

@Composable
private fun ShimmerRow(alpha: State<Float>) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppRowDefaults.ListPadding)
            .graphicsLayer { this.alpha = alpha.value }
            .clip(AppRowDefaults.Shape)
            .background(colors.surface)
            .height(Dimens.appRowHeight)
            .padding(horizontal = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.appIconSize).clip(RoundedCornerShape(14.dp)).background(colors.surfaceMuted))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceMuted))
            Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceMuted))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(colors.surfaceMuted))
    }
}

private const val SHIMMER_ROWS = 9
