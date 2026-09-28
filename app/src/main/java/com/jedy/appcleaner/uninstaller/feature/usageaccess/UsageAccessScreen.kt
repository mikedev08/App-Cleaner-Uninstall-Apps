package com.jedy.appcleaner.uninstaller.feature.usageaccess

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

/**
 * CONTRACT (frozen signature). STUB — replaced by the Insights feature. PRD §4 Screen 11.
 * Calls [onClose] on "Not now", and on its own once access is detected as granted.
 */
@Composable
fun UsageAccessScreen(
    trigger: UsageAccessTrigger,
    onClose: () -> Unit,
) = Unit
