package com.jedy.appcleaner.uninstaller.feature.insights

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AgeUnit { DAYS, MONTHS, YEARS }

/** How long ago something happened, in the unit a person would say it in. Pure for unit tests. */
data class Age(val amount: Int, val unit: AgeUnit) {
    companion object {
        /**
         * "45 days ago" under two months, "4 months ago" under a year, then "2 years ago". Unused
         * rows are always 30+ days old, so the day unit only shows for the 30-day threshold.
         */
        fun between(then: Long, now: Long): Age {
            val days = ((now - then).coerceAtLeast(0) / DAY_MILLIS).toInt()
            return when {
                days < 60 -> Age(days, AgeUnit.DAYS)
                days < 365 -> Age(days / 30, AgeUnit.MONTHS)
                else -> Age(days / 365, AgeUnit.YEARS)
            }
        }
    }
}

/**
 * Locale-correct "4 months ago" and "Mar 2025" (PRD §4 Screen 5). Built for the *in-app* locale
 * from LocalConfiguration — DateUtils would follow the device locale instead, so a Hebrew UI on
 * an English phone would print English dates. ICU also brings each language's plural rules and
 * digits (Arabic-Indic in Arabic), matching the Formatter-based byte sizes beside them.
 */
class InsightsDateFormatter(locale: Locale) {
    private val relative = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    private val monthYear = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMyyyy"), locale)

    fun ago(then: Long, now: Long): String {
        val age = Age.between(then, now)
        if (age.amount == 0) {
            return relative.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
        }
        val unit = when (age.unit) {
            AgeUnit.DAYS -> RelativeDateTimeFormatter.RelativeUnit.DAYS
            AgeUnit.MONTHS -> RelativeDateTimeFormatter.RelativeUnit.MONTHS
            AgeUnit.YEARS -> RelativeDateTimeFormatter.RelativeUnit.YEARS
        }
        return relative.format(age.amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
    }

    fun monthYear(millis: Long): String = monthYear.format(Date(millis))
}
