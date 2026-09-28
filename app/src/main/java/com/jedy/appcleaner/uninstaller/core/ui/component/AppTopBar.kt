package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The one top bar for every screen (design review §4 / §5). Replaces the four per-screen
 * treatments. It enforces:
 * - the same [Dimens.topBarHeight] below the status bar everywhere (it pads the status bar and
 *   horizontal cutouts itself, so put it first in a plain `Column`, not under another inset pad);
 * - 48dp icon buttons ([TopBarAction], [TopBarBackButton]) with outlined 24dp glyphs, placed so the
 *   first and last glyphs sit exactly on the 20dp page gutter;
 * - a scrolled state: once content is under it ([scrolled]), it fills with `surface` and shows a
 *   hairline, so text never cuts off hard against the background.
 *
 * Large titles: put a [LargeTitle] as the first scrolling item and pass
 * `titleVisible = rememberIsLargeTitleCollapsed(listState)`, so the small title fades in exactly
 * when the large one scrolls away. Screens without a large title leave [titleVisible] true.
 *
 * @param title the small bar title; null for none (Home shows a wordmark via [navigationIcon]).
 * @param onBack shows [TopBarBackButton]. Ignored when [navigationIcon] is given.
 * @param navigationIcon custom start content (e.g. the Home logo) instead of the back button.
 * @param actions [TopBarAction]s, end-aligned.
 * @param scrolled from [rememberIsScrolled].
 */
@Composable
fun AppTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrolled: Boolean = false,
    titleVisible: Boolean = true,
) {
    val colors = AppTheme.colors
    val background by animateColorAsState(if (scrolled) colors.surface else colors.background, label = "topBarBg")
    val hairline by animateColorAsState(if (scrolled) colors.border else Color.Transparent, label = "topBarLine")
    val titleAlpha by animateFloatAsState(if (titleVisible) 1f else 0f, label = "topBarTitle")
    val start = navigationIcon ?: onBack?.let { back -> { TopBarBackButton(onClick = back) } }
    Column(modifier.fillMaxWidth().background(background)) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth()
                .height(Dimens.topBarHeight)
                // A 48dp button centres a 24dp glyph, so 8dp here puts the glyph on the 20dp gutter.
                .padding(start = if (start != null) TopBarEdge else Dimens.gutter, end = TopBarEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (start != null) {
                start()
                Spacer(Modifier.width(4.dp))
            }
            Box(Modifier.weight(1f).graphicsLayer { alpha = titleAlpha }) {
                if (title != null) {
                    // Full words, never "…": shrinks on one line first, then wraps to two.
                    FitText(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        minFontSize = 15.sp,
                    )
                }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(hairline))
    }
}

private val TopBarEdge = Dimens.gutter - (Dimens.minTouchTarget - 24.dp) / 2

/** A 48dp top-bar icon button with an outlined glyph (use `Icons.Outlined.*` everywhere). */
@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.textPrimary,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(Dimens.minTouchTarget)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** The back arrow (auto-mirrored for RTL), a [TopBarAction]. */
@Composable
fun TopBarBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TopBarAction(
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.action_back),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The large page title, placed as the first item of the scrolling content at the 20dp gutter
 * with [Dimens.headingToContent] below. Pair with `AppTopBar(titleVisible = …)` so the bar's
 * small title takes over as it scrolls away. A title too long for one line wraps (never "…").
 */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space4, bottom = Dimens.headingToContent),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = AppTheme.colors.textPrimary,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

/** True once any content has scrolled under the top bar: drives `AppTopBar(scrolled = …)`. */
@Composable
fun rememberIsScrolled(state: LazyListState): Boolean {
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
    }
    return scrolled
}

/** [rememberIsScrolled] for a `verticalScroll` column. */
@Composable
fun rememberIsScrolled(state: ScrollState): Boolean {
    val scrolled by remember(state) { derivedStateOf { state.value > 0 } }
    return scrolled
}

/**
 * True once the [LargeTitle] at [largeTitleIndex] has mostly scrolled away (its first
 * [threshold] is gone): drives `AppTopBar(titleVisible = …)`.
 */
@Composable
fun rememberIsLargeTitleCollapsed(state: LazyListState, largeTitleIndex: Int = 0, threshold: Dp = 28.dp): Boolean {
    val px = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(state, largeTitleIndex, px) {
        derivedStateOf {
            state.firstVisibleItemIndex > largeTitleIndex ||
                (state.firstVisibleItemIndex == largeTitleIndex && state.firstVisibleItemScrollOffset > px)
        }
    }
    return collapsed
}

/** [rememberIsLargeTitleCollapsed] for a `verticalScroll` column whose first child is the title. */
@Composable
fun rememberIsLargeTitleCollapsed(state: ScrollState, threshold: Dp = 28.dp): Boolean {
    val px = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(state, px) { derivedStateOf { state.value > px } }
    return collapsed
}
