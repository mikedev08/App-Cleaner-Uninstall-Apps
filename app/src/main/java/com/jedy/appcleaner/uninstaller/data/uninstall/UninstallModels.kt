package com.jedy.appcleaner.uninstaller.data.uninstall

import android.content.Context

/**
 * Where one queued app stands (PRD Feature 2). Persisted as [UninstallItemEntity.state] by name,
 * so the queue survives process death exactly as it was (PRD §6 item 22).
 */
enum class ItemState {
    WAITING,

    /** The uninstall was handed to PackageInstaller and its final status has not come back yet. */
    IN_PROGRESS,
    REMOVED,

    /** The user tapped Cancel on Android's dialog. Never stops the batch (PRD Feature 2). */
    SKIPPED,
    FAILED,

    /** Gone before its turn (removed elsewhere). Not ours, so not in the freed total (PRD §6 item 5). */
    ALREADY_REMOVED;

    val isFinal: Boolean get() = this != WAITING && this != IN_PROGRESS

    companion object {
        fun of(value: String): ItemState = entries.firstOrNull { it.name == value } ?: WAITING
    }
}

/**
 * Why an item failed. [analyticsValue] is the PRD §9 `uninstall_failed.reason` enum, so the event
 * never needs anything but this and the status code.
 */
enum class FailureReason(val analyticsValue: String, val canRetry: Boolean) {
    /** STATUS_FAILURE_BLOCKED: device policy. Retrying cannot succeed, so no retry (PRD §6 item 2). */
    BLOCKED("blocked", canRetry = false),

    /** An active device administrator; retry works once the user deactivates it (PRD §6 item 1). */
    DEVICE_ADMIN("device_admin", canRetry = true),

    /** STATUS_SUCCESS, yet the package is still installed — some OEM skins do this (PRD §6 item 6). */
    NOT_REMOVED_AFTER_SUCCESS("not_removed_after_success", canRetry = true),
    STATUS_CODE_OTHER("status_code_other", canRetry = true);

    companion object {
        fun of(value: String?): FailureReason? = entries.firstOrNull { it.name == value }
    }
}

/** A reason to pause before removing an app, shown as a chip on the Confirm Sheet. Warn, never block. */
enum class AppWarning {
    DEVICE_ADMIN,
    KEYBOARD,
    LAUNCHER,
    SMS,
    DIALER,

    /** Accessibility service, notification listener or anything else [UsageInsights] flags. */
    ALWAYS_RUNNING,
}

/**
 * What PackageInstaller told us about one request. The confirmation dialog's Intent stays inside
 * [ConfirmationLauncher], so the engine (and its tests) never touch an Android Intent.
 */
sealed interface RemovalEvent {
    val itemId: Long

    /** STATUS_PENDING_USER_ACTION: Android's own dialog is ready to be shown from the foreground. */
    data class NeedsConfirmation(override val itemId: Long, val launcher: ConfirmationLauncher) : RemovalEvent

    /** Any final status, including our own [UninstallRules.STATUS_NOT_SHOWN] sentinel. */
    data class Finished(override val itemId: Long, val status: Int) : RemovalEvent
}

/** Starts Android's confirmation dialog. Must be called with a foreground Activity context. */
fun interface ConfirmationLauncher {
    /** @return false when the dialog could not be started at all. */
    fun launch(context: Context): Boolean
}

/** The confirmation dialog the Progress screen should show, and whether it already has. */
data class PendingConfirmation(
    val itemId: Long,
    val launcher: ConfirmationLauncher,
    val launched: Boolean = false,
)

/** In-memory engine state. Everything durable lives in Room; this is only what the UI needs live. */
data class EngineRuntime(
    val activeBatchId: Long? = null,
    val awaitingItemId: Long? = null,
    val confirmation: PendingConfirmation? = null,
    /** Our screen came back but no result arrived: offer "Show it again" (see [UninstallEngine.reRequestCurrent]). */
    val stalled: Boolean = false,
    /**
     * Three Cancels in a row: the queue is paused on "Stop removing the rest?" and this is how
     * many apps are still to go (PRD §6 "Several dialogs cancelled in a row"). Null otherwise.
     */
    val cancelStreakPrompt: Int? = null,
)

/** Injectable time source so the queue logic is testable. */
fun interface Clock {
    fun now(): Long
}
