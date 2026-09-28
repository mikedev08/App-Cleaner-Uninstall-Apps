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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity

/**
 * The All tab (PRD §4 Screen 4, Feature 1): every user app as a card row with a stable key and
 * `animateItem`, so a 500-app list stays smooth (§6 item 8) and re-sorts glide instead of jump.
 * Sorted by size, the red and amber apps get their own "Taking the most space" section with its
 * real total — the list itself makes the case for cleaning up. Pull to refresh forces a rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllAppsTab(
    state: AppsUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
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
        val row: @Composable (AppsRow) -> Unit = { item ->
            AppListRow(
                row = item,
                selected = item.packageName in state.selected,
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
            modifier = Modifier.fillMaxSize(),
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
                    count = state.rows.size,
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
                item(key = "section_hogs", contentType = "section") {
                    SectionTitle(
                        title = stringResource(R.string.apps_section_hogs),
                        subtitle = stringResource(
                            R.string.apps_summary,
                            pluralStringResource(R.plurals.home_list_count, sections.hogs.size, sections.hogs.size),
                            formatBytes(LocalContext.current, sections.hogBytes),
                        ),
                        emphasized = true,
                        modifier = Modifier.animateItem(),
                    )
                }
                rows(sections.hogs, row)
                if (sections.rest.isNotEmpty()) {
                    item(key = "section_rest", contentType = "section") {
                        SectionTitle(
                            title = stringResource(R.string.apps_section_rest),
                            subtitle = null,
                            emphasized = false,
                            modifier = Modifier.animateItem().padding(top = 12.dp),
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
 * Mar 2024", "Updated …", "Opened …"); the size sits on the right in severity colour.
 */
@Composable
private fun AppListRow(
    row: AppsRow,
    selected: Boolean,
    sortOrder: SortOrder,
    usageKnown: Boolean,
    monthYear: (Long) -> String,
    onToggleSelected: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val context = LocalContext.current
    val meta = when (sortOrder) {
        SortOrder.LAST_UPDATED -> stringResource(R.string.home_row_updated, monthYear(row.app.lastUpdateTime))
        // Without Usage Access there is no usage data at all, so claiming "no recent use" would lie.
        SortOrder.LAST_USED -> if (!usageKnown) {
            stringResource(R.string.home_row_installed, monthYear(row.app.firstInstallTime))
        } else row.lastUsedAt?.let { stringResource(R.string.home_row_opened, monthYear(it)) }
            ?: stringResource(R.string.home_row_not_opened)
        else -> stringResource(R.string.home_row_installed, monthYear(row.app.firstInstallTime))
    }
    val hog = row.severity == Severity.DANGER
    val idle = row.idle
    AppRow(
        packageName = row.packageName,
        label = row.app.label,
        meta = meta,
        selected = selected,
        onToggleSelected = { onToggleSelected(row.packageName) },
        onClick = { onOpenDetails(row.packageName) },
        trailingText = formatBytes(context, row.sizeBytes),
        severity = row.severity,
        sizeFraction = row.sizeFraction,
        chips = if (hog || idle != null) {
            {
                if (hog) SeverityChip(stringResource(R.string.apps_chip_space_hog), Severity.DANGER, icon = Icons.Rounded.LocalFireDepartment)
                if (idle != null) SeverityChip(idleText(idle), idle.severity, icon = Icons.Rounded.Schedule)
            }
        } else {
            null
        },
    )
}

@Composable
internal fun idleText(idle: IdleChip): String =
    if (idle.overAYear) stringResource(R.string.apps_chip_idle_year)
    else pluralStringResource(R.plurals.apps_chip_idle_months, idle.months, idle.months)

@Composable
private fun SectionTitle(title: String, subtitle: String?, emphasized: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emphasized) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(colors.danger))
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.danger)
        }
    }
}

/** "142 apps" and a pill Select all / Deselect all for the *visible* rows. */
@Composable
private fun ListHeader(
    count: Int,
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
        Text(
            text = pluralStringResource(R.plurals.home_list_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
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
            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .height(Dimens.appRowHeight)
            .padding(horizontal = 14.dp),
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
