package com.jedy.appcleaner.uninstaller.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.history.HistoryDay
import com.jedy.appcleaner.uninstaller.data.history.HistoryRepository
import com.jedy.appcleaner.uninstaller.data.history.HistoryRules
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.local.UninstallHistoryEntity
import com.jedy.appcleaner.uninstaller.data.uninstall.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryRow(
    val entry: UninstallHistoryEntity,
    /** The package is back on the device (PRD Feature 4): a tag replaces the button. */
    val isReinstalled: Boolean,
) {
    val isFromPlay: Boolean get() = entry.installerPackage == InstalledApp.PLAY_STORE_PACKAGE
}

data class HistorySection(val kind: HistoryDay.Kind, val date: LocalDate, val rows: List<HistoryRow>)

data class HistoryUiState(
    val sections: List<HistorySection> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0,
    val anyEstimate: Boolean = false,
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && totalCount == 0
}

/** PRD §4 Screen 12. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val inventory: AppInventory,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {

    /** Bumped on resume: coming back from the Play Store after a reinstall must flip the tag. */
    private val recheck = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = combine(repository.entries, inventory.apps, recheck) { entries, _, _ ->
        val installed = entries.mapTo(HashSet()) { it.packageName }.filterTo(HashSet()) { inventory.isInstalled(it) }
        HistoryUiState(
            sections = HistoryRules.groupByDay(entries, clock.now()).map { day ->
                HistorySection(day.kind, day.date, day.entries.map { HistoryRow(it, it.packageName in installed) })
            },
            totalCount = entries.size,
            totalBytes = entries.sumOf { it.bytes },
            anyEstimate = entries.any { it.bytesIsEstimate },
            isLoaded = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onResumed() = recheck.update { it + 1 }

    fun onReinstallTapped(entry: UninstallHistoryEntity) {
        analytics.log(AnalyticsEvent.HistoryReinstallTapped(HistoryRules.daysSinceRemovedBucket(entry.removedAt, clock.now())))
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clear() }
    }
}
