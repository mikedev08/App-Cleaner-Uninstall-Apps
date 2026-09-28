package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.scan.CleanupScan
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.data.scan.ScanStep
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StepStatus { PENDING, RUNNING, DONE, SKIPPED }

/** The three visible steps, in order; each maps to one [ScanStep] of the real scan. */
enum class ScanStage(val step: ScanStep) { APPS(ScanStep.APPS), USAGE(ScanStep.USAGE), STORAGE(ScanStep.STORAGE) }

sealed interface ScanPhase {
    /** No Usage Access yet: explain it, offer the disclosure or a quick scan. */
    data object Intro : ScanPhase

    data class Scanning(val steps: Map<ScanStage, StepStatus>) : ScanPhase {
        /** Completed share for the ring: finished steps plus a head start on the running one. */
        val fraction: Float get() {
            val done = steps.values.count { it == StepStatus.DONE || it == StepStatus.SKIPPED }
            val running = if (steps.values.any { it == StepStatus.RUNNING }) 0.6f else 0f
            return ((done + running) / steps.size).coerceIn(0f, 1f)
        }
    }

    data class Result(val result: ScanResult) : ScanPhase

    data object Failed : ScanPhase
}

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.Intro,
    val isPremium: Boolean = false,
    val hasUsageAccess: Boolean = false,
)

/**
 * Runs the real [CleanupScan] behind a staged animation. Each step turns green only once the
 * scan has actually passed it *and* it has been on screen for [MIN_STEP_MS] (≈2.4 s in total),
 * so the scan reads as thorough without ever showing a number the scan did not produce.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scan: CleanupScan,
    private val usageAccess: UsageAccess,
    private val usageInsights: UsageInsights,
    private val inventory: AppInventory,
    private val selection: SelectionStore,
    premium: Premium,
) : ViewModel() {

    init {
        usageAccess.recheck()
    }

    /** With access the first frame is already the scan, so the intro never flashes. */
    private val phase = MutableStateFlow<ScanPhase>(if (usageAccess.isGranted.value) ScanPhase.Scanning(pendingSteps()) else ScanPhase.Intro)
    private var scanJob: Job? = null

    val uiState: StateFlow<ScanUiState> = combine(phase, premium.isPremium, usageAccess.isGranted) { p, isPremium, granted ->
        ScanUiState(phase = p, isPremium = isPremium, hasUsageAccess = granted)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScanUiState(phase = phase.value, isPremium = premium.isPremium.value, hasUsageAccess = usageAccess.isGranted.value),
    )

    init {
        if (phase.value is ScanPhase.Scanning) start()
        // With access there is nothing to explain: go straight to the scan. Without it, the intro
        // waits — and starts on its own the moment the user returns having granted access.
        viewModelScope.launch {
            usageAccess.isGranted.collect { granted -> if (granted && phase.value == ScanPhase.Intro) start() }
        }
    }

    fun onResume() = usageAccess.recheck()

    /** "Quick scan": everything that needs no permission (inventory and APK sizes). */
    fun onQuickScan() = start()

    fun onRetry() = start()

    private fun start() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val skipUsage = !usageAccess.isGranted.value
            val steps = pendingSteps().toMutableMap()
            phase.value = ScanPhase.Scanning(steps.toMap())
            val finished = MutableStateFlow(false)
            // runCatching keeps a failed scan from cancelling this coroutine before it can say so.
            val work = async { runCatching { scan.run() } }
            work.invokeOnCompletion { finished.value = true }
            try {
                for (stage in ScanStage.entries) {
                    steps[stage] = StepStatus.RUNNING
                    phase.value = ScanPhase.Scanning(steps.toMap())
                    val shownAt = System.currentTimeMillis()
                    // Wait for the real scan to move past this step (or to finish).
                    combine(scan.progress, finished) { progress, done ->
                        done || (progress != null && progress.step.ordinal > stage.step.ordinal)
                    }.first { it }
                    delay((MIN_STEP_MS - (System.currentTimeMillis() - shownAt)).coerceAtLeast(0))
                    steps[stage] = if (stage == ScanStage.USAGE && skipUsage) StepStatus.SKIPPED else StepStatus.DONE
                    phase.value = ScanPhase.Scanning(steps.toMap())
                }
                val result = work.await().getOrThrow()
                delay(RESULT_PAUSE_MS)
                phase.value = ScanPhase.Result(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                phase.update { ScanPhase.Failed }
            }
        }
    }

    /**
     * Premium "Free up X": pre-select exactly the apps the scan counted as unused, so the Unused
     * tab opens with the uninstall one tap away. Computed from the same rule and threshold.
     */
    fun preselectUnused(result: ScanResult) {
        val unused = usageInsights.unusedApps(inventory.apps.value, result.thresholdDays)
        selection.select(unused.map { it.app.packageName })
    }

    private fun pendingSteps(): Map<ScanStage, StepStatus> = ScanStage.entries.associateWith { StepStatus.PENDING }

    private companion object {
        const val MIN_STEP_MS = 800L
        const val RESULT_PAUSE_MS = 350L
    }
}
