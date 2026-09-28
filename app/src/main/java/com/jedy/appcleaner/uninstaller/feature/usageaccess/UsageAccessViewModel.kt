package com.jedy.appcleaner.uninstaller.feature.usageaccess

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccessIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Usage Access disclosure (PRD §4 Screen 11). Tracks the round trip to Settings for
 * `usage_access_granted` (seconds spent there, whether the fallback list page was used) and
 * closes the screen by itself once the grant is detected.
 *
 * The Settings timestamp uses elapsedRealtime and lives in [SavedStateHandle], so it survives
 * both a wall-clock change and our process being killed while the user is in Settings.
 */
@HiltViewModel
class UsageAccessViewModel @Inject constructor(
    private val access: UsageAccess,
    private val analytics: Analytics,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _close = Channel<Unit>(Channel.CONFLATED)

    /** Emits once when the screen should close because access is now granted. */
    val close: Flow<Unit> = _close.receiveAsFlow()

    private var closed = false

    init {
        viewModelScope.launch {
            access.isGranted.filter { it }.collect { onGranted() }
        }
    }

    /** PRD §9 usage_access_prompt_shown — once per visit, not per recomposition or rotation. */
    fun onShown(trigger: UsageAccessTrigger) {
        if (savedState.get<Boolean>(KEY_PROMPT_LOGGED) == true) return
        savedState[KEY_PROMPT_LOGGED] = true
        analytics.log(AnalyticsEvent.UsageAccessPromptShown(trigger.value))
    }

    /** The intent for "Continue to Settings"; starts the seconds-in-settings clock. */
    fun onContinue(): UsageAccessIntent {
        val target = access.settingsIntent()
        savedState[KEY_OPENED_AT] = SystemClock.elapsedRealtime()
        savedState[KEY_USED_FALLBACK] = target.usesFallbackPage
        return target
    }

    /**
     * PRD §6 item 13: the package-specific page resolved but failed to start on this OEM.
     * Falls back to the plain list and records that the fallback was used.
     */
    fun fallbackIntent(): Intent {
        savedState[KEY_USED_FALLBACK] = true
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /** Settings could not be opened at all: the clock must not keep running. */
    fun onSettingsUnavailable() {
        savedState.remove<Long>(KEY_OPENED_AT)
    }

    fun onResume() = access.recheck()

    private fun onGranted() {
        if (closed) return
        closed = true
        savedState.get<Long>(KEY_OPENED_AT)?.let { openedAt ->
            val seconds = ((SystemClock.elapsedRealtime() - openedAt) / 1000).coerceAtLeast(0)
            analytics.log(
                AnalyticsEvent.UsageAccessGranted(
                    secondsInSettings = seconds,
                    usedFallbackPage = savedState.get<Boolean>(KEY_USED_FALLBACK) == true,
                )
            )
        }
        _close.trySend(Unit)
    }

    private companion object {
        const val KEY_PROMPT_LOGGED = "prompt_logged"
        const val KEY_OPENED_AT = "settings_opened_at"
        const val KEY_USED_FALLBACK = "used_fallback_page"
    }
}
