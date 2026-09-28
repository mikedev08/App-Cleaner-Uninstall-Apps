package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.scan.CleanupScan
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.data.scan.ScanStep
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
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

/** The Scan result's primary button: what it does is what its label says. */
sealed interface ScanCta {
    /** Quick scan: the honest next step is the full scan. */
    data object AllowAccess : ScanCta

    /** Premium: "Review 34 apps · 3.2 GB" pre-selects the Unused and Large apps. */
    data class ReviewApps(val count: Int, val bytes: Long, val tab: HomeTab) : ScanCta

    /** Premium, only cache to free: "Review cache · 520 MB". */
    data class ReviewCache(val bytes: Long) : ScanCta

    /** Free: "Remove 3 large apps · 1.0 GB", pre-selected on the free All list. */
    data class RemoveLarge(val count: Int, val bytes: Long) : ScanCta

    /** Free, and everything to free is Pro-only: "Review with Pro" with the lock → paywall. */
    data object Unlock : ScanCta

    /** Nothing to free: only "Review all apps". */
    data object None : ScanCta
}

object ScanCtas {
    /**
     * Design review §2A: a free-looking button never quotes a Pro-only number. Premium reviews
     * everything the rows count as apps (Unused + Large); free users are offered the Large apps,
     * which they can remove from the free All list; the rest is behind a visible lock.
     */
    fun primary(result: ScanResult, isPremium: Boolean): ScanCta = when {
        !result.hasUsageAccess -> ScanCta.AllowAccess
        isPremium && result.reviewAppCount > 0 -> ScanCta.ReviewApps(
            count = result.reviewAppCount,
            bytes = result.reviewAppBytes,
            // Only the All list shows Unused and Large apps together.
            tab = if (result.largeOnlyCount > 0) HomeTab.ALL else HomeTab.UNUSED,
        )
        isPremium && result.keptCacheBytes > 0 -> ScanCta.ReviewCache(result.keptCacheBytes)
        isPremium -> ScanCta.None
        result.largeOnlyCount > 0 -> ScanCta.RemoveLarge(result.largeOnlyCount, result.largeOnlyBytes)
        result.unusedCount > 0 || result.keptCacheBytes > 0 -> ScanCta.Unlock
        else -> ScanCta.None
    }
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
    private val storage: StorageBreakdown,
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
     * Premium "Review N apps": select exactly the apps the rows counted (Unused, then the Large
     * apps that are not unused), computed with the same rules and threshold, so the selection
     * bar repeats the button's N.
     */
    fun preselectReview(result: ScanResult) {
        val apps = inventory.apps.value
        val unused = unusedPackages(result)
        selection.clear()
        selection.select(unused + ScanMath.largeOnlyPackages(apps, storage.sizes.value, unused))
    }

    /** Free "Remove N large apps": the Large row's apps, on the free All list. */
    fun preselectLarge(result: ScanResult) {
        selection.clear()
        selection.select(ScanMath.largeOnlyPackages(inventory.apps.value, storage.sizes.value, unusedPackages(result)))
    }

    private fun unusedPackages(result: ScanResult): Set<String> =
        if (!result.hasUsageAccess) emptySet()
        else usageInsights.unusedApps(inventory.apps.value, result.thresholdDays).mapTo(HashSet()) { it.app.packageName }

    private fun pendingSteps(): Map<ScanStage, StepStatus> = ScanStage.entries.associateWith { StepStatus.PENDING }

    private companion object {
        const val MIN_STEP_MS = 800L
        const val RESULT_PAUSE_MS = 350L
    }
}
