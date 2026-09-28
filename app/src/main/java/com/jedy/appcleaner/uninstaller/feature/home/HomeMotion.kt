package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import java.text.NumberFormat
import java.util.Locale

/**
 * A number that counts up from zero the first time it appears, then glides to new values. The
 * motion is presentation only: it always lands on [target], the real figure.
 */
@Composable
internal fun rememberCountUp(target: Float, durationMillis: Int = 1_100): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }
    return value.value
}

/** "72%" in the in-app language (digits and sign placement follow the locale, e.g. Arabic). */
@Composable
internal fun rememberPercentFormat(fractionDigits: Int = 0): (Float) -> String {
    val locale: Locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT
    return remember(locale, fractionDigits) {
        val format = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = fractionDigits }
        val formatter: (Float) -> String = { fraction -> format.format(fraction.toDouble()) }
        formatter
    }
}

/** "Just now", "12 minutes ago", "3 hours ago", "2 days ago" — for the Last scan card. */
@Composable
internal fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - then).coerceAtLeast(0) / MINUTE_MILLIS).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.home_time_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.home_time_minutes, minutes, minutes)
        minutes < 24 * 60 -> (minutes / 60).let { pluralStringResource(R.plurals.home_time_hours, it, it) }
        else -> ((now - then) / DAY_MILLIS).toInt().let { pluralStringResource(R.plurals.home_time_days, it, it) }
    }
}

private const val MINUTE_MILLIS = 60_000L
