package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The Apps header: back, search and sort icons on top, then a large "Apps" title with the
 * library's real size underneath. Search swaps the title for a pill field in place, so the list
 * never jumps. Handles the status-bar inset itself (the NavHost applies none).
 */
@Composable
internal fun AppsHeader(
    summary: String?,
    isSearchOpen: Boolean,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    showSort: Boolean,
    sortOrder: SortOrder,
    isPremium: Boolean,
    onSortSelected: (SortOrder) -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = if (isSearchOpen) onCloseSearch else onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(if (isSearchOpen) R.string.home_action_close_search else R.string.apps_action_back),
                    tint = colors.textPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!isSearchOpen) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.home_action_search), tint = colors.textPrimary)
                }
            }
            if (showSort) SortMenuButton(current = sortOrder, isPremium = isPremium, onSelected = onSortSelected)
        }
        AnimatedContent(
            targetState = isSearchOpen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "appsHeader",
        ) { searching ->
            if (searching) {
                SearchPill(onQueryChanged = onQueryChanged, modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 4.dp))
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter)) {
                    Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                    Text(
                        text = summary.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The field owns its own text state so typing never round-trips through the ViewModel (which
 * drops characters under fast input); the ViewModel just observes it. Leaving search removes
 * the field from composition, so it reopens empty.
 */
@Composable
private fun SearchPill(onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val textState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val latestOnQueryChanged by rememberUpdatedState(onQueryChanged)

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { latestOnQueryChanged(it) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(Dimens.chipRadius))
                    .background(colors.surface)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
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
                    IconButton(onClick = { textState.clearText() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_action_clear_search), tint = colors.textSecondary)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

/** PRD §4 Screen 4 sort menu. Last used is premium: a PRO badge, and the paywall for free users. */
@Composable
private fun SortMenuButton(
    current: SortOrder,
    isPremium: Boolean,
    onSelected: (SortOrder) -> Unit,
) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.home_action_sort), tint = colors.textPrimary)
        }
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
                modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp),
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
                        { ProBadge() }
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

/**
 * All / Unused / Large as a pill segmented control. The green thumb slides between segments
 * (`offset` mirrors in RTL); a PRO badge marks the premium tabs for free users.
 */
@Composable
internal fun AppsTabs(
    selected: HomeTab,
    isPremium: Boolean,
    onSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val tabs = HomeTab.entries
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(colors.surface)
            .padding(4.dp),
    ) {
        val segment = maxWidth / tabs.size
        val thumbOffset by animateDpAsState(segment * selected.ordinal, label = "tabThumb")
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(colors.accent),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val textColor by animateColorAsState(if (isSelected) colors.onAccent else colors.textSecondary, label = "tabText")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Dimens.chipRadius))
                        .clickable { onSelected(tab) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tab != HomeTab.ALL && !isPremium) {
                        Spacer(Modifier.width(6.dp))
                        ProBadge()
                    }
                }
            }
        }
    }
}

@StringRes
private fun HomeTab.labelRes(): Int = when (this) {
    HomeTab.ALL -> R.string.apps_tab_all
    HomeTab.UNUSED -> R.string.home_tab_unused
    HomeTab.LARGE -> R.string.home_tab_large
}
