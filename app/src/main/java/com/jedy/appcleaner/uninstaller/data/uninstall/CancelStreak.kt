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
}
