package com.jedy.appcleaner.uninstaller.feature.onboarding

import androidx.compose.runtime.Composable
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage

/**
 * CONTRACT (frozen signature). STUB — replaced by the Onboarding feature. PRD §3.
 * Language selector -> 3 slides -> notification prompt on "Get Started" -> [onFinished].
 */
@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFinished: (skipped: Boolean) -> Unit,
) = Unit
