package com.jedy.appcleaner.uninstaller.data.billing

import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-wide premium flag (frozen [Premium] contract): the RevenueCat `premium` entitlement,
 * or — in debug builds only — the "Force premium (debug)" switch in Settings, so QA and
 * screenshots can exercise every premium surface before a RevenueCat key exists.
 *
 * Both inputs are read synchronously for the initial value so a subscriber on the first frame,
 * or a worker in a fresh process, never sees a false "free" before the real answer lands.
 */
@Singleton
class RevenueCatPremium @Inject constructor(
    billing: BillingRepository,
    preferences: AppPreferences,
    @ApplicationScope scope: CoroutineScope,
) : Premium {

    override val isPremium: StateFlow<Boolean> = if (!BuildConfig.DEBUG) {
        // Release: the override does not exist, so the entitlement *is* the answer.
        billing.entitlementActive
    } else {
        val forcedAtStart = runBlocking { preferences.debugForcePremium.first() }
        combine(billing.entitlementActive, preferences.debugForcePremium) { active, forced ->
            BillingRules.isPremium(active, debugBuild = true, debugForcePremium = forced)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BillingRules.isPremium(billing.entitlementActive.value, true, forcedAtStart),
        )
    }
}
