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
import androidx.compose.ui.text.style.TextAlign
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

    /** Nothing in the category: a quiet single line, not tappable. */
    data object Empty : CategoryValue

    /** The whole category: [count] apps taking [bytes], the same numbers its Apps filter shows. */
    data class Value(val count: Int, val bytes: Long) : CategoryValue
}

data class CategoryRow(val category: Category, val value: CategoryValue, val locked: Boolean)

/**
 * The category rows shared by Home and the Scan result, so both screens (and the Apps filters)
 * show the same three numbers. Each row is its whole category, so the rows may overlap; the
 * "could be freed" headline counts every app once (see [ScanResult]) and [overlapCount] explains
 * the difference in one line.
 */
object HomeCategories {

    /**
     * Pro-only Apps filters: the row shows one lock at its trailing edge. Only Unused is Pro;
     * Large and Cache are free. Keep in step with the Apps screen's own gating.
     */
    val proOnly: Set<Category> = setOf(Category.UNUSED)

    fun rows(summary: ScanResult?, isPremium: Boolean): List<CategoryRow> = Category.entries.map { category ->
        val value = if (summary == null) CategoryValue.Loading else valueOf(category, summary)
        CategoryRow(category, value, locked = !isPremium && category in proOnly && value is CategoryValue.Value)
    }

    /** Apps in both the Unused and the Large row ("3 apps are both unused and big"); 0 = no note. */
    fun overlapCount(summary: ScanResult?): Int =
        summary?.takeIf { it.hasUsageAccess && it.unusedCount > 0 && it.largeCount > 0 }?.overlapCount ?: 0

    private fun valueOf(category: Category, r: ScanResult): CategoryValue = when (category) {
        Category.UNUSED -> when {
            !r.hasUsageAccess -> CategoryValue.NeedsAccess
            r.unusedCount == 0 -> CategoryValue.Empty
            else -> CategoryValue.Value(r.unusedCount, r.unusedBytes)
        }
        // Free, and never asks for access: without it the sizes are APK sizes (same rule as the filter).
        Category.LARGE -> when {
            r.largeCount == 0 -> CategoryValue.Empty
            else -> CategoryValue.Value(r.largeCount, r.largeBytes)
        }
        Category.CACHE -> when {
            !r.hasUsageAccess -> CategoryValue.NeedsAccess
            r.cacheAppCount == 0 -> CategoryValue.Empty
            else -> CategoryValue.Value(r.cacheAppCount, r.cacheBytes)
        }
    }

    /**
     * Where Home's "Review" goes: the first filter with something in it that this user can open
     * (Unused only for premium), else the All list.
     */
    fun reviewTab(result: ScanResult, isPremium: Boolean): HomeTab = when {
        isPremium && result.hasUsageAccess && result.unusedCount > 0 -> HomeTab.UNUSED
        result.largeCount > 0 -> HomeTab.LARGE
        result.hasUsageAccess && result.cacheAppCount > 0 -> HomeTab.CACHE
        else -> HomeTab.ALL
    }
}

/**
 * The grouped card: Unused · Large · Cache, one row each, "count" under the title, the size in
 * textPrimary at the end, then a chevron — or a single lock when the filter is Pro. When
 * [overlapCount] > 0 one plain line under the rows says those apps are counted once.
 */
@Composable
internal fun CategoryCard(
    rows: List<CategoryRow>,
    onOpen: (Category) -> Unit,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
    overlapCount: Int = 0,
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
        if (overlapCount > 0) {
            Text(
                pluralStringResource(R.plurals.home_cat_overlap_note, overlapCount, overlapCount),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = RowPadding, end = RowPadding, top = Dimens.space4, bottom = Dimens.space12),
            )
        }
    }
}

@Composable
private fun CategoryRowItem(row: CategoryRow, onClick: (() -> Unit)?) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val value = row.value
    val quiet = value == CategoryValue.Empty
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
            )
            when (value) {
                CategoryValue.Loading -> PlaceholderLine(MaterialTheme.typography.bodyMedium, widthFraction = 0.4f)
                CategoryValue.NeedsAccess -> Text(
                    stringResource(R.string.scan_intro_allow),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.positive,
                )
                is CategoryValue.Value -> Text(
                    pluralStringResource(R.plurals.home_list_count, value.count, value.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                CategoryValue.Empty -> Unit
            }
        }
        Spacer(Modifier.width(Dimens.space8))
        when (value) {
            CategoryValue.Loading -> PlaceholderLine(
                MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
                widthFraction = 1f,
            )
            CategoryValue.Empty -> Text(
                stringResource(R.string.home_cat_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
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
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

private val RowPadding = Dimens.space16
private val IconSize = 36.dp
private val RowHeight = 64.dp
private val QuietRowHeight = 52.dp
