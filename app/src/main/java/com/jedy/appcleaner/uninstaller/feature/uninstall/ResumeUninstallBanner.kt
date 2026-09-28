package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
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

/**
 * Warm, not alarming: amber says "unfinished", never "something went wrong". Continue is the
 * filled action because finishing is what the user already chose; Discard stays one tap away.
 */
@Composable
private fun ResumeBannerCard(
    remaining: Int,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        color = AppTheme.colors.warningSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.RestartAlt, contentDescription = null,
                    tint = AppTheme.colors.warning, modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.uninstall_resume_title, remaining, remaining),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.uninstall_resume_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall)) {
            SecondaryButton(
                text = stringResource(R.string.uninstall_resume_discard),
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
