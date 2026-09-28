package com.jedy.appcleaner.uninstaller.feature.settings

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger

/** CONTRACT (frozen signature). STUB — replaced by the Settings feature. PRD §4 Screen 13. */
@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
) = Unit
