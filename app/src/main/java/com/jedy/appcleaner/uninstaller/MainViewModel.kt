package com.jedy.appcleaner.uninstaller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.locale.LocaleController
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode
import com.jedy.appcleaner.uninstaller.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Null while deciding; the system splash stays up during that window. */
    val startRoute: String? = null,
)

/** A reminder notification tap (PRD Feature 4). */
data class ReminderLaunch(val tab: HomeTab, val preselect: List<String>, val count: Int, val threshold: Int)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val localeController: LocaleController,
    private val inventory: AppInventory,
    private val selection: SelectionStore,
    private val analytics: Analytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState(language = localeController.deviceDefault()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
    }

    fun resolveStartRoute() {
        if (_uiState.value.startRoute != null) return
        viewModelScope.launch {
            val onboarded = preferences.isOnboardingComplete()
            val route = if (onboarded) Routes.HOME else Routes.ONBOARDING
            if (!onboarded) {
                analytics.log(AnalyticsEvent.OnboardingStart)
                // PRD §3: warm the inventory during onboarding so Home is populated on arrival.
                launch { inventory.refresh() }
            }
            _uiState.update {
                it.copy(startRoute = route, language = localeController.resolve(preferences.languageTag.first()))
            }
        }
    }

    /** Applies immediately so the UI flips under the user's finger, then persists. */
    fun onLanguageSelected(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
        viewModelScope.launch { preferences.setLanguageTag(language.tag) }
    }

    fun onOnboardingFinished(skipped: Boolean) {
        viewModelScope.launch {
            preferences.setLanguageTag(_uiState.value.language.tag)
            preferences.setOnboardingComplete(true)
            preferences.setOnboardingPaywallShown(true)
            analytics.log(AnalyticsEvent.OnboardingComplete(skipped))
        }
    }

    fun requestHomeTab(tab: HomeTab) = selection.requestTab(tab)

    fun onReminderLaunch(launch: ReminderLaunch) {
        selection.select(launch.preselect)
        selection.requestTab(launch.tab)
        analytics.log(AnalyticsEvent.ReminderNotificationOpened(launch.count, launch.threshold))
    }
}
