package com.jedy.appcleaner.uninstaller.data.reminders

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    private val now = 1_000 * DAY_MILLIS

    @Test
    fun `nothing unused never reminds`() {
        assertFalse(ReminderPolicy.shouldRemind(unusedCount = 0, lastCount = 0, lastAt = 0, now = now))
    }

    @Test
    fun `first reminder fires when anything is unused`() {
        assertTrue(ReminderPolicy.shouldRemind(unusedCount = 3, lastCount = 0, lastAt = 0, now = now))
    }

    @Test
    fun `a grown count reminds even within 30 days`() {
        assertTrue(ReminderPolicy.shouldRemind(unusedCount = 10, lastCount = 9, lastAt = now - 7 * DAY_MILLIS, now = now))
    }

    @Test
    fun `the same count waits for 30 days`() {
        assertFalse(ReminderPolicy.shouldRemind(9, 9, lastAt = now - 29 * DAY_MILLIS, now = now))
        assertTrue(ReminderPolicy.shouldRemind(9, 9, lastAt = now - 30 * DAY_MILLIS, now = now))
        assertTrue(ReminderPolicy.shouldRemind(4, 9, lastAt = now - 31 * DAY_MILLIS, now = now))
    }

    @Test
    fun `a last reminder in the future (clock moved back) stays silent unless the count grew`() {
        assertFalse(ReminderPolicy.shouldRemind(9, 9, lastAt = now + DAY_MILLIS, now = now))
        assertTrue(ReminderPolicy.shouldRemind(10, 9, lastAt = now + DAY_MILLIS, now = now))
    }

    @Test
    fun `nudge is free-only and at most once per 30 days`() {
        assertFalse(ReminderPolicy.shouldNudge(isPremium = true, installedCount = 120, lastNudgeAt = 0, now = now))
        assertFalse(ReminderPolicy.shouldNudge(isPremium = false, installedCount = 0, lastNudgeAt = 0, now = now))
        assertTrue(ReminderPolicy.shouldNudge(isPremium = false, installedCount = 120, lastNudgeAt = 0, now = now))
        assertFalse(ReminderPolicy.shouldNudge(false, 120, lastNudgeAt = now - 29 * DAY_MILLIS, now = now))
        assertTrue(ReminderPolicy.shouldNudge(false, 120, lastNudgeAt = now - 30 * DAY_MILLIS, now = now))
        assertFalse(ReminderPolicy.shouldNudge(false, 120, lastNudgeAt = now + DAY_MILLIS, now = now))
    }
}
