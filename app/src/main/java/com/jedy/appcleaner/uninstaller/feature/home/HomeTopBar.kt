package com.jedy.appcleaner.uninstaller.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.component.PremiumCrown
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * PRD §4 Screen 4 top app bar: title, Search, Sort (All tab only), History, Settings. Search
 * expands inline in place of the title and filters as the user types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(
    isSearchOpen: Boolean,
    onQueryChanged: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    showSort: Boolean,
    sortOrder: SortOrder,
    isPremium: Boolean,
    onSortSelected: (SortOrder) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = AppTheme.colors
    TopAppBar(
        title = {
            if (isSearchOpen) {
                InlineSearchField(onQueryChanged = onQueryChanged)
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (isSearchOpen) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.home_action_close_search))
                }
            }
        },
        actions = {
            if (!isSearchOpen) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.home_action_search))
                }
            }
            if (showSort) {
                SortMenuButton(current = sortOrder, isPremium = isPremium, onSelected = onSortSelected)
            }
            if (!isSearchOpen) {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.home_action_history))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.home_action_settings))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            scrolledContainerColor = colors.background,
            titleContentColor = colors.textPrimary,
            navigationIconContentColor = colors.textPrimary,
            actionIconContentColor = colors.textPrimary,
        ),
    )
}

/**
 * The field owns its own text state so typing never round-trips through the ViewModel (which
 * drops characters under fast input); the ViewModel just observes it. Leaving search removes
 * the field from composition, so it reopens empty.
 */
@Composable
private fun InlineSearchField(onQueryChanged: (String) -> Unit) {
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
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (textState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (textState.text.isNotEmpty()) {
                    IconButton(onClick = { textState.clearText() }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.home_action_clear_search),
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
        },
    )
}

/** PRD §4 Screen 4 sort menu. Last used is premium: a crown, and the paywall for free users. */
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
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.home_action_sort))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(Dimens.controlRadius),
            containerColor = colors.background,
            border = BorderStroke(Dimens.hairline, colors.border),
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
                    text = { Text(stringResource(order.labelRes()), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelected(order)
                    },
                    leadingIcon = {
                        if (order == current) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    trailingIcon = if (locked) {
                        { PremiumCrown(size = 18.dp) }
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
