package com.jedy.appcleaner.uninstaller.core.model

/**
 * One user-installed app, as the inventory knows it (PRD Feature 1).
 *
 * [apkBytes] is the base APK plus every split: readable without any permission, so it is the
 * free-tier size everywhere. Full app + data + cache sizes come from [AppSize] (premium).
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    /** `getInstallSourceInfo().installingPackageName`; null when unknown or sideloaded. */
    val installerPackage: String?,
    val apkBytes: Long,
    /** `ApplicationInfo.storageUuid` as a string; query sizes on this volume, never UUID_DEFAULT. */
    val storageUuid: String?,
    val uid: Int,
) {
    val isFromPlay: Boolean get() = installerPackage == PLAY_STORE_PACKAGE

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

/** StorageStatsManager breakdown for one app (premium, needs Usage Access). */
data class AppSize(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val measuredAt: Long,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/** Whole-device storage for the Home storage card. Needs no permission. */
data class DeviceStorage(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

/** An app the Unused finder flagged. [lastUsedAt] null = no record inside the retention window. */
data class UnusedApp(
    val app: InstalledApp,
    val lastUsedAt: Long?,
)

enum class HomeTab { ALL, UNUSED, LARGE }

/** PRD §4, Screen 4 sort menu. [LAST_USED] is premium. */
enum class SortOrder {
    SIZE,
    NAME,
    INSTALL_DATE,
    LAST_UPDATED,
    LAST_USED;

    val isPremium: Boolean get() = this == LAST_USED

    companion object {
        fun defaultFor(tab: HomeTab): SortOrder = when (tab) {
            HomeTab.ALL -> SIZE
            HomeTab.UNUSED -> LAST_USED
            HomeTab.LARGE -> SIZE
        }
    }
}

/** Where a paywall / premium surface was opened from (PRD §9 trigger_source). */
enum class PaywallSource(val value: String) {
    ONBOARDING("onboarding"),
    UNUSED_TAB("unused_tab"),
    LARGE_TAB("large_tab"),
    DETAILS_SHEET("details_sheet"),
    REMINDER_TOGGLE("reminder_toggle"),
    SORT_MENU("sort_menu"),
    SETTINGS("settings");

    companion object {
        fun from(value: String?): PaywallSource = entries.firstOrNull { it.value == value } ?: SETTINGS
    }
}

/** What sent the user to the Usage Access disclosure (PRD §9). */
enum class UsageAccessTrigger(val value: String) {
    UNUSED_TAB("unused_tab"),
    LARGE_TAB("large_tab"),
    STORAGE_CARD("storage_card"),
    SETTINGS("settings");

    companion object {
        fun from(value: String?): UsageAccessTrigger = entries.firstOrNull { it.value == value } ?: UNUSED_TAB
    }
}
