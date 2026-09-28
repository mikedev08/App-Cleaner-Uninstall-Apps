package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

/**
 * CONTRACT (frozen signature). STUB — replaced by the Home/Scan feature.
 * Scanning animation -> "You can free up X GB" result with buckets.
 */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onOpenApps: (HomeTab) -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
) = Unit
