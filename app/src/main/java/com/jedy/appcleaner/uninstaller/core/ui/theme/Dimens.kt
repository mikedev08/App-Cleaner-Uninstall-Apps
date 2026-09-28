package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design system v2.1 spacing (design review §4). Big soft cards with a 1dp [hairline] border,
 * pill controls, and one spacing scale: 4 / 8 / 12 / 16 / 24 / 32. Reach for a named role
 * ([sectionGap], [cardGap], ...) first, a raw step ([space12], ...) second, and a literal never.
 */
object Dimens {
    /** The one side margin for *everything*: headings, cards, app rows, bottom bars. */
    val gutter = 20.dp
    val gutterSmall = 12.dp
    val gutterLarge = 28.dp

    // The spacing scale.
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp

    /** Between two sections of a screen. */
    val sectionGap = space32
    /** Between two cards in a stack. */
    val cardGap = space12
    /** From a section heading to its first card or row. */
    val headingToContent = space12
    /** Between two stacked full-width buttons. */
    val stackedButtonGap = space12
    /** Between two app rows in a list (AppRow applies half of it above and below itself). */
    val rowGap = space8
    /** Scrolling content ends this far above the navigation bar (see `bottomContentPadding`). */
    val bottomContentGap = space16

    val cardRadius = 24.dp
    val controlRadius = 18.dp
    val chipRadius = 999.dp

    val hairline = 1.dp

    /** App list row: minimum height (a long name or a chip on its own line makes it taller), 48dp squircle icon. */
    val appRowHeight = 80.dp
    val appIconSize = 48.dp

    /** [AppTopBar][com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar] height below the status bar. */
    val topBarHeight = 64.dp
    /** Minimum touch target for every icon button and check. */
    val minTouchTarget = 48.dp

    val selectionBarHeight = 88.dp
    val buttonHeight = 58.dp
}
