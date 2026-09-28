package com.jedy.appcleaner.uninstaller.core.model

/**
 * One user-installed app, as the inventory knows it (PRD Feature 1).
 *
 * [apkBytes] is the base APK plus every split: readable without any permission, so it is the
 * fallback size everywhere. Full app + data + cache sizes come from [AppSize] (free, needs Usage
 * Access); every screen shows [bestKnownBytes].
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

/** StorageStatsManager breakdown for one app (free for everyone, needs Usage Access). */
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

/**
 * The one unused rule: an app counts as unused when it has not been opened for this many days.
 * Fixed by the owner (no 30 / 60 / 90 choice any more): Scan, Home, the Apps "Unused apps" filter,
 * reminders and the paywall all use this constant. Any value stored by older builds is ignored.
 */
const val UNUSED_THRESHOLD_DAYS: Int = 30

/** An app the Unused finder flagged. [lastUsedAt] null = no record inside the retention window. */
data class UnusedApp(
    val app: InstalledApp,
    val lastUsedAt: Long?,
)

/**
 * The Apps screen filters, in display order: All · Unused · Large · Cache (design review §2.6 —
 * the same names Home and Scan use). [CACHE] lists apps by cache size.
 */
/** [analyticsName] is the user-facing name in events (PRD §9): Large is "heavy", Cache is "temp_files". */
enum class HomeTab(val analyticsName: String) { ALL("all"), UNUSED("unused"), LARGE("heavy"), CACHE("temp_files") }

/**
 * The one definition of a "Large" app (design review §2A / priority #0). Home's category row, the
 * Scan result and the Apps "Large" filter must all count with [isLarge], so the three numbers
 * always agree. Never re-derive a threshold elsewhere, and never use a share-of-disk rule.
 *
 * **Which bytes to pass:** the app's *best known size* — the measured app + data + cache total
 * ([AppSize.totalBytes]) when StorageStatsManager has one, else the APK bytes
 * ([InstalledApp.apkBytes]). Use [bestKnownBytes] rather than computing it by hand. This is the
 * same number `ScanMath.sizeOf` uses for the scan headline.
 */
object LargeApps {
    /** 250 MB in the decimal units Android's size formatter shows, so "250 MB" is exactly the line. */
    const val THRESHOLD_BYTES: Long = 250_000_000L

    fun isLarge(bestKnownBytes: Long): Boolean = bestKnownBytes >= THRESHOLD_BYTES

    fun isLarge(app: InstalledApp, size: AppSize?): Boolean = isLarge(app.bestKnownBytes(size))

    fun isLarge(app: InstalledApp, sizes: Map<String, AppSize>): Boolean = isLarge(app.bestKnownBytes(sizes))
}

/** Measured app + data + cache total when known, else the APK bytes. What [LargeApps] judges. */
fun InstalledApp.bestKnownBytes(size: AppSize?): Long = size?.totalBytes ?: apkBytes

/** [bestKnownBytes] looked up in a package-name → [AppSize] map. */
fun InstalledApp.bestKnownBytes(sizes: Map<String, AppSize>): Long = bestKnownBytes(sizes[packageName])

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
            HomeTab.CACHE -> SIZE
        }
    }
}

/** Where a paywall / premium surface was opened from (PRD §9 trigger_source). */
enum class PaywallSource(val value: String) {
    ONBOARDING("onboarding"),
    HOME("home"),
    SCAN_RESULT("scan_result"),
    UNUSED_TAB("unused_tab"),
    /** No longer opened: Large and Cache are free. Kept so old analytics values still parse. */
    LARGE_TAB("large_tab"),
    /** No longer opened: the details sheet's size breakdown is free. Kept for analytics. */
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
    LARGE_TAB("heavy_tab"),
    CACHE_TAB("temp_files_tab"),
    DETAILS_SHEET("details_sheet"),
    STORAGE_CARD("home_card"),
    SCAN("scan"),
    SETTINGS("settings");

    companion object {
        fun from(value: String?): UsageAccessTrigger = entries.firstOrNull { it.value == value } ?: UNUSED_TAB
    }
}
