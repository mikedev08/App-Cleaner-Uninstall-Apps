package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

private data class SelectionFigures(val count: Int, val bytes: Long, val hidden: Int)

/**
 * PRD §4 Screen 4 Selection Bar: slides up whenever anything is selected, on every tab.
 * "3 selected · 1.2 GB (2 hidden by search)" (§6 item 21), a clear (X) and the Remove Red
 * "Uninstall". It rides above the navigation bar and, while searching, above the keyboard.
 */
@Composable
internal fun SelectionBar(
    count: Int,
    bytes: Long,
    hiddenCount: Int,
    onClear: () -> Unit,
    onUninstall: () -> Unit,
) {
    // Keep the last non-empty figures on screen while the bar slides away, instead of "0 selected".
    val last = remember { arrayOfNulls<SelectionFigures>(1) }
    if (count > 0) last[0] = SelectionFigures(count, bytes, hiddenCount)

    AnimatedVisibility(
        visible = count > 0,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val figures = last[0] ?: return@AnimatedVisibility
        SelectionBarContent(figures, onClear, onUninstall)
    }
}

@Composable
private fun SelectionBarContent(
    figures: SelectionFigures,
    onClear: () -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val countText = pluralStringResource(R.plurals.home_selection_count, figures.count, figures.count)
    val summary = stringResource(R.string.home_selection_summary, countText, formatBytes(context, figures.bytes))
    val text = if (figures.hidden > 0) {
        stringResource(
            R.string.home_selection_with_hidden,
            summary,
            pluralStringResource(R.plurals.home_selection_hidden, figures.hidden, figures.hidden),
        )
    } else {
        summary
    }

    Column(Modifier.fillMaxWidth().background(colors.accentSurface)) {
        HorizontalDivider(thickness = Dimens.hairline, color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .heightIn(min = Dimens.selectionBarHeight)
                .padding(start = 4.dp, end = Dimens.gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.home_selection_clear),
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Dimens.gutterSmall))
            Button(
                onClick = onUninstall,
                shape = RoundedCornerShape(Dimens.controlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.removeRed,
                    contentColor = colors.onRemoveRed,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.home_uninstall), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
