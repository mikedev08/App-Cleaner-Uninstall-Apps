package com.jedy.appcleaner.uninstaller.core.analytics

/**
 * Every funnel event in PRD §9.
 *
 * Hard rule (PRD §0, §6 item 15): no event may carry a package name, app label or free text. The
 * fields here are counts, booleans, enums and byte *buckets* only — there is deliberately no
 * free-form String parameter that could smuggle part of the user's app inventory out.
 */
sealed class AnalyticsEvent(val name: String) {
    open val params: Map<String, Any> get() = emptyMap()

    data object OnboardingStart : AnalyticsEvent("onboarding_start")

    data class OnboardingStepViewed(val stepNumber: Int, val languageSelected: String) :
        AnalyticsEvent("onboarding_step_viewed") {
        override val params get() = mapOf("step_number" to stepNumber, "language_selected" to languageSelected)
    }

    data class OnboardingComplete(val skipped: Boolean) : AnalyticsEvent("onboarding_complete") {
        override val params get() = mapOf("skipped" to skipped)
    }

    data class HomeViewed(
        val appCount: Int,
        val storageUsedPct: Int,
        val hasUsageAccess: Boolean,
        val isPremium: Boolean,
    ) : AnalyticsEvent("home_viewed") {
        override val params get() = mapOf(
            "app_count" to appCount, "storage_used_pct" to storageUsedPct,
            "has_usage_access" to hasUsageAccess, "is_premium" to isPremium,
        )
    }

    /** [tab]: all | unused | large. [via]: checkbox | select_all | reminder_preselect. */
    data class AppSelected(val tab: String, val via: String) : AnalyticsEvent("app_selected") {
        override val params get() = mapOf("tab" to tab, "via" to via)
    }

    data class UninstallBatchStarted(
        val appCount: Int,
        val bytesBucket: String,
        val sourceTab: String,
        val hasWarnings: Boolean,
    ) : AnalyticsEvent("uninstall_batch_started") {
        override val params get() = mapOf(
            "app_count" to appCount, "bytes_bucket" to bytesBucket,
            "source_tab" to sourceTab, "has_warnings" to hasWarnings,
        )
    }

    /** North Star (PRD §9). Fires when the Result screen appears with removedCount >= 1. */
    data class UninstallBatchCompleted(
        val removedCount: Int,
        val skippedCount: Int,
        val failedCount: Int,
        val bytesFreedBucket: String,
        val bytesIsEstimate: Boolean,
        val secondsToComplete: Long,
        val stoppedEarly: Boolean,
        val isPremium: Boolean,
    ) : AnalyticsEvent("uninstall_batch_completed") {
        override val params get() = mapOf(
            "removed_count" to removedCount, "skipped_count" to skippedCount, "failed_count" to failedCount,
            "bytes_freed_bucket" to bytesFreedBucket, "bytes_is_estimate" to bytesIsEstimate,
            "seconds_to_complete" to secondsToComplete, "stopped_early" to stoppedEarly, "is_premium" to isPremium,
        )
    }

    /** [reason]: blocked | device_admin | not_removed_after_success | status_code_other. */
    data class UninstallFailed(val reason: String, val statusCode: Int) : AnalyticsEvent("uninstall_failed") {
        override val params get() = mapOf("reason" to reason, "status_code" to statusCode)
    }

    /** [action]: continue | discard. */
    data class UninstallQueueResumed(val remainingCount: Int, val action: String) :
        AnalyticsEvent("uninstall_queue_resumed") {
        override val params get() = mapOf("remaining_count" to remainingCount, "action" to action)
    }

    /** Three Cancels in a row paused the batch (PRD §6 "Several dialogs cancelled in a row"). */
    data class UninstallCancelStreakPrompt(val action: CancelStreakAction, val remainingCount: Int) :
        AnalyticsEvent("uninstall_cancel_streak_prompt") {
        override val params get() = mapOf("action" to action.value, "remaining_count" to remainingCount)
    }

