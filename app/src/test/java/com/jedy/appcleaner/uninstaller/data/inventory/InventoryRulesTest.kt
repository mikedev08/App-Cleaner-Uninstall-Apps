package com.jedy.appcleaner.uninstaller.data.inventory

import android.content.pm.ApplicationInfo
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {

    private val own = "com.jedy.appcleaner.uninstaller"

    @Test
    fun `user app without system flag is kept`() {
        assertTrue(InventoryRules.isUserApp(appFlags = 0, packageName = "com.example.game", ownPackage = own))
    }

    @Test
    fun `system and updated system apps are hidden`() {
        val system = ApplicationInfo.FLAG_SYSTEM
        val updatedSystem = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        assertFalse(InventoryRules.isUserApp(system, "com.android.chrome", own))
        assertFalse(InventoryRules.isUserApp(updatedSystem, "com.google.android.youtube", own))
    }

    @Test
    fun `our own package is always excluded`() {
        assertFalse(InventoryRules.isUserApp(appFlags = 0, packageName = own, ownPackage = own))
    }

    @Test
    fun `apk bytes sum the base apk and every split`() {
        val sizes = mapOf("/base.apk" to 100L, "/split_a.apk" to 20L, "/split_b.apk" to 3L)
        val total = InventoryRules.apkBytes("/base.apk", arrayOf("/split_a.apk", "/split_b.apk")) { sizes.getValue(it) }
        assertEquals(123L, total)
    }

    @Test
    fun `apk bytes treat missing or unreadable paths as zero`() {
        assertEquals(0L, InventoryRules.apkBytes(null, null) { error("never called") })
        val total = InventoryRules.apkBytes("/base.apk", arrayOf("/broken.apk")) { path ->
            if (path == "/broken.apk") throw SecurityException("denied") else 50L
        }
        assertEquals(50L, total)
    }

    @Test
    fun `diff upserts new and changed apps and removes missing ones`() {
        val kept = app("com.kept")
        val updated = app("com.updated", version = "1.0")
        val removed = app("com.removed")
        val added = app("com.added")
        val diff = InventoryRules.diff(
            cached = listOf(kept, updated, removed),
            live = listOf(kept, updated.copy(versionName = "2.0", lastUpdateTime = 99), added),
        )
        assertEquals(setOf("com.updated", "com.added"), diff.upserts.map { it.packageName }.toSet())
        assertEquals(listOf("com.removed"), diff.removedPackages)
    }

    @Test
    fun `diff of identical lists is empty`() {
        val apps = listOf(app("a"), app("b"))
        assertTrue(InventoryRules.diff(apps, apps.reversed()).isEmpty)
    }

    @Test
    fun `fewer than five user apps is incomplete`() {
        assertTrue(InventoryRules.isIncomplete(0))
        assertTrue(InventoryRules.isIncomplete(29))
        assertFalse(InventoryRules.isIncomplete(30))
    }

    @Test
    fun `receiver patches replace or drop a single package`() {
        val list = listOf(app("com.b", label = "Beta"), app("com.a", label = "alpha"))
        val patched = InventoryRules.upsert(list, app("com.b", label = "Beta", version = "9"))
        assertEquals(listOf("com.a", "com.b"), patched.map { it.packageName })
        assertEquals("9", patched.last().versionName)

        val added = InventoryRules.upsert(list, app("com.c", label = "Charlie"))
        assertEquals(3, added.size)

        assertEquals(listOf("com.b"), InventoryRules.remove(patched, "com.a").map { it.packageName })
        assertSame(list, InventoryRules.remove(list, "com.missing"))
    }

    @Test
    fun `entity mapping round trips`() {
        val original = app("com.round", version = "3.1").copy(installerPackage = InstalledApp.PLAY_STORE_PACKAGE, storageUuid = "uuid")
        assertEquals(original, original.toEntity(refreshedAt = 42).toModel())
    }

    private fun app(pkg: String, label: String = pkg, version: String? = "1") = InstalledApp(
        packageName = pkg,
        label = label,
        versionName = version,
        firstInstallTime = 1,
        lastUpdateTime = 2,
        installerPackage = null,
        apkBytes = 10,
        storageUuid = null,
        uid = 10_000,
    )
}
