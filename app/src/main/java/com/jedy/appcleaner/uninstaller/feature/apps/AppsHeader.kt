package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar
import com.jedy.appcleaner.uninstaller.core.ui.component.LargeTitle
import com.jedy.appcleaner.uninstaller.core.ui.component.LockIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.TopBarAction
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * The Apps top bar (the shared [AppTopBar]): back, search and sort as 48dp outlined actions.
 * While searching, the back arrow closes search. The small "Apps" title fades in once the large
 * title has scrolled away ([titleVisible]).
 */
@Composable
internal fun AppsTopBar(
    isSearchOpen: Boolean,
    titleVisible: Boolean,
    scrolled: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    showSort: Boolean,
    sortOrder: SortOrder,
    isPremium: Boolean,
    onSortSelected: (SortOrder) -> Unit,
) {
    AppTopBar(
        title = if (isSearchOpen) null else stringResource(R.string.apps_title),
        titleVisible = titleVisible,
        scrolled = scrolled,
        navigationIcon = {
            if (isSearchOpen) {
                TopBarAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.home_action_close_search),
                    onClick = onCloseSearch,
                )
            } else {
                TopBarAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.apps_action_back),
                    onClick = onBack,
                )
            }
        },
        actions = {
            if (!isSearchOpen) {
                TopBarAction(Icons.Outlined.Search, stringResource(R.string.home_action_search), onOpenSearch)
            }
            if (showSort) SortMenuButton(current = sortOrder, isPremium = isPremium, onSelected = onSortSelected)
        },
    )
}

/** Large "Apps" title with the library's one count and size ("87 apps · 12 GB"). Scrolls away. */
@Composable
internal fun AppsTitle(summary: String?) {
    LargeTitle(text = stringResource(R.string.apps_title), subtitle = summary.orEmpty())
}

/**
 * Collapsing header state: the title block scrolls away with the list and comes back when the
 * list is pulled back to its top; only the filter bar stays pinned (design review §2.6 / §8 #7).
 *
 * [connection] must sit *between* the list and any pull-to-refresh container, so the header
 * expands before the refresh indicator takes the pull.
 */
@Stable
internal class CollapsingHeaderState {
    /** 0 = fully shown, negative = pixels scrolled away. */
    var offset by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(0f)
        internal set

    val collapsedFraction: Float get() = if (height <= 0f) 0f else (-offset / height).coerceIn(0f, 1f)

    fun reset() {
        offset = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val next = (offset + available.y).coerceAtLeast(-height)
            val consumed = next - offset
            offset = next
            return Offset(0f, consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val next = (offset + available.y).coerceAtMost(0f)
            val used = next - offset
            offset = next
            return Offset(0f, used)
        }
    }
}

@Composable
internal fun rememberCollapsingHeaderState(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/**
 * Lays out [header] (scrolls away by [state]'s offset), then [pinned] (always visible), then
 * [content] filling the rest. Content grows as the header collapses, so nothing is ever hidden
 * under it.
 */
@Composable
internal fun CollapsingHeaderLayout(
    state: CollapsingHeaderState,
    header: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(header, pinned, content),
        modifier = modifier,
    ) { (headerMeasurables, pinnedMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val headerPlaceables = headerMeasurables.map { it.measure(loose) }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
        state.height = headerHeight.toFloat()
        val offset = state.offset.coerceIn(-headerHeight.toFloat(), 0f).roundToInt()
        val pinnedPlaceables = pinnedMeasurables.map { it.measure(loose) }
        val pinnedHeight = pinnedPlaceables.maxOfOrNull { it.height } ?: 0
        val top = headerHeight + offset + pinnedHeight
        val contentHeight = (constraints.maxHeight - top).coerceAtLeast(0)
        val contentPlaceables = contentMeasurables.map {
            it.measure(Constraints.fixed(constraints.maxWidth, contentHeight))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.placeRelative(0, top) }
            headerPlaceables.forEach { it.placeRelative(0, offset) }
            pinnedPlaceables.forEach { it.placeRelative(0, headerHeight + offset) }
        }
    }
}

/**
 * All · Unused apps · Heavy apps · Temp files, as a horizontally scrollable row of pill chips:
 * every label is always complete, in every language, at 360dp (the row scrolls instead of
 * squeezing or "…"-ing a label). Each chip leads with its category icon; the selected one is
 * filled green. Only Unused apps is Pro: for free users it carries one small lock; tapping it
 * still opens its tab, whose teaser shows the user's real numbers. The selected chip is scrolled
 * into view, so a deep link to Temp files never leaves it off-screen.
 */
