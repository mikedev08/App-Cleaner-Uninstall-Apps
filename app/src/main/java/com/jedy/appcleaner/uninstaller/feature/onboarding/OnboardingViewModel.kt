package com.jedy.appcleaner.uninstaller.feature.onboarding

import androidx.lifecycle.ViewModel
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Onboarding's analytics. `onboarding_start` and `onboarding_complete` are logged by
 * MainViewModel, which owns the start route and the completion write; this logs the funnel
 * steps in between (PRD §9).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val analytics: Analytics,
) : ViewModel() {

    /** Survives rotation with the ViewModel, so swiping back and forth counts each slide once. */
    private val loggedSteps = mutableSetOf<Int>()

    /** @param step 1-based slide number, as PRD §9 `step_number` expects. */
    fun onStepViewed(step: Int, language: AppLanguage) {
        if (loggedSteps.add(step)) {
            analytics.log(AnalyticsEvent.OnboardingStepViewed(stepNumber = step, languageSelected = language.tag))
        }
    }
}
