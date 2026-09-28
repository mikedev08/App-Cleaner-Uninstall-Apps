package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.EmptyState
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The All tab (PRD §4 Screen 4, Feature 1): every user app as a 72dp row with a stable key, so a
 * 500-app list stays smooth (§6 item 8). Row body opens details, the checkbox selects. Pull to
 * refresh forces a full rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllAppsTab(
    state: HomeUiState,
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
                containerColor = colors.surface,
                color = colors.teal,
            )
        },
    ) {
        val monthYear = rememberMonthYearFormat()
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
            } else {
                item(key = "header", contentType = "header") {
                    ListHeader(
                        count = state.rows.size,
                        allSelected = state.allVisibleSelected,
                        onSelectAll = onSelectAll,
                        onDeselectAll = onDeselectAll,
                    )
                }
                items(state.rows, key = { it.packageName }, contentType = { "app" }) { row ->
                    AppListRow(
                        row = row,
                        selected = row.packageName in state.selected,
                        sortOrder = state.sortOrder,
                        usageKnown = state.hasUsageAccess,
                        monthYear = monthYear,
                        onToggleSelected = onToggleSelected,
                        onOpenDetails = onOpenDetails,
                    )
                }
            }
        }
    }
}

/**
 * One row. The metadata line follows the sort so the row explains its own position:
 * "248 MB · Installed Mar 2024", "· Updated …" or "· Opened …".
 */
@Composable
private fun AppListRow(
    row: HomeAppRow,
    selected: Boolean,
    sortOrder: SortOrder,
    usageKnown: Boolean,
    monthYear: (Long) -> String,
    onToggleSelected: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val context = LocalContext.current
    val detail = when (sortOrder) {
        SortOrder.LAST_UPDATED -> stringResource(R.string.home_row_updated, monthYear(row.app.lastUpdateTime))
        // Without Usage Access there is no usage data at all, so claiming "no recent use" would lie.
        SortOrder.LAST_USED -> if (!usageKnown) {
            stringResource(R.string.home_row_installed, monthYear(row.app.firstInstallTime))
        } else row.lastUsedAt?.let { stringResource(R.string.home_row_opened, monthYear(it)) }
            ?: stringResource(R.string.home_row_not_opened)
        else -> stringResource(R.string.home_row_installed, monthYear(row.app.firstInstallTime))
    }
    AppRow(
        packageName = row.packageName,
        label = row.app.label,
        meta = stringResource(R.string.home_row_meta, formatBytes(context, row.sizeBytes), detail),
        selected = selected,
        onToggleSelected = { onToggleSelected(row.packageName) },
        onClick = { onOpenDetails(row.packageName) },
    )
}

/** "142 apps" and the Select all / Deselect all toggle for the *visible* rows. */
@Composable
private fun ListHeader(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = Dimens.gutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.home_list_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = if (allSelected) onDeselectAll else onSelectAll) {
            Text(
                text = stringResource(if (allSelected) R.string.home_deselect_all else R.string.home_select_all),
                style = MaterialTheme.typography.labelLarge,
                color = colors.teal,
            )
        }
    }
}

/** PRD §4 Screen 4: grey shimmer rows while the first-ever scan runs. */
@Composable
private fun ShimmerList(contentPadding: PaddingValues) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 850), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(top = 8.dp),
    ) {
        repeat(SHIMMER_ROWS) { ShimmerRow(alpha) }
    }
}

@Composable
private fun ShimmerRow(alpha: State<Float>) {
    val block = AppTheme.colors.surfaceMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.appRowHeight)
            .padding(horizontal = Dimens.gutter)
            .graphicsLayer { this.alpha = alpha.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.appIconSize).clip(RoundedCornerShape(10.dp)).background(block))
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(block))
        }
        Spacer(Modifier.width(Dimens.gutterSmall))
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(block))
    }
}

private const val SHIMMER_ROWS = 9
