package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

/** CONTRACT (frozen signature). STUB body — replaced by the Home feature. PRD §4 Screens 4–7. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
    onBatchStarted: (batchId: Long) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Home") }
}
