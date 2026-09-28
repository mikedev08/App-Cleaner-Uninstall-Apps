package com.jedy.appcleaner.uninstaller.feature.apps

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jedy.appcleaner.uninstaller.core.locale.LocalAppLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Date formatting in the *in-app* language via [LocalAppLocale] (which, unlike
 * `LocalConfiguration`, survives into bottom sheets), so "Installed Mar 2024" follows the language picker rather than the
 * device locale, digits included.
 */
@Composable
internal fun rememberMonthYearFormat(): (Long) -> String = rememberPattern("MMMyyyy")

/** "Mar 4, 2024" and its local equivalents, for the details sheet. */
@Composable
internal fun rememberDayMonthYearFormat(): (Long) -> String = rememberPattern("dMMMyyyy")

@Composable
private fun rememberPattern(skeleton: String): (Long) -> String {
    val locale: Locale = LocalAppLocale.current
    return remember(locale, skeleton) {
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        val formatter: (Long) -> String = { millis -> format.format(Date(millis)) }
        formatter
    }
}
