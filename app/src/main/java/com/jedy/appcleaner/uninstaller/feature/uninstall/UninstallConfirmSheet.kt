package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.model.HomeTab

/**
 * CONTRACT (frozen signature). STUB — replaced by the Uninstall feature. PRD §4 Screen 8.
 * A modal bottom sheet; on confirm it creates the batch and calls [onStarted] with its id.
 */
@Composable
fun UninstallConfirmSheet(
    packages: List<String>,
    sourceTab: HomeTab,
    onDismiss: () -> Unit,
    onStarted: (batchId: Long) -> Unit,
) = Unit
