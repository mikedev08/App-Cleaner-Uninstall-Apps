package com.jedy.appcleaner.uninstaller.data.reminders

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS

/**
 * When a reminder or nudge may be posted (PRD Feature 4, §2). Pure Kotlin so the anti-spam rules
 * are unit-tested: a retention notification that nags is worse than none.
 */
object ReminderPolicy {

    const val REPEAT_AFTER_MILLIS = 30 * DAY_MILLIS

    /**
     * Premium reminder: only when something is actually unused, and either the count grew since
     * the last reminder or 30 days have passed. A clock moved backwards (last reminder "in the
     * future") counts as not-yet-due rather than due, erring towards silence.
     */
    fun shouldRemind(unusedCount: Int, lastCount: Int, lastAt: Long, now: Long): Boolean {
        if (unusedCount <= 0) return false
        if (unusedCount > lastCount) return true
        return lastAt in 0..now && now - lastAt >= REPEAT_AFTER_MILLIS
    }

    /** Free nudge: non-premium only, at most once per 30 days, and only with something to clean. */
    fun shouldNudge(isPremium: Boolean, installedCount: Int, lastNudgeAt: Long, now: Long): Boolean {
        if (isPremium || installedCount <= 0) return false
        if (lastNudgeAt > now) return false
        return now - lastNudgeAt >= REPEAT_AFTER_MILLIS
    }
}
