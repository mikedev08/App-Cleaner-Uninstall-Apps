package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp

/** One `UsageStats` bucket, reduced to the fields the finder needs. Plain Kotlin so the rule is unit-testable. */
data class UsageRecord(
    val packageName: String,
    val lastTimeUsed: Long,
    /** `lastTimeVisible` (API 29+); 0 below. */
    val lastTimeVisible: Long,
    /** The bucket's `firstTimeStamp`: how far back the retained data reaches. */
    val bucketStart: Long,
)

/** The reduced view: latest use per package, and the start of the window the data covers. */
data class UsageSnapshot(
    val lastUsed: Map<String, Long>,
    val windowStart: Long,
)

/**
 * The Unused finder's rules (PRD Feature 3 and §6 item 14), kept free of Android types so the
 * part that decides "suggest removing this" is covered by plain JUnit tests.
 */
object UnusedRule {

    /** Android keeps yearly buckets for about two years; that is the furthest back we can see. */
    const val RETENTION_MILLIS = 2 * 365 * DAY_MILLIS

    /**
     * Latest of `lastTimeUsed` and `lastTimeVisible` per package across every bucket. A timestamp
     * in the future (the clock was moved back) is clamped to [now] — "used today" — so a clock
     * change can make the finder under-report but never flag an app the user just opened.
     */
    fun reduce(records: List<UsageRecord>, now: Long): UsageSnapshot {
        val lastUsed = HashMap<String, Long>()
        var earliestBucket = Long.MAX_VALUE
        for (record in records) {
            if (record.bucketStart in 1 until earliestBucket) earliestBucket = record.bucketStart
            val used = maxOf(record.lastTimeUsed, record.lastTimeVisible)
            if (used <= 0L) continue
            val clamped = used.coerceAtMost(now)
            val previous = lastUsed[record.packageName]
            if (previous == null || clamped > previous) lastUsed[record.packageName] = clamped
        }
        val windowStart = if (earliestBucket == Long.MAX_VALUE) now - RETENTION_MILLIS else earliestBucket.coerceAtMost(now)
        return UsageSnapshot(lastUsed, windowStart)
    }

    /**
     * Unused at [thresholdDays] means: not used within the threshold AND not installed within it
     * (an app installed last week is not "unused for 60 days"). No record at all inside the window
     * counts as unused with `lastUsedAt = null` — shown as "Not opened since at least …", never
     * "Never opened", which we can't know. [excluded] (launcher, keyboard, SMS, dialer,
     * accessibility, notification listeners, device admins) run without being "opened" and are
     * never suggested. Sorted least recently used first; no-record apps lead.
     */
    fun find(
        apps: List<InstalledApp>,
        lastUsed: Map<String, Long>,
        excluded: Set<String>,
        thresholdDays: Int,
        now: Long,
    ): List<UnusedApp> {
        val threshold = thresholdDays.coerceAtLeast(1) * DAY_MILLIS
        return apps.asSequence()
            .filter { it.packageName !in excluded }
            .filter { now - it.firstInstallTime >= threshold }
            .mapNotNull { app ->
                val used = lastUsed[app.packageName]?.coerceAtMost(now)
                if (used == null || now - used >= threshold) UnusedApp(app, used) else null
            }
            .sortedWith(compareBy<UnusedApp, Long?>(nullsFirst()) { it.lastUsedAt }.thenBy { it.app.label.lowercase() })
            .toList()
    }

    /**
     * `Settings.Secure` stores enabled accessibility services and notification listeners as
     * colon-separated flattened component names ("pkg/.Cls:pkg2/pkg2.Cls"). Returns the packages.
     */
    fun packagesFromComponentList(value: String?): Set<String> =
        value.orEmpty()
            .split(':')
            .mapNotNull { entry -> entry.trim().substringBefore('/').takeIf { it.isNotEmpty() } }
            .toSet()
}
