package com.jedy.appcleaner.uninstaller.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GDPR / US-state consent through Google's User Messaging Platform (AdMob PRD §8, §11). UMP
 * decides whether a form is needed at all; outside the regions that require one nothing shows.
 * An error (offline, for one) never blocks the app: the ad SDK starts anyway.
 */
internal class ConsentManager(context: Context, private val logger: AdEventLogger) {

    private val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True when the user must be able to reopen the form, from Settings. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Refreshes consent, shows the form if it is needed, then calls [onComplete] once. */
    fun requestAndShow(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let(::logError)
                    refresh()
                    onComplete()
                }
            },
            { error ->
                logError(error)
                refresh()
                onComplete()
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let(::logError)
            refresh()
        }
    }

    private fun refresh() {
        _privacyOptionsRequired.value = consentInfo.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    // The code only: the message is free text, which no event may carry (PRD §9).
    private fun logError(error: FormError) = logger.log(AnalyticsEvent.ConsentError(error.errorCode))
}
