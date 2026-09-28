package com.jedy.appcleaner.uninstaller.data.usage

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.reminders.ReminderNotifications
import com.jedy.appcleaner.uninstaller.data.reminders.WorkManagerCleanupReminders
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Insights feature's process-start hook (PRD Features 3 and 4). It owns *when* things are
 * computed, so no screen has to remember to:
 *
 *  - re-check Usage Access every time the app comes to the foreground (PRD §6 item 12);
 *  - recompute last-used data and sizes whenever access becomes granted or the inventory
 *    changes (debounced, so an install burst costs one pass) — for every user who granted
 *    access, because the blurred teaser's count must be real (PRD §0 decision 3);
 *  - wipe cached sizes the moment access is revoked, so stale numbers never pose as current;
 *  - turn the reminder prefs + entitlement into WorkManager state (Settings only writes prefs).
 *
 * UI reads [sizesMeasured] / [sizesRefreshing] to tell "still measuring" from "unavailable".
 */
@OptIn(FlowPreview::class)
@Singleton
class InsightsSync @Inject constructor(
    private val access: UsageAccess,
    private val insights: UsageInsights,
    private val storage: StorageBreakdown,
    private val inventory: AppInventory,
    private val premium: Premium,
    private val preferences: AppPreferences,
    private val reminders: WorkManagerCleanupReminders,
    private val notifications: ReminderNotifications,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AppStartup {

    private val _sizesRefreshing = MutableStateFlow(false)
    val sizesRefreshing: StateFlow<Boolean> = _sizesRefreshing.asStateFlow()

    /** True once a size pass has completed since access was (last) granted. */
    private val _sizesMeasured = MutableStateFlow(false)
    val sizesMeasured: StateFlow<Boolean> = _sizesMeasured.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun start() {
        access.recheck()
        // start() runs off the main thread (see AppStartup); Lifecycle observers must be added on
        // it. An observer added after the process has started still gets onStart immediately.
        scope.launch(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    access.recheck()
                    requestRefresh()
                }
            })
        }
        scope.launch { syncInsights() }
        scope.launch {
            // The free nudge is always kept scheduled; its worker skips premium users itself.
            reminders.scheduleNudge()
            syncReminders()
        }
        // Re-creating the channel on a language change renames it in system settings.
        scope.launch { preferences.languageTag.distinctUntilChanged().collect { notifications.ensureChannel() } }
    }

    /** Recompute now (e.g. the app returned to the foreground). No-op without access. */
    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun syncInsights() {
        // Keyed on package + update time: a relabel doesn't need re-measuring, an update does.
        val inventoryChanges = inventory.apps
            .map { apps -> apps.mapTo(HashSet()) { it.packageName to it.lastUpdateTime } }
            .distinctUntilChanged()
            .debounce(INVENTORY_DEBOUNCE_MILLIS)
            .map { }
        access.isGranted.collectLatest { granted ->
            if (!granted) {
                _sizesMeasured.value = false
                insights.refresh() // clears last-used data when access is gone
                storage.refresh() // clears the size cache when access is gone
                return@collectLatest
            }
            merge(inventoryChanges, refreshRequests).collectLatest { recompute() }
        }
    }

    private suspend fun recompute() {
        insights.refresh()
        _sizesRefreshing.value = true
        try {
            storage.refresh()
            if (access.isGranted.value) _sizesMeasured.value = true
        } finally {
            _sizesRefreshing.value = false
        }
    }

    /**
     * PRD Feature 4: reminders switch on by themselves at the first premium unlock, then follow
     * the Settings toggle. Debounced because the entitlement can read "false" for a moment at
     * cold start while RevenueCat loads its cache; cancelling and re-enqueueing on every launch
     * would keep pushing the weekly run into the future.
     */
    private suspend fun syncReminders() {
        combine(premium.isPremium, preferences.remindersEnabled, preferences.remindersInitialised) { p, e, i ->
            Triple(p, e, i)
        }
            .distinctUntilChanged()
            .debounce(ENTITLEMENT_SETTLE_MILLIS)
            .collect { (isPremium, enabled, initialised) ->
                when {
                    isPremium && !initialised -> {
                        preferences.setRemindersEnabled(true)
                        preferences.setRemindersInitialised(true)
                    }
                    isPremium && enabled -> reminders.schedule()
                    else -> reminders.cancel()
                }
            }
    }

    private companion object {
        const val INVENTORY_DEBOUNCE_MILLIS = 500L
        const val ENTITLEMENT_SETTLE_MILLIS = 3_000L
    }
}
