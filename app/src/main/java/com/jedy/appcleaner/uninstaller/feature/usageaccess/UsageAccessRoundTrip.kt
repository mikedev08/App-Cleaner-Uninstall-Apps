package com.jedy.appcleaner.uninstaller.feature.usageaccess

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccessIntent
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One trip to the Usage access page in Settings and back (PRD §6 "OEM without the
 * package-specific Usage Access page").
 *
 * The disclosure screen closes as soon as Settings opens, so the user comes back to exactly the
 * screen and tab they started from. On that return (`onResume`), either access is on — the screen
 * refreshes by itself and `usage_access_granted` is logged — or it is still off, and [stillOff]
 * names the screen that should show the gentle "Usage access is still off" card with "Try again".
 * Never the paywall, never a modal, never the full disclosure again.
 *
 * The trip is kept in a tiny SharedPreferences file, so a process killed while the user was in
 * Settings still gets its card and its analytics. elapsedRealtime survives wall-clock changes.
 */
@Singleton
class UsageAccessRoundTrip @Inject constructor(
    @ApplicationContext context: Context,
    private val access: UsageAccess,
    private val analytics: Analytics,
    @ApplicationScope scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _stillOff = MutableStateFlow<UsageAccessTrigger?>(null)

    /** The screen the user left for Settings and came back to without turning access on. */
    val stillOff: StateFlow<UsageAccessTrigger?> = _stillOff.asStateFlow()

    init {
        scope.launch { access.isGranted.filter { it }.collect { onGranted() } }
    }

    fun settingsIntent(): UsageAccessIntent = access.settingsIntent()

    /** Settings is on screen now; [trigger] is where the user will come back to. */
    @Synchronized
    fun onSettingsOpened(trigger: UsageAccessTrigger, usedFallbackPage: Boolean) {
        prefs.edit {
            putLong(KEY_OPENED_AT, SystemClock.elapsedRealtime())
            putBoolean(KEY_USED_FALLBACK, usedFallbackPage)
            putString(KEY_TRIGGER, trigger.value)
        }
        _stillOff.value = null
    }

    /** Every activity resume. Only does anything when the user is coming back from Settings. */
    fun onAppResumed() {
        if (!prefs.contains(KEY_TRIGGER)) return
        access.recheck()
        if (access.isGranted.value) {
            onGranted()
        } else {
            synchronized(this) {
                val trigger = prefs.getString(KEY_TRIGGER, null)?.let(UsageAccessTrigger::from) ?: return
                prefs.edit { clear() }
                _stillOff.value = trigger
            }
        }
    }

    @Synchronized
    private fun onGranted() {
        _stillOff.value = null
        if (!prefs.contains(KEY_TRIGGER)) return
        val openedAt = prefs.getLong(KEY_OPENED_AT, 0L)
        val usedFallback = prefs.getBoolean(KEY_USED_FALLBACK, false)
        prefs.edit { clear() }
        val seconds = ((SystemClock.elapsedRealtime() - openedAt) / 1000).coerceAtLeast(0)
        analytics.log(AnalyticsEvent.UsageAccessGranted(secondsInSettings = seconds, usedFallbackPage = usedFallback))
    }

    private companion object {
        const val PREFS = "usage_access_round_trip"
        const val KEY_OPENED_AT = "opened_at"
        const val KEY_USED_FALLBACK = "used_fallback_page"
        const val KEY_TRIGGER = "trigger"
    }
}
