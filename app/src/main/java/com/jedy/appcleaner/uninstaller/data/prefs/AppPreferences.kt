package com.jedy.appcleaner.uninstaller.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appcleaner_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Small, local app state. Nothing here ever leaves the device (PRD §2, on-device inventory).
 * Every key the V1 feature set needs is declared here so features never race to add one.
 */
@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val onboardingPaywallShown = booleanPreferencesKey("onboarding_paywall_shown")
        val languageTag = stringPreferencesKey("language_tag")
        val themeMode = stringPreferencesKey("theme_mode")
        // "size_display" (the old "Show app size as" setting) may still be on disk; it is never read.
        val unusedThresholdDays = intPreferencesKey("unused_threshold_days")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val remindersInitialised = booleanPreferencesKey("reminders_initialised")
        val lastReminderCount = intPreferencesKey("last_reminder_count")
        val lastReminderAt = longPreferencesKey("last_reminder_at")
        val lastNudgeAt = longPreferencesKey("last_nudge_at")
        val premiumEndedBannerSeen = booleanPreferencesKey("premium_ended_banner_seen")
        val wasPremium = booleanPreferencesKey("was_premium")
        val debugForcePremium = booleanPreferencesKey("debug_force_premium")
        fun sort(tab: HomeTab) = stringPreferencesKey("sort_${tab.name.lowercase()}")
    }

    private val data get() = context.dataStore.data

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    // Onboarding
    val onboardingComplete: Flow<Boolean> = data.map { it[Keys.onboardingComplete] ?: false }
    suspend fun isOnboardingComplete(): Boolean = onboardingComplete.first()
    suspend fun setOnboardingComplete(value: Boolean) = set(Keys.onboardingComplete, value)

    /** PRD §3: the paywall shows at most once during onboarding, never again on cold start. */
    val onboardingPaywallShown: Flow<Boolean> = data.map { it[Keys.onboardingPaywallShown] ?: false }
    suspend fun setOnboardingPaywallShown(value: Boolean) = set(Keys.onboardingPaywallShown, value)

    // Appearance
    val languageTag: Flow<String?> = data.map { it[Keys.languageTag] }
    suspend fun setLanguageTag(tag: String) = set(Keys.languageTag, tag)

    val themeMode: Flow<ThemeMode> = data.map { prefs ->
        prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.themeMode, mode.name)

    // Sorting (PRD §4, Screen 4: persists per tab)
    fun sortOrder(tab: HomeTab): Flow<SortOrder> = data.map { prefs ->
        prefs[Keys.sort(tab)]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.defaultFor(tab)
    }
    suspend fun setSortOrder(tab: HomeTab, order: SortOrder) = set(Keys.sort(tab), order.name)

    // Unused finder + reminders (PRD Features 3 and 4)
    val unusedThresholdDays: Flow<Int> = data.map { it[Keys.unusedThresholdDays] ?: DEFAULT_UNUSED_DAYS }
    suspend fun setUnusedThresholdDays(days: Int) = set(Keys.unusedThresholdDays, days)

    val remindersEnabled: Flow<Boolean> = data.map { it[Keys.remindersEnabled] ?: false }
    suspend fun setRemindersEnabled(value: Boolean) = set(Keys.remindersEnabled, value)

    /** Reminders switch on automatically once, on the first premium unlock (PRD Feature 4). */
    val remindersInitialised: Flow<Boolean> = data.map { it[Keys.remindersInitialised] ?: false }
    suspend fun setRemindersInitialised(value: Boolean) = set(Keys.remindersInitialised, value)

    val lastReminderCount: Flow<Int> = data.map { it[Keys.lastReminderCount] ?: 0 }
    val lastReminderAt: Flow<Long> = data.map { it[Keys.lastReminderAt] ?: 0L }
    suspend fun recordReminder(count: Int, at: Long) {
        context.dataStore.edit {
            it[Keys.lastReminderCount] = count
            it[Keys.lastReminderAt] = at
        }
    }

    /** Free users: at most one generic nudge per 30 days (PRD §2). */
    val lastNudgeAt: Flow<Long> = data.map { it[Keys.lastNudgeAt] ?: 0L }
    suspend fun setLastNudgeAt(at: Long) = set(Keys.lastNudgeAt, at)

    // Premium lifecycle (PRD §6 item 18)
    val wasPremium: Flow<Boolean> = data.map { it[Keys.wasPremium] ?: false }
    suspend fun setWasPremium(value: Boolean) = set(Keys.wasPremium, value)
    val premiumEndedBannerSeen: Flow<Boolean> = data.map { it[Keys.premiumEndedBannerSeen] ?: false }
    suspend fun setPremiumEndedBannerSeen(value: Boolean) = set(Keys.premiumEndedBannerSeen, value)

    /** Debug builds only: lets QA and screenshots see premium without a RevenueCat key. */
    val debugForcePremium: Flow<Boolean> = data.map { it[Keys.debugForcePremium] ?: false }
    suspend fun setDebugForcePremium(value: Boolean) = set(Keys.debugForcePremium, value)

    companion object {
        const val DEFAULT_UNUSED_DAYS = 60
        val UNUSED_THRESHOLDS = listOf(30, 60, 90)
    }
}
