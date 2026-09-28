package com.jedy.appcleaner.uninstaller.feature.usageaccess

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Usage Access disclosure (PRD §4 Screen 11). The trip to Settings and back — the seconds spent
 * there, the fallback page, `usage_access_granted`, the "still off" card on return — belongs to
 * [UsageAccessRoundTrip], because this screen closes as soon as Settings opens.
 */
@HiltViewModel
class UsageAccessViewModel @Inject constructor(
    private val access: UsageAccess,
    private val analytics: Analytics,
    private val savedState: SavedStateHandle,
    val roundTrip: UsageAccessRoundTrip,
) : ViewModel() {

    private val _close = Channel<Unit>(Channel.CONFLATED)

    /** Emits once when the screen should close because access is now granted. */
    val close: Flow<Unit> = _close.receiveAsFlow()

    init {
        viewModelScope.launch {
            access.isGranted.filter { it }.collect { _close.trySend(Unit) }
        }
    }

    /** PRD §9 usage_access_prompt_shown — once per visit, not per recomposition or rotation. */
    fun onShown(trigger: UsageAccessTrigger) {
        if (savedState.get<Boolean>(KEY_PROMPT_LOGGED) == true) return
        savedState[KEY_PROMPT_LOGGED] = true
        analytics.log(AnalyticsEvent.UsageAccessPromptShown(trigger.value))
    }

    fun onResume() = access.recheck()

    private companion object {
        const val KEY_PROMPT_LOGGED = "prompt_logged"
    }
}
