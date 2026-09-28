package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

/**
 * CONTRACT (frozen signature). STUB — replaced by the Home/Scan feature: the app list with the
 * All / Unused / Large tabs, search, sort, selection bar (what Home used to be).
 */
@Composable
fun AppsScreen(
    initialTab: HomeTab,
    onBack: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
    onBatchStarted: (batchId: Long) -> Unit,
) = Unit
