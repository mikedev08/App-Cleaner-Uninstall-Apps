package com.jedy.appcleaner.uninstaller.feature.insights

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
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
         * "45 days" under two months, "4 months" under a year, then "2 years". Unused rows are
         * always 30+ days old, so the day unit only shows for the 30-day threshold.
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
 * Locale-correct "4 months" and "Mar 2025" (PRD §4 Screen 5). Built for the *in-app* locale from
 * LocalConfiguration — DateUtils would follow the device locale instead, so a Hebrew UI on an
 * English phone would print English dates. ICU also brings each language's plural rules and
 * digits (Arabic-Indic in Arabic), matching the Formatter-based byte sizes beside them, which is
 * why the idle chip uses MeasureFormat rather than one plural resource per unit and language.
 */
class InsightsDateFormatter(locale: Locale) {
    private val duration = MeasureFormat.getInstance(ULocale.forLocale(locale), MeasureFormat.FormatWidth.SHORT)
    private val monthYear = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMyyyy"), locale)

    /** "4 mths" (short units, so the chip never clips), for the "Not opened in 4 mths" chip. */
    fun duration(then: Long, now: Long): String {
        val age = Age.between(then, now)
        val unit = when (age.unit) {
            AgeUnit.DAYS -> MeasureUnit.DAY
            AgeUnit.MONTHS -> MeasureUnit.MONTH
            AgeUnit.YEARS -> MeasureUnit.YEAR
        }
        return duration.format(Measure(age.amount, unit))
    }

    fun monthYear(millis: Long): String = monthYear.format(Date(millis))
}
