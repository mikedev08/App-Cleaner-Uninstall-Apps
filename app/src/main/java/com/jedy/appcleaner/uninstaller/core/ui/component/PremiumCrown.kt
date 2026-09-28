package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme

/** The gold premium marker (PRD §1: gold is used only for premium). */
@Composable
fun PremiumCrown(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(
        imageVector = Icons.Rounded.WorkspacePremium,
        contentDescription = null,
        tint = AppTheme.colors.premiumGold,
        modifier = modifier.size(size),
    )
}
