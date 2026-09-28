package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.uninstall.AppWarning
import com.jedy.appcleaner.uninstaller.data.uninstall.AppWarnings
import com.jedy.appcleaner.uninstaller.data.uninstall.UninstallEngine
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the Confirm Sheet. [bytes] null = the app is not in the inventory (size unavailable). */
data class ConfirmApp(
    val packageName: String,
    val label: String,
    val bytes: Long?,
    val warnings: List<AppWarning>,
)

data class ConfirmUiState(
    val apps: List<ConfirmApp> = emptyList(),
    val totalBytes: Long = 0,
    /** APK-only sizes (free, or no Usage Access): the summary says "about" (PRD Feature 2). */
    val isEstimate: Boolean = true,
    val isStarting: Boolean = false,
)

/**
 * PRD §4 Screen 8. Always lists the *full* selection, including apps a search filter hides, so
 * nothing invisible is ever uninstalled (PRD §6 item 21).
 */
@HiltViewModel
class UninstallConfirmViewModel @Inject constructor(
    inventory: AppInventory,
    storage: StorageBreakdown,
    premium: Premium,
    usageAccess: UsageAccess,
    private val warnings: AppWarnings,
    private val engine: UninstallEngine,
) : ViewModel() {

    private val packages = MutableStateFlow<List<String>>(emptyList())
    private val flagged = MutableStateFlow<Map<String, List<AppWarning>>>(emptyMap())
    private val starting = MutableStateFlow(false)

    private val _started = Channel<Long>(Channel.BUFFERED)
    /** Emits the new batch id once it is safely in Room. */
    val started: Flow<Long> = _started.receiveAsFlow()

    private val fullSizes = combine(premium.isPremium, usageAccess.isGranted, storage.sizes) { isPremium, granted, sizes ->
        if (isPremium && granted) sizes else null
    }

    val uiState: StateFlow<ConfirmUiState> = combine(
        packages, inventory.apps, fullSizes, flagged, starting,
    ) { selected, apps, sizes, warningMap, isStarting ->
        val byPackage = apps.associateBy { it.packageName }
        var estimate = false
        val rows = selected.map { pkg ->
            val app = byPackage[pkg]
            val full = sizes?.get(pkg)?.totalBytes
            if (full == null) estimate = true
            ConfirmApp(
                packageName = pkg,
                label = app?.label ?: pkg,
                bytes = full ?: app?.apkBytes,
                warnings = warningMap[pkg].orEmpty(),
            )
        }
        ConfirmUiState(
            apps = rows,
            totalBytes = rows.sumOf { it.bytes ?: 0L },
            isEstimate = estimate || rows.isEmpty(),
            isStarting = isStarting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmUiState())

    /** True from confirm until the caller replaces the selection; see [setPackages]. */
    private var handedOff = false

    fun setPackages(selection: List<String>) {
        val distinct = selection.distinct()
        // Starting a batch clears the global selection before the caller navigates away; keep
        // showing the confirmed apps instead of flashing "Uninstall 0 apps" during that frame.
        if (distinct.isEmpty() && (starting.value || handedOff)) return
        handedOff = false
        if (packages.value == distinct) return
        packages.value = distinct
        viewModelScope.launch {
            flagged.value = runCatching { warnings.warningsFor(distinct) }.getOrDefault(emptyMap())
        }
    }

    fun confirm(sourceTab: HomeTab) {
        val selection = packages.value
        if (starting.value || selection.isEmpty()) return
        starting.value = true
        viewModelScope.launch {
            val batchId = runCatching { engine.createBatch(selection, sourceTab) }.getOrNull()
            if (batchId != null) {
                handedOff = true
                _started.send(batchId)
            }
            starting.value = false
        }
    }
}
