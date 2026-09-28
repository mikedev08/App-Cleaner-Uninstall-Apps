package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * CONTRACT (frozen signatures). STUBS — replaced by the Insights feature. PRD §4 Screens 5–6.
 * Home embeds these as the Unused and Large tab bodies. They read/write SelectionStore directly.
 *
 * @param searchQuery Home's inline search text; filter rows by label and package name.
 * @param onRequestAccess open the Usage Access disclosure.
 * @param onUnlock open the paywall.
 * @param onOpenDetails open Home's App Details sheet for a package.
 */
@Composable
fun UnusedTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onUnlock: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) = Unit

@Composable
fun LargeTab(
    searchQuery: String,
    onRequestAccess: () -> Unit,
    onUnlock: () -> Unit,
    onOpenDetails: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
