package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.color

/**
 * The list row shared by every app list (design system v2). Each row is its own soft card so
 * the list reads as a stack of things you can act on, not a spreadsheet:
 * 48dp icon · label + meta · optional severity chips · a size bar against the biggest app ·
 * the size in bold severity colour · a round check.
 *
 * @param sizeText bold trailing size ("515 MB"). Coloured by [severity].
 * @param sizeFraction 0..1 of the largest app in the list; null hides the bar.
 * @param chips severity chips under the meta line ("Space hog", "Not opened in 3 months").
 * @param icon defaults to the live launcher icon; History passes its snapshot instead.
 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    meta: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onToggleSelected: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingText: String? = null,
    severity: Severity = Severity.OK,
    sizeFraction: Float? = null,
    icon: @Composable () -> Unit = { AppIcon(packageName) },
    badge: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val container by animateColorAsState(
        if (selected == true) colors.accentSurface else colors.surface, label = "rowbg",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Dimens.appRowHeight)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chips != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) { chips() }
            }
            badge?.invoke()
            if (sizeFraction != null) {
                SizeBar(fraction = sizeFraction, severity = severity, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            }
        }
        if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.titleSmall,
                color = if (severity == Severity.OK) colors.textPrimary else severity.color,
                maxLines = 1,
            )
        }
        trailing?.invoke()
        if (selected != null) {
            RoundCheck(checked = selected, onToggle = onToggleSelected)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
