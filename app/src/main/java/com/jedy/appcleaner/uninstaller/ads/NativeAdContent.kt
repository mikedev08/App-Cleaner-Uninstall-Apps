package com.jedy.appcleaner.uninstaller.ads

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.analytics.AdPlacement
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/** Null in previews and tests, where no ad is ever shown. Provided by MainActivity. */
val LocalAdManager = staticCompositionLocalOf<AdManager?> { null }

enum class NativeAdStyle(@param:LayoutRes internal val layout: Int, internal val corner: Dp) {
    /** Icon, headline, body and call to action in one row: the pinned ad on every screen. */
    STANDARD(R.layout.ad_native, Dimens.cardRadius),
}

/**
 * A fresh native ad for as long as the caller stays composed, or null until one has loaded. A
 * slot built on this takes no space before then: nothing shows rather than an empty box. The ad
 * is destroyed when the caller leaves.
 *
 * Always null for Pro users, so the slot is never composed; turning Pro on mid-visit destroys the
 * ad on screen at once (AdMob PRD §4.3).
 */
@Composable
fun rememberNativeAd(placement: AdPlacement): NativeAd? {
    val ads = LocalAdManager.current ?: return null
    val pro by ads.isPro.collectAsStateWithLifecycle()
    if (pro) {
        LaunchedEffect(ads, placement) { ads.onNativeSuppressed(placement) }
        return null
    }
    val holder = remember { NativeAdHolder() }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    DisposableEffect(ads) {
        val cancel = ads.requestNativeAd(holder::replace)
        onDispose { cancel() }
    }
    return holder.ad
}

/** Draws [ad] in [style], coloured for the current theme. */
@Composable
fun NativeAdCard(ad: NativeAd, style: NativeAdStyle, placement: AdPlacement, modifier: Modifier = Modifier) {
    val colors = adColors()
    val ads = LocalAdManager.current
    // The layout's own @string/ad_badge would resolve in the phone's language, not the app's.
    val badgeLabel = stringResource(R.string.ad_badge)
    key(style) {
        AndroidView(
            factory = { context ->
                LayoutInflater.from(context).inflate(style.layout, FrameLayout(context), false) as NativeAdView
            },
            modifier = modifier.fillMaxWidth(),
            update = { view ->
                view.findViewById<TextView>(R.id.ad_badge).text = badgeLabel
                if (view.bind(ad, style, colors)) ads?.onNativeAdShown(placement)
            },
        )
    }
}

private class NativeAdHolder {
    var ad by mutableStateOf<NativeAd?>(null)
        private set

    fun replace(fresh: NativeAd) {
        ad?.destroy()
        ad = fresh
    }

    fun destroy() {
        ad?.destroy()
        ad = null
    }
}

/**
 * Ad policy wants an ad told apart from the app at a glance, so it sits on a cool grey the app
 * uses nowhere else, a step away from both the background and the green-grey cards (PRD §6.3).
 */
private data class AdColors(
    val background: Color,
    val border: Color,
    val headline: Color,
    val body: Color,
    val badgeBackground: Color,
    val badgeText: Color,
    val action: Color,
    val onAction: Color,
)

@Composable
private fun adColors(): AdColors {
    val c = AppTheme.colors
    return AdColors(
        background = if (c.isDark) Color(0xFF232A36) else Color(0xFFE3E8F0),
        border = c.border,
        headline = c.textPrimary,
        body = c.textSecondary,
        badgeBackground = c.background,
        badgeText = c.textPrimary,
        action = c.accent,
        onAction = c.onAccent,
    )
}

/** True when [ad] is newly bound, rather than repainted. */
private fun NativeAdView.bind(ad: NativeAd, style: NativeAdStyle, colors: AdColors): Boolean {
    val density = resources.displayMetrics.density
    fun rounded(color: Color, radius: Dp, stroke: Color? = null) = GradientDrawable().apply {
        setColor(color.toArgb())
        cornerRadius = radius.value * density
        stroke?.let { setStroke((Dimens.hairline.value * density).toInt().coerceAtLeast(1), it.toArgb()) }
    }

    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView?>(R.id.ad_body)
    val action = findViewById<Button>(R.id.ad_call_to_action)
    val icon = findViewById<ImageView>(R.id.ad_app_icon)
    val badge = findViewById<TextView>(R.id.ad_badge)

    // Colours are set on every pass, so a theme switch repaints an ad already on screen.
    background = rounded(colors.background, style.corner, stroke = colors.border)
    headline.setTextColor(colors.headline.toArgb())
    body?.setTextColor(colors.body.toArgb())
    badge.background = rounded(colors.badgeBackground, 4.dp)
    badge.setTextColor(colors.badgeText.toArgb())
    action.background = rounded(colors.action, 100.dp)
    action.setTextColor(colors.onAction.toArgb())

    if (tag === ad) return false
    tag = ad
    headlineView = headline
    bodyView = body
    callToActionView = action
    iconView = icon

    headline.text = ad.headline
    body?.apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    action.text = ad.callToAction
    action.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    icon.setImageDrawable(ad.icon?.drawable)
    icon.visibility = if (ad.icon == null) View.INVISIBLE else View.VISIBLE
    setNativeAd(ad)
    return true
}
