package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing and radii. PRD §1: 12dp–16dp corners, subtle borders instead of shadows. */
object Dimens {
    val gutter = 16.dp
    val gutterSmall = 12.dp
    val gutterLarge = 24.dp

    val cardRadius = 16.dp
    val controlRadius = 12.dp
    val chipRadius = 999.dp

    val hairline = 1.dp

    /** App list row (PRD §1: 72dp row, 40dp icon). */
    val appRowHeight = 72.dp
    val appIconSize = 40.dp

    val selectionBarHeight = 72.dp
    val buttonHeight = 52.dp
}
