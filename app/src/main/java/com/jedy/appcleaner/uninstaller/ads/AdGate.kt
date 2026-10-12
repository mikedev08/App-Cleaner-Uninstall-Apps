package com.jedy.appcleaner.uninstaller.ads

import com.jedy.appcleaner.uninstaller.core.analytics.AdFormat

/**
 * Whether any ad code may run (AdMob PRD §4.3). Pro means none at all: no consent form, no SDK
 * start, no load and no show. A first-run user is left alone until the language step is behind
 * them, so the consent form is never the first thing they see.
 */
enum class AdGate { ALLOW, WAIT_FOR_LANGUAGE, SUPPRESSED_PRO }

fun adGate(isPro: Boolean, pastLanguageStep: Boolean): AdGate = when {
    isPro -> AdGate.SUPPRESSED_PRO
    !pastLanguageStep -> AdGate.WAIT_FOR_LANGUAGE
    else -> AdGate.ALLOW
}

/**
 * Lets `ad_suppressed` fire once per placement per screen visit, not on every recomposition.
 * [newVisit] starts the count again when the screen changes.
 */
class SuppressionLog {
    private val logged = mutableSetOf<String>()

    /** True the first time [placement] of [format] is suppressed in this visit. */
    fun shouldLog(format: AdFormat, placement: String): Boolean = logged.add("${format.value}/$placement")

    fun newVisit() = logged.clear()
}
