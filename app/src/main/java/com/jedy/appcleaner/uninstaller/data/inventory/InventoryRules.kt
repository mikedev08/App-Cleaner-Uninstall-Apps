package com.jedy.appcleaner.uninstaller.data.inventory

import android.content.pm.ApplicationInfo
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.local.InventoryAppEntity

/**
 * The pure rules behind the inventory (PRD Feature 1), kept free of Android framework calls so
 * they run under plain JUnit. [PackageManagerAppInventory] does the I/O and delegates every
 * decision here.
 */
internal object InventoryRules {

    /**
     * PRD §6 item 11: with `QUERY_ALL_PACKAGES` a phone reports hundreds of packages (system ones
     * included); with it stripped, package-visibility filtering leaves only a handful. Counting
     * *all* visible packages, not user apps, keeps a fresh phone with three installs from being
     * told apps are hidden.
     */
    const val INCOMPLETE_THRESHOLD = 30

    /**
     * PRD Feature 1: user-installed only. `FLAG_SYSTEM` stays set on *updated* system apps too, so
     * this one check also hides them, as V1 requires. App Cleaner itself is never listed — it can't
     * uninstall itself through the queue, and offering it would be a trap.
     */
    fun isUserApp(appFlags: Int, packageName: String, ownPackage: String): Boolean =
        (appFlags and ApplicationInfo.FLAG_SYSTEM) == 0 && packageName != ownPackage

    /**
     * PRD Feature 1 apk_bytes: base APK plus every split. [lengthOf] is `File(path).length()` in
     * production; an unreadable path counts as 0 rather than failing the whole app.
     */
    fun apkBytes(sourceDir: String?, splitSourceDirs: Array<String>?, lengthOf: (String) -> Long): Long =
        (listOfNotNull(sourceDir) + splitSourceDirs.orEmpty()).sumOf { path ->
            runCatching { lengthOf(path) }.getOrDefault(0L).coerceAtLeast(0L)
        }

    /**
     * PRD Feature 1 cache-first refresh: compares the live scan with the Room snapshot. A row is
     * rewritten only when something about it changed (new app, update, relabel, resize), so a
     * no-op refresh touches no rows at all.
     */
    fun diff(cached: List<InstalledApp>, live: List<InstalledApp>): InventoryDiff {
        val cachedByPackage = cached.associateBy { it.packageName }
        val livePackages = live.mapTo(HashSet(live.size)) { it.packageName }
        return InventoryDiff(
            upserts = live.filter { cachedByPackage[it.packageName] != it },
            removedPackages = cached.map { it.packageName }.filterNot { it in livePackages },
        )
    }

    fun isIncomplete(visiblePackageCount: Int): Boolean = visiblePackageCount < INCOMPLETE_THRESHOLD

    /**
     * Stable emission order (label, then package) so observers see deterministic lists. Home
     * applies its own sort on top; this only keeps diffs and tests predictable.
     */
    fun ordered(apps: Collection<InstalledApp>): List<InstalledApp> =
        apps.sortedWith(compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })

    /** Live-receiver patch: insert or replace one app. */
    fun upsert(apps: List<InstalledApp>, app: InstalledApp): List<InstalledApp> =
        ordered(apps.filterNot { it.packageName == app.packageName } + app)

    /** Live-receiver patch: drop one package. Returns the same list when it wasn't present. */
    fun remove(apps: List<InstalledApp>, packageName: String): List<InstalledApp> =
        if (apps.none { it.packageName == packageName }) apps else apps.filterNot { it.packageName == packageName }
}

internal data class InventoryDiff(
    val upserts: List<InstalledApp>,
    val removedPackages: List<String>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && removedPackages.isEmpty()
}

internal fun InventoryAppEntity.toModel() = InstalledApp(
    packageName = packageName,
    label = label,
    versionName = versionName,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    installerPackage = installerPackage,
    apkBytes = apkBytes,
    storageUuid = storageUuid,
    uid = uid,
)

internal fun InstalledApp.toEntity(refreshedAt: Long) = InventoryAppEntity(
    packageName = packageName,
    label = label,
    versionName = versionName,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    installerPackage = installerPackage,
    apkBytes = apkBytes,
    storageUuid = storageUuid,
    uid = uid,
    refreshedAt = refreshedAt,
)
