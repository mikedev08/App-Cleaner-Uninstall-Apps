package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.ui.component.PremiumCrown
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

private val CardShape = RoundedCornerShape(Dimens.cardRadius)

/**
 * PRD §4 Screen 4 storage card: segmented Apps / Other / Free bar, "96.2 GB of 128 GB used" and
 * "Apps: 21.4 GB". Numbers go through [formatBytes] so they match system Settings (Feature 1).
 *
 * "See full breakdown" is also a funnel entry: no access → the disclosure, access without
 * premium → the paywall, premium → the Large tab (handled by the caller).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StorageCard(
    summary: StorageSummary?,
    isPremium: Boolean,
    hasUsageAccess: Boolean,
    onSeeFullBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, CardShape)
            .padding(start = Dimens.gutter, end = 4.dp, top = 4.dp, bottom = Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_storage_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onSeeFullBreakdown,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_storage_breakdown),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.teal,
                )
                // Crown only where the link actually leads to a premium surface.
                if (hasUsageAccess && !isPremium) {
                    Spacer(Modifier.width(4.dp))
                    PremiumCrown(size = 14.dp)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(end = Dimens.gutter - 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                summary == null -> {
                    // Measured off the main thread; a quiet placeholder for the first frame.
                    StorageBar(apps = 0f, other = 0f)
                }
                !summary.isKnown -> Text(
                    text = stringResource(R.string.home_storage_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                else -> {
                    Text(
                        text = stringResource(
                            R.string.home_storage_used,
                            formatBytes(context, summary.usedBytes),
                            formatBytes(context, summary.totalBytes),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    val total = summary.totalBytes.toFloat()
                    StorageBar(apps = summary.appsBytes / total, other = summary.otherBytes / total)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gutter),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LegendItem(colors.storageApps, stringResource(R.string.home_storage_apps, formatBytes(context, summary.appsBytes)), emphasized = true)
                        LegendItem(colors.storageOther, stringResource(R.string.home_storage_other, formatBytes(context, summary.otherBytes)))
                        LegendItem(colors.storageFree, stringResource(R.string.home_storage_free, formatBytes(context, summary.freeBytes)))
                    }
                }
            }
        }
    }
}

/** Fractions of total capacity; whatever remains is Free. Mirrors in RTL with the Row. */
@Composable
private fun StorageBar(apps: Float, other: Float) {
    val colors = AppTheme.colors
    val appsWeight = apps.coerceIn(0f, 1f)
    val otherWeight = other.coerceIn(0f, 1f - appsWeight)
    val freeWeight = 1f - appsWeight - otherWeight
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(colors.storageFree),
    ) {
        if (appsWeight > 0f) Box(Modifier.weight(appsWeight).fillMaxHeight().background(colors.storageApps))
        if (otherWeight > 0f) Box(Modifier.weight(otherWeight).fillMaxHeight().background(colors.storageOther))
        if (freeWeight > 0f) Spacer(Modifier.weight(freeWeight))
    }
}

@Composable
private fun LegendItem(color: Color, text: String, emphasized: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .border(Dimens.hairline, AppTheme.colors.border, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
            color = if (emphasized) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
        )
    }
}

/** "All apps" · "Unused" (crown) · "Large" (crown). Crowns disappear once premium is active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTabRow(
    selected: HomeTab,
    isPremium: Boolean,
    onSelected: (HomeTab) -> Unit,
) {
    val colors = AppTheme.colors
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = colors.background,
        contentColor = colors.teal,
        divider = { HorizontalDivider(thickness = Dimens.hairline, color = colors.border) },
    ) {
        HomeTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                selectedContentColor = colors.teal,
                unselectedContentColor = colors.textSecondary,
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(tab.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (tab != HomeTab.ALL && !isPremium) PremiumCrown(size = 14.dp)
                    }
                },
            )
        }
    }
}

private fun HomeTab.labelRes(): Int = when (this) {
    HomeTab.ALL -> R.string.home_tab_all
    HomeTab.UNUSED -> R.string.home_tab_unused
    HomeTab.LARGE -> R.string.home_tab_large
}

/** PRD §6 item 18: the one-time "premium ended" explanation, dismissible. */
@Composable
internal fun PremiumEndedBanner(
    onRenew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, CardShape)
            .padding(start = Dimens.gutter, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumCrown(size = 20.dp)
        Spacer(Modifier.width(Dimens.gutterSmall))
        Text(
            text = stringResource(R.string.home_premium_ended),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        TextButton(onClick = onRenew) {
            Text(stringResource(R.string.home_premium_renew), color = colors.teal, style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_action_dismiss), tint = colors.textSecondary)
        }
    }
}

/** PRD §6 item 11: shown when the scan saw fewer than five user apps. */
@Composable
internal fun InventoryIncompleteCard(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, CardShape)
            .padding(Dimens.gutter),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_incomplete_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.home_incomplete_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
