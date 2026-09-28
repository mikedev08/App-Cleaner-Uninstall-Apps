package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme

private data class SelectionFigures(val count: Int, val bytes: Long, val hidden: Int)

/** Height the list reserves under its last row while the bar floats over it. */
internal val SelectionBarReserve = 112.dp

/**
 * PRD §4 Screen 4 Selection Bar, now a floating rounded bar above the navigation bar (and the
 * keyboard while searching): the freed-space figure large ("455 MB"), "3 selected" under it,
 * the hidden-by-search note (§6 item 21), a clear X and the Remove Red "Uninstall".
 */
@Composable
internal fun SelectionBar(
    count: Int,
    bytes: Long,
    hiddenCount: Int,
    onClear: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last non-empty figures on screen while the bar slides away, instead of "0 selected".
    val last = remember { arrayOfNulls<SelectionFigures>(1) }
    if (count > 0) last[0] = SelectionFigures(count, bytes, hiddenCount)

    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn() + scaleIn(initialScale = 0.96f),
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
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(elevation = 18.dp, shape = shape)
            .clip(shape)
            .background(colors.surfaceElevated)
            .heightIn(min = 84.dp)
            .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_selection_clear), tint = colors.textSecondary)
        }
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = formatBytes(context, figures.bytes),
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "selectionBytes",
            ) { size ->
                Text(size, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, maxLines = 1)
            }
            val countText = pluralStringResource(R.plurals.home_selection_count, figures.count, figures.count)
            Text(
                text = if (figures.hidden > 0) {
                    stringResource(
                        R.string.home_selection_with_hidden,
                        countText,
                        pluralStringResource(R.plurals.home_selection_hidden, figures.hidden, figures.hidden),
                    )
                } else {
                    countText
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        PrimaryButton(
            text = stringResource(R.string.home_uninstall),
            onClick = onUninstall,
            destructive = true,
            icon = Icons.Rounded.DeleteOutline,
        )
    }
}
