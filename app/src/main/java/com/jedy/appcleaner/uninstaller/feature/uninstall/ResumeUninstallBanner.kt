package com.jedy.appcleaner.uninstaller.feature.uninstall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * CONTRACT (frozen signature). STUB — replaced by the Uninstall feature. PRD §6 item 4:
 * "Finish removing 3 apps?" on Home. Renders nothing when no unfinished batch exists.
 */
@Composable
fun ResumeUninstallBanner(
    onContinue: (batchId: Long) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
