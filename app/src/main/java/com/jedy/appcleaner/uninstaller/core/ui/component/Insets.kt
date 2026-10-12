package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/*
 * Edge-to-edge (design review §2A). The navigation bar is fully transparent (MainActivity), so
 * content scrolls under the buttons. Every scrolling screen must end its content above them:
 * navigation-bar height + 16dp.
 */

/**
 * True under something pinned between the content and the navigation bar (a native ad, AdMob
 * PRD §6.2): that already clears the bar, so the content ends [Dimens.bottomContentGap] above it
 * instead. Whoever provides it also consumes the navigation-bar insets, for [Modifier.bottomContentPadding].
 */
val LocalNavigationBarCovered = staticCompositionLocalOf { false }

/**
 * Bottom padding for scrolling content: the navigation bar's height + [extra] (16dp). Add it to
 * a `LazyColumn`'s `contentPadding` bottom, plus anything floating over the list (a selection
 * bar's height).
 */
@Composable
fun bottomContentPadding(extra: Dp = Dimens.bottomContentGap): Dp =
    if (LocalNavigationBarCovered.current) extra
    else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

/** `LazyColumn(contentPadding = listContentPadding())`: [top] above, [bottomContentPadding] below. */
@Composable
fun listContentPadding(top: Dp = 0.dp, extraBottom: Dp = Dimens.bottomContentGap): PaddingValues =
    PaddingValues(top = top, bottom = bottomContentPadding(extraBottom))

/**
 * The same rule for a `verticalScroll` `Column`: apply it to the column's modifier *after*
 * `verticalScroll` so the space sits inside the scrolling content.
 */
fun Modifier.bottomContentPadding(extra: Dp = Dimens.bottomContentGap): Modifier =
    navigationBarsPadding().padding(bottom = extra)
