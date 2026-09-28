package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.runtime.Composable

/**
 * CONTRACT (frozen signature). STUB — replaced by the Uninstall feature. PRD §4 Screen 10.
 * [onRetry] receives a new batch (the "Try again" apps) to run through the progress screen.
 */
@Composable
fun UninstallResultScreen(
    batchId: Long,
    onDone: () -> Unit,
    onOpenUnused: () -> Unit,
    onRetry: (newBatchId: Long) -> Unit,
) = Unit
