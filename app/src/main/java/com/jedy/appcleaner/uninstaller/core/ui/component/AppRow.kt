package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The 72dp list row shared by every tab (PRD §1 Design Integrity): 40dp icon, label, one line
 * of metadata, trailing checkbox. Row body opens details; the checkbox selects (PRD §4 Screen 4).
 *
 * @param icon defaults to the live launcher icon; History passes its snapshot instead.
 * @param badge optional chip under the label (e.g. a warning), rendered below [meta].
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
    icon: @Composable () -> Unit = { AppIcon(packageName) },
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.appRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            badge?.invoke()
        }
        if (trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        trailing?.invoke()
        if (selected != null) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggleSelected?.let { toggle -> { _ -> toggle() } },
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colors.teal,
                    checkmarkColor = AppTheme.colors.onTeal,
                    uncheckedColor = AppTheme.colors.textSecondary,
                ),
            )
        }
    }
}
