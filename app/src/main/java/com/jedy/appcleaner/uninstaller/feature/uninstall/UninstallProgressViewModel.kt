package com.jedy.appcleaner.uninstaller.feature.uninstall

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.data.local.UninstallItemEntity
import com.jedy.appcleaner.uninstaller.data.uninstall.BatchSummary
import com.jedy.appcleaner.uninstaller.data.uninstall.EngineRuntime
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.PendingConfirmation
import com.jedy.appcleaner.uninstaller.data.uninstall.UninstallEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueRow(
    val item: UninstallItemEntity,
    val state: ItemState,
    /** Android's dialog for this app is up (or about to be). */
    val isAwaitingUser: Boolean,
)

data class ProgressUiState(
    val rows: List<QueueRow> = emptyList(),
    val total: Int = 0,
    val position: Int = 0,
    val progress: Float = 0f,
    val isFinished: Boolean = false,
    val isStopping: Boolean = false,
    /** A dialog the screen must start — only while it is at least STARTED. */
    val confirmation: PendingConfirmation? = null,
    val stalled: Boolean = false,
    /** Three Cancels in a row: ask "Stop removing the rest?"; the value is how many apps are left. */
    val cancelStreakRemaining: Int? = null,
    val isLoaded: Boolean = false,
)

/** PRD §4 Screen 9. A thin view over [UninstallEngine]; every transition is already in Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UninstallProgressViewModel @Inject constructor(
    private val engine: UninstallEngine,
) : ViewModel() {

    private val batchId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ProgressUiState> = batchId.filterNotNull()
        .flatMapLatest { id ->
            combine(engine.observeSummary(id), engine.runtime) { summary, rt ->
                // A batch id that is not in Room has nothing to run: hand over to the Result screen.
                summary?.toUiState(rt.takeIf { it.activeBatchId == id }) ?: ProgressUiState(isFinished = true, isLoaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun bind(id: Long) {
        if (batchId.value == id) return
        batchId.value = id
        engine.attach(id)
    }

    fun onResumed() = engine.onScreenResumed()
    fun onPaused() = engine.onScreenPaused()
    fun onStopped() = engine.onScreenStopped()

    fun launchConfirmation(context: Context, confirmation: PendingConfirmation) {
        val shown = confirmation.launcher.launch(context)
        engine.onConfirmationLaunched(confirmation.itemId, shown)
    }

    fun onShowAgain() = engine.reRequestCurrent()

    fun onStop() {
        val id = batchId.value ?: return
        viewModelScope.launch { engine.stop(id) }
    }

    /** "Stop removing the rest?" → Stop: ends the batch like [onStop]; the Result screen follows. */
    fun onCancelStreakStop() {
        val id = batchId.value ?: return
        viewModelScope.launch { engine.stopAfterCancelStreak(id) }
    }

    /** "Stop removing the rest?" → Keep going: the streak starts over and the next dialog opens. */
    fun onCancelStreakKeepGoing() = engine.keepGoingAfterCancelStreak()

    private fun BatchSummary.toUiState(rt: EngineRuntime?) =
        ProgressUiState(
            rows = items.map { item ->
                QueueRow(
                    item = item,
                    state = ItemState.of(item.state),
                    isAwaitingUser = rt?.awaitingItemId == item.id,
                )
            },
            total = total,
            position = currentPosition,
            progress = progress,
            isFinished = isFinished,
            isStopping = batch.stoppedEarly && !isFinished,
            confirmation = rt?.confirmation?.takeIf { !it.launched },
            stalled = rt?.stalled == true,
            cancelStreakRemaining = rt?.cancelStreakPrompt?.takeIf { !isFinished && !batch.stoppedEarly },
            isLoaded = true,
        )
}
