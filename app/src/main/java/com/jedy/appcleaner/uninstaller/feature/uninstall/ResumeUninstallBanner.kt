package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.uninstall.ResumableBatch
import com.jedy.appcleaner.uninstaller.data.uninstall.UninstallEngine
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

/**
 * PRD §6 item 4. The queue lives in Room, so a batch interrupted by a kill is still there on the
 * next launch; this is the one place the user decides what happens to it.
 */
@HiltViewModel
class ResumeUninstallViewModel @Inject constructor(
    private val engine: UninstallEngine,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    private val _continued = Channel<Long>(Channel.BUFFERED)
    val continued: Flow<Long> = _continued.receiveAsFlow()

    /** Null hides the banner. */
    val resumable: StateFlow<ResumableBatch?> = combine(engine.observeResumable(), busy) { batch, isBusy ->
        batch?.takeIf { !isBusy }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Killed after the last result but before "finished" was written: close it quietly.
        viewModelScope.launch {
            engine.observeResumable().collect { batch ->
                if (batch != null && batch.remaining == 0) engine.finishIfComplete(batch.batchId)
            }
        }
    }

    fun onContinue(batchId: Long) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                engine.continueBatch(batchId)
                _continued.send(batchId)
            } finally {
                busy.value = false
            }
        }
    }

    fun onDiscard(batchId: Long) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                engine.discardBatch(batchId)
            } finally {
                busy.value = false
            }
        }
    }
}

/**
 * CONTRACT (frozen signature). PRD §6 item 4: "Finish removing 3 apps?" on Home. Renders nothing
 * when no unfinished batch exists. "Continue" re-verifies every remaining package before
 * [onContinue] opens the Progress screen; "Discard" drops the rest of the queue.
 */
@Composable
fun ResumeUninstallBanner(
    onContinue: (batchId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResumeUninstallViewModel = hiltViewModel()
    val resumable by viewModel.resumable.collectAsStateWithLifecycle()
    val latestOnContinue by rememberUpdatedState(onContinue)
    LaunchedEffect(viewModel) { viewModel.continued.collect { latestOnContinue(it) } }

    val batch = resumable?.takeIf { it.remaining > 0 }
    AnimatedVisibility(visible = batch != null, modifier = modifier) {
        if (batch != null) {
            ResumeBannerCard(
                remaining = batch.remaining,
                onContinue = { viewModel.onContinue(batch.batchId) },
                onDiscard = { viewModel.onDiscard(batch.batchId) },
            )
        }
    }
}

@Composable
private fun ResumeBannerCard(
    remaining: Int,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.tealSurface,
        border = BorderStroke(Dimens.hairline, AppTheme.colors.border),
    ) {
        Column(Modifier.padding(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.uninstall_resume_title, remaining, remaining),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.uninstall_resume_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.uninstall_resume_discard), color = AppTheme.colors.textSecondary)
                }
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.teal,
                        contentColor = AppTheme.colors.onTeal,
                    ),
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }
    }
}
