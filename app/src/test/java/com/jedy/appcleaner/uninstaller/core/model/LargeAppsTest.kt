package com.jedy.appcleaner.uninstaller.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeAppsTest {

    private fun app(apk: Long) = InstalledApp(
        packageName = "p.a", label = "A", versionName = null, firstInstallTime = 0, lastUpdateTime = 0,
        installerPackage = null, apkBytes = apk, storageUuid = null, uid = 0,
    )

    @Test fun `threshold is inclusive at 250 MB as displayed`() {
        assertTrue(LargeApps.isLarge(250_000_000L))
        assertFalse(LargeApps.isLarge(249_999_999L))
    }

    @Test fun `best known size prefers the measured total over the apk`() {
        val a = app(apk = 40_000_000L)
        val measured = AppSize(appBytes = 100_000_000L, dataBytes = 120_000_000L, cacheBytes = 30_000_000L, measuredAt = 0)
        assertEquals(250_000_000L, a.bestKnownBytes(measured))
        assertEquals(40_000_000L, a.bestKnownBytes(null as AppSize?))
        assertTrue(LargeApps.isLarge(a, mapOf("p.a" to measured)))
        assertFalse(LargeApps.isLarge(a, emptyMap()))
    }
}
