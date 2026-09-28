package com.jedy.appcleaner.uninstaller.data.uninstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelStreakTest {

    private fun run(vararg outcomes: Outcome): Int = outcomes.fold(0, CancelStreak::next)

    @Test
    fun `three Cancels in a row ask, with apps left`() {
        val streak = run(Outcome.Skipped, Outcome.Skipped, Outcome.Skipped)
        assertEquals(3, streak)
        assertTrue(CancelStreak.shouldAsk(streak, remaining = 2))
    }

    @Test
    fun `one or two Cancels never ask`() {
        assertFalse(CancelStreak.shouldAsk(run(Outcome.Skipped), remaining = 5))
        assertFalse(CancelStreak.shouldAsk(run(Outcome.Skipped, Outcome.Skipped), remaining = 5))
    }

    @Test
    fun `a removal between Cancels starts the count over`() {
        val streak = run(Outcome.Skipped, Outcome.Skipped, Outcome.Removed, Outcome.Skipped, Outcome.Skipped)
        assertEquals(2, streak)
        assertFalse(CancelStreak.shouldAsk(streak, remaining = 3))
    }

    @Test
    fun `items with no dialog neither count nor break the run`() {
        val streak = run(
            Outcome.Skipped,
            Outcome.Failed(FailureReason.BLOCKED),
            Outcome.Skipped,
            Outcome.NotShown,
            Outcome.Skipped,
        )
        assertEquals(3, streak)
    }

    @Test
    fun `nothing left to remove means nothing to ask`() {
        assertFalse(CancelStreak.shouldAsk(streak = 3, remaining = 0))
    }

    @Test
    fun `keep going resets, and it takes three more Cancels to ask again`() {
        // "Keep going" is the engine setting the streak back to 0.
        val afterKeepGoing = run(Outcome.Skipped, Outcome.Skipped)
        assertFalse(CancelStreak.shouldAsk(afterKeepGoing, remaining = 4))
        assertTrue(CancelStreak.shouldAsk(CancelStreak.next(afterKeepGoing, Outcome.Skipped), remaining = 3))
    }
}