    /**
     * A History entry was saved without its icon, or late, because the phone was out of space
     * (PRD §6 "Storage almost full"). An enum only: never the package or the error text.
     */
    data class HistorySnapshotDegraded(val reason: SnapshotDegradedReason) :
        AnalyticsEvent("history_snapshot_degraded") {
        override val params get() = mapOf("reason" to reason.value)
    }

    data class UsageAccessPromptShown(val trigger: String) : AnalyticsEvent("usage_access_prompt_shown") {
        override val params get() = mapOf("trigger" to trigger)
    }

    data class UsageAccessGranted(val secondsInSettings: Long, val usedFallbackPage: Boolean) :
        AnalyticsEvent("usage_access_granted") {
        override val params get() = mapOf("seconds_in_settings" to secondsInSettings, "used_fallback_page" to usedFallbackPage)
    }

    /** The blurred-count state — validates PRD §0 decision 3. [tab]: unused | large. */
    data class PremiumTeaserViewed(val tab: String, val foundCount: Int, val bytesBucket: String) :
        AnalyticsEvent("premium_teaser_viewed") {
        override val params get() = mapOf("tab" to tab, "found_count" to foundCount, "bytes_bucket" to bytesBucket)
    }

    data class PaywallViewed(val triggerSource: String, val trialEligible: Boolean, val offeringLoaded: Boolean) :
        AnalyticsEvent("paywall_viewed") {
        override val params get() = mapOf(
            "trigger_source" to triggerSource, "trial_eligible" to trialEligible, "offering_loaded" to offeringLoaded,
        )
    }

    data class PaywallDismissed(val triggerSource: String, val secondsVisible: Long) :
        AnalyticsEvent("paywall_dismissed") {
        override val params get() = mapOf("trigger_source" to triggerSource, "seconds_visible" to secondsVisible)
    }

    data class TrialStarted(val triggerSource: String) : AnalyticsEvent("trial_started") {
        override val params get() = mapOf("trigger_source" to triggerSource)
    }

    data class SubscriptionStarted(val triggerSource: String) : AnalyticsEvent("subscription_started") {
        override val params get() = mapOf("trigger_source" to triggerSource)
    }

    data class PurchaseRestored(val restoredPremium: Boolean) : AnalyticsEvent("purchase_restored") {
        override val params get() = mapOf("restored_premium" to restoredPremium)
    }

    /** [daysSinceRemovedBucket]: 0 | 1-7 | 8-30 | >30. */
    data class HistoryReinstallTapped(val daysSinceRemovedBucket: String) : AnalyticsEvent("history_reinstall_tapped") {
        override val params get() = mapOf("days_since_removed_bucket" to daysSinceRemovedBucket)
    }

    data class ReminderNotificationOpened(val unusedCount: Int, val thresholdDays: Int) :
        AnalyticsEvent("reminder_notification_opened") {
        override val params get() = mapOf("unused_count" to unusedCount, "threshold_days" to thresholdDays)
    }

    data class UnusedAppsFound(val count: Int, val thresholdDays: Int) : AnalyticsEvent("unused_apps_found") {
        override val params get() = mapOf("count" to count, "threshold_days" to thresholdDays)
    }

    data class InventoryIncomplete(val visibleCount: Int) : AnalyticsEvent("inventory_incomplete") {
        override val params get() = mapOf("visible_count" to visibleCount)
    }
}

/** `uninstall_cancel_streak_prompt.action`. */
enum class CancelStreakAction(val value: String) {
    STOP("stop"),
    KEEP_GOING("keep_going"),
}

/** `history_snapshot_degraded.reason`. */
enum class SnapshotDegradedReason(val value: String) {
    /** Under the free-space floor: the icon PNG was not even attempted. */
    LOW_SPACE("low_space"),

    /** Writing the icon PNG failed (IOException, ENOSPC). */
    IO_ERROR("io_error"),

    /** The Room write threw SQLiteFullException; the entry was held in memory and written later. */
    DB_FULL("db_full"),
}
