package com.jedy.appcleaner.uninstaller.data.uninstall

/**
 * PRD §6 "Several dialogs cancelled in a row": a single Cancel means "not that one", three in a
 * row usually mean "stop". Plain Kotlin so every branch is unit-tested.
 *
 * Only a user Cancel ([Outcome.Skipped], i.e. `STATUS_FAILURE_ABORTED`) adds to the streak and
 * only a verified removal resets it (as does "Keep going"). Items that never showed a dialog — a
 * blocked app, one withdrawn by Stop — neither count nor break the run: they are not a dialog the
 * user answered.
 */
object CancelStreak {

    const val THRESHOLD = 3

    fun next(streak: Int, outcome: Outcome): Int = when (outcome) {
        Outcome.Skipped -> streak + 1
        Outcome.Removed -> 0
        Outcome.NotShown, is Outcome.Failed -> streak
    }

    /** Ask "Stop removing the rest?" instead of opening the next dialog — only if there is one. */
    fun shouldAsk(streak: Int, remaining: Int): Boolean = streak >= THRESHOLD && remaining > 0

    /**
     * How warm the Progress ring is: 0 is calm green, 1 amber (the first "Stop removing the
     * rest?"), 2 red (asked again after Keep going). Each Cancel moves it a third of the way to
     * the next colour; [keepGoings] is how many times the user chose Keep going since the last
     * removal. A removal resets both, so the ring cools down to green.
     */
    fun warmth(streak: Int, keepGoings: Int): Float =
        (keepGoings + streak.toFloat() / THRESHOLD).coerceIn(0f, MAX_WARMTH)

    /** A removal cools everything down; any other outcome keeps the Keep-going count. */
    fun nextKeepGoings(keepGoings: Int, outcome: Outcome): Int = if (outcome == Outcome.Removed) 0 else keepGoings

    const val MAX_WARMTH = 2f
}
