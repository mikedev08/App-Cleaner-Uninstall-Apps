package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity

/**
 * A neutral chip for facts that are not warnings ("Not opened in 2 mo", "40 MB cache"):
 * textSecondary on surfaceMuted (design review §2A: no orange text on pale yellow). One line at
 * its intrinsic width, so AppRow never clips its unit.
 */
@Composable
internal fun QuietChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = AppTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(colors.surfaceMuted)
            .padding(horizontal = Dimens.space8, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(Dimens.space4))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, softWrap = false)
    }
}

/** The one "Large" marker (`LargeApps.isLarge`), amber like the Large bar. */
@Composable
internal fun LargeChip(modifier: Modifier = Modifier) {
    SeverityChip(stringResource(R.string.apps_chip_large), Severity.WARNING, modifier = modifier)
}
