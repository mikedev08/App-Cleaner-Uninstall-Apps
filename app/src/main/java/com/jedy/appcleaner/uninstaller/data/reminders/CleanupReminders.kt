package com.jedy.appcleaner.uninstaller.data.reminders

/** CONTRACT (frozen). Weekly premium cleanup reminder + monthly free nudge (PRD Feature 4, §2). */
interface CleanupReminders {
    /** Enqueue (or keep) the periodic worker. Idempotent. */
    fun schedule()

    /** Cancel the premium reminder worker (the free nudge keeps its own schedule). */
    fun cancel()
}
