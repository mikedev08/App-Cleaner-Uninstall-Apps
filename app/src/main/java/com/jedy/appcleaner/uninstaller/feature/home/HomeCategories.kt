package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.LockIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.PlaceholderLine
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult

/** The three categories (design review §2.6 vocabulary), each opening that Apps filter. */
enum class Category(val tab: HomeTab) {
    UNUSED(HomeTab.UNUSED),
    LARGE(HomeTab.LARGE),
    CACHE(HomeTab.CACHE),
}

/** What one category row shows. */
sealed interface CategoryValue {
    /** Numbers not read yet: the row keeps its final height with placeholder lines. */
    data object Loading : CategoryValue

    /** Needs Usage Access: the row leads to the disclosure instead of showing a misleading zero. */
    data object NeedsAccess : CategoryValue

    /**
     * Nothing to add: a quiet single line, not tappable. [countedAbove] = the category does have
     * apps, but every one is already counted in a row above (e.g. every large app is unused).
     */
    data class Empty(val countedAbove: Boolean) : CategoryValue

    /**
     * [count] apps taking [bytes]. [more] = part of the category is already counted in a row
     * above, so this row reads "2 more apps" / "40 other apps" and the rows still add up.
     */
    data class Value(val count: Int, val bytes: Long, val more: Boolean) : CategoryValue
}

data class CategoryRow(val category: Category, val value: CategoryValue, val locked: Boolean)

/**
 * The category rows shared by Home and the Scan result, so both screens show the same three
 * numbers and those numbers add up to the "You can free up X" headline (see [ScanResult]).
 */
object HomeCategories {

    /**
     * Pro-only Apps filters: the row shows one lock at its trailing edge. Keep in step with the
     * Apps screen's own gating.
     */
    val proOnly: Set<Category> = setOf(Category.UNUSED, Category.LARGE, Category.CACHE)

    fun rows(summary: ScanResult?, isPremium: Boolean): List<CategoryRow> = Category.entries.map { category ->
        val value = if (summary == null) CategoryValue.Loading else valueOf(category, summary)
        CategoryRow(category, value, locked = !isPremium && category in proOnly && value is CategoryValue.Value)
    }

    private fun valueOf(category: Category, r: ScanResult): CategoryValue = when (category) {
        Category.UNUSED -> when {
            !r.hasUsageAccess -> CategoryValue.NeedsAccess
            r.unusedCount == 0 -> CategoryValue.Empty(countedAbove = false)
            else -> CategoryValue.Value(r.unusedCount, r.unusedBytes, more = false)
        }
        Category.LARGE -> when {
            r.largeOnlyCount == 0 -> CategoryValue.Empty(countedAbove = r.largeCount > 0)
            else -> CategoryValue.Value(r.largeOnlyCount, r.largeOnlyBytes, more = r.largeAlsoUnusedCount > 0)
        }
        Category.CACHE -> when {
            !r.hasUsageAccess -> CategoryValue.NeedsAccess
            r.keptCacheBytes <= 0 -> CategoryValue.Empty(countedAbove = r.cacheBytes > 0)
            else -> CategoryValue.Value(r.keptCacheAppCount, r.keptCacheBytes, more = r.cacheBytes > r.keptCacheBytes)
        }
    }

    /**
     * Where Home's "Review" goes: the first filter with something in it for premium, the free
     * All list (everything a free user can act on) otherwise.
     */
    fun reviewTab(result: ScanResult, isPremium: Boolean): HomeTab = when {
        !isPremium -> HomeTab.ALL
        result.hasUsageAccess && result.unusedCount > 0 -> HomeTab.UNUSED
        result.largeOnlyCount > 0 -> HomeTab.LARGE
        result.hasUsageAccess && result.keptCacheBytes > 0 -> HomeTab.CACHE
        else -> HomeTab.ALL
    }
}

/**
 * The grouped card: Unused · Large · Cache, one row each, "count" under the title, the size in
 * textPrimary at the end, then a chevron — or a single lock when the filter is Pro.
 */
@Composable
internal fun CategoryCard(
    rows: List<CategoryRow>,
    onOpen: (Category) -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Dimens.space4)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(start = RowPadding + IconSize + Dimens.space12, end = RowPadding)
                        .fillMaxWidth()
                        .height(Dimens.hairline)
                        .background(AppTheme.colors.border),
                )
            }
            CategoryRowItem(
                row = row,
                onClick = when (row.value) {
                    is CategoryValue.Value -> ({ onOpen(row.category) })
                    CategoryValue.NeedsAccess -> onAllowAccess
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun CategoryRowItem(row: CategoryRow, onClick: (() -> Unit)?) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val value = row.value
    val quiet = value is CategoryValue.Empty
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (quiet) QuietRowHeight else RowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = RowPadding, vertical = Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (quiet) {
            Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
                Icon(row.category.icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
        } else {
            IconBadge(icon = row.category.icon, size = IconSize)
        }
        Spacer(Modifier.width(Dimens.space12))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(row.category.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (quiet) colors.textSecondary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (value) {
                CategoryValue.Loading -> PlaceholderLine(MaterialTheme.typography.bodyMedium, widthFraction = 0.4f)
                CategoryValue.NeedsAccess -> Text(
                    stringResource(R.string.scan_intro_allow),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.positive,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is CategoryValue.Value -> Text(
                    countText(row.category, value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is CategoryValue.Empty -> Unit
            }
        }
        Spacer(Modifier.width(Dimens.space8))
        when (value) {
            CategoryValue.Loading -> PlaceholderLine(
                MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
                widthFraction = 1f,
            )
            is CategoryValue.Empty -> Text(
                stringResource(if (value.countedAbove) R.string.home_cat_counted_above else R.string.home_cat_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
            is CategoryValue.Value -> Text(
                formatBytes(context, value.bytes),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
            CategoryValue.NeedsAccess -> Unit
        }
        if (!quiet) {
            Spacer(Modifier.width(Dimens.space4))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when {
                    value == CategoryValue.Loading -> Unit
                    row.locked -> LockIcon(size = 18.dp)
                    else -> Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun countText(category: Category, value: CategoryValue.Value): String {
    val n = value.count
    return when {
        category == Category.CACHE && value.more -> pluralStringResource(R.plurals.home_cat_other_apps, n, n)
        value.more -> pluralStringResource(R.plurals.home_cat_more_apps, n, n)
        else -> pluralStringResource(R.plurals.home_list_count, n, n)
    }
}

private val Category.icon: ImageVector
    get() = when (this) {
        Category.UNUSED -> Icons.Outlined.HourglassEmpty
        Category.LARGE -> Icons.Outlined.Inventory2
        Category.CACHE -> Icons.Outlined.Layers
    }

private val Category.titleRes: Int
    get() = when (this) {
        Category.UNUSED -> R.string.apps_tab_unused
        Category.LARGE -> R.string.apps_tab_large
        Category.CACHE -> R.string.apps_tab_cache
    }

/**
 * A text-only action ("Scan again", "Review all apps"): 48dp tall, green label, no fill, so it
 * never competes with the one [PrimaryButton][com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton].
 */
@Composable
internal fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.positive,
) {
    Box(
        modifier
            .heightIn(min = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val RowPadding = Dimens.space16
private val IconSize = 36.dp
private val RowHeight = 64.dp
private val QuietRowHeight = 52.dp