@Composable
internal fun AppsFilters(
    selected: HomeTab,
    isPremium: Boolean,
    onSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = Dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            FilterChip(
                label = stringResource(tab.labelRes()),
                icon = tab.icon(),
                selected = tab == selected,
                locked = tab == HomeTab.UNUSED && !isPremium,
                onClick = { onSelected(tab) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val container by animateColorAsState(if (selected) colors.accent else colors.surface, label = "filterBg")
    val content by animateColorAsState(if (selected) colors.onAccent else colors.textPrimary, label = "filterText")
    val iconTint by animateColorAsState(if (selected) colors.onAccent else colors.textSecondary, label = "filterIcon")
    val outline by animateColorAsState(if (selected) colors.accent else colors.border, label = "filterBorder")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) bringIntoView.bringIntoView() }
    val shape = RoundedCornerShape(Dimens.chipRadius)
    Row(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .heightIn(min = FilterChipHeight)
            .clip(shape)
            .background(container)
            .border(Dimens.hairline, outline, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(start = Dimens.space12, end = Dimens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            softWrap = false,
        )
        if (locked) {
            Spacer(Modifier.width(Dimens.space4))
            LockIcon(tint = iconTint, size = 13.dp)
        }
    }
}

private val FilterChipHeight = 40.dp

@StringRes
private fun HomeTab.labelRes(): Int = when (this) {
    HomeTab.ALL -> R.string.apps_tab_all
    HomeTab.UNUSED -> R.string.apps_tab_unused
    HomeTab.LARGE -> R.string.apps_tab_large
    HomeTab.CACHE -> R.string.apps_tab_cache
}

/** One icon per category, the same outlined glyphs Home and Scan use for these categories. */
internal fun HomeTab.icon(): ImageVector = when (this) {
    HomeTab.ALL -> Icons.Outlined.Apps
    HomeTab.UNUSED -> Icons.Outlined.HourglassEmpty
    HomeTab.LARGE -> Icons.Outlined.Inventory2
    HomeTab.CACHE -> Icons.Outlined.Layers
}

/**
 * The field owns its own text state so typing never round-trips through the ViewModel (which
 * drops characters under fast input); the ViewModel just observes it. Leaving search removes
 * the field from composition, so it reopens empty.
 */
@Composable
internal fun SearchPill(onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val textState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val latestOnQueryChanged by rememberUpdatedState(onQueryChanged)

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { latestOnQueryChanged(it) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val shape = RoundedCornerShape(Dimens.chipRadius)
    BasicTextField(
        state = textState,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        onKeyboardAction = { keyboard?.hide() },
        decorator = TextFieldDecorator { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(colors.surface)
                    .border(Dimens.hairline, colors.border, shape)
                    .padding(start = Dimens.space16, end = Dimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Dimens.space12))
                Box(Modifier.weight(1f)) {
                    if (textState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textMuted,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (textState.text.isNotEmpty()) {
                    TopBarAction(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.home_action_clear_search),
                        onClick = { textState.clearText() },
                        tint = colors.textSecondary,
                    )
                } else {
                    Spacer(Modifier.width(Dimens.space12))
                }
            }
        },
    )
}

/** PRD §4 Screen 4 sort menu. Last used is premium: the shared lock icon, and the paywall for free users. */
@Composable
private fun SortMenuButton(
    current: SortOrder,
    isPremium: Boolean,
    onSelected: (SortOrder) -> Unit,
) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        TopBarAction(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.home_action_sort), onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(Dimens.cardRadius),
            containerColor = colors.surfaceElevated,
            shadowElevation = 12.dp,
        ) {
            Text(
                text = stringResource(R.string.home_sort_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.space8),
            )
            SortOrder.entries.forEach { order ->
                val locked = order.isPremium && !isPremium
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(order.labelRes()),
                            style = if (order == current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(order)
                    },
                    leadingIcon = {
                        if (order == current) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accentText)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    trailingIcon = if (locked) {
                        { LockIcon() }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(textColor = colors.textPrimary),
                )
            }
        }
    }
}

@StringRes
private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.SIZE -> R.string.home_sort_size
    SortOrder.NAME -> R.string.home_sort_name
    SortOrder.INSTALL_DATE -> R.string.home_sort_install_date
    SortOrder.LAST_UPDATED -> R.string.home_sort_last_updated
    SortOrder.LAST_USED -> R.string.home_sort_last_used
}
