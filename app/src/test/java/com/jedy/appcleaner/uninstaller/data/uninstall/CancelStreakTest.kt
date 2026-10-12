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

    @Test
    fun `each Cancel warms the ring a third of the way, amber at the first ask`() {
        assertEquals(0f, CancelStreak.warmth(streak = 0, keepGoings = 0), 0f)
        assertEquals(1f / 3, CancelStreak.warmth(streak = 1, keepGoings = 0), 1e-6f)
        assertEquals(2f / 3, CancelStreak.warmth(streak = 2, keepGoings = 0), 1e-6f)
        assertEquals(1f, CancelStreak.warmth(streak = 3, keepGoings = 0), 0f)
    }

    @Test
    fun `after Keep going the ring stays amber and heads for red, and never past it`() {
        assertEquals(1f, CancelStreak.warmth(streak = 0, keepGoings = 1), 0f)
        assertEquals(4f / 3, CancelStreak.warmth(streak = 1, keepGoings = 1), 1e-6f)
        assertEquals(2f, CancelStreak.warmth(streak = 3, keepGoings = 1), 0f)
        assertEquals(2f, CancelStreak.warmth(streak = 3, keepGoings = 4), 0f)
    }

    @Test
    fun `only a removal forgets the Keep goings`() {
        assertEquals(0, CancelStreak.nextKeepGoings(2, Outcome.Removed))
        assertEquals(2, CancelStreak.nextKeepGoings(2, Outcome.Skipped))
        assertEquals(2, CancelStreak.nextKeepGoings(2, Outcome.NotShown))
    }
}
