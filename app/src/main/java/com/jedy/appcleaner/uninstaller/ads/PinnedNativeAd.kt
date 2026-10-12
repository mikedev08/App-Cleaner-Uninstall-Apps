package com.jedy.appcleaner.uninstaller.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.nativead.NativeAd
import com.jedy.appcleaner.uninstaller.core.analytics.AdPlacement
import com.jedy.appcleaner.uninstaller.core.ui.component.LocalNavigationBarCovered
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * [content] above a native ad pinned to the bottom of the screen, which never scrolls away
 * (AdMob PRD §6.2). Until an ad has loaded, and for Pro users, [content] has the whole screen.
 * With an ad, the ad clears the navigation bar and [content] ends above the ad instead.
 */
@Composable
fun PinnedAdLayout(placement: AdPlacement, content: @Composable () -> Unit) {
    val ad = rememberNativeAd(placement)
    if (ad == null) {
        content()
        return
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
            CompositionLocalProvider(LocalNavigationBarCovered provides true, content = content)
        }
        PinnedNativeAd(ad, placement, Modifier.navigationBarsPadding())
    }
}

/** A pinned ad at the page gutter, for screens that place it themselves (above their own buttons). */
@Composable
fun PinnedNativeAd(ad: NativeAd, placement: AdPlacement, modifier: Modifier = Modifier) {
    NativeAdCard(
        ad = ad,
        style = NativeAdStyle.STANDARD,
        placement = placement,
        modifier = modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp),
    )
}
