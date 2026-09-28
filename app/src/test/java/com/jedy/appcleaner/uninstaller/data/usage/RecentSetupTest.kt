package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSetupTest {

    private val now = 1_700_000_000_000L

    private fun installedDaysAgo(vararg days: Long) = days.mapIndexed { i, d ->
        InstalledApp(
            packageName = "p.$i",
            label = "App $i",
            versionName = "1",
            firstInstallTime = now - d * DAY_MILLIS,
            lastUpdateTime = 0,
            installerPackage = null,
            apkBytes = 1,
            storageUuid = null,
            uid = 10_000 + i,
        )
    }

    @Test
    fun `a restored phone - nearly every app installed this month`() {
        assertTrue(RecentSetup.looksRecentlySetUp(installedDaysAgo(2, 2, 2, 2, 2, 2, 2, 2, 400, 900), now))
    }

    @Test
    fun `exactly 60 percent counts`() {
        assertTrue(RecentSetup.looksRecentlySetUp(installedDaysAgo(1, 5, 10, 200, 300), now))
    }

    @Test
    fun `just under 60 percent does not`() {
        assertFalse(RecentSetup.looksRecentlySetUp(installedDaysAgo(1, 5, 200, 300, 400), now))
    }

    @Test
    fun `a phone in normal use - a few new apps a month`() {
        assertFalse(RecentSetup.looksRecentlySetUp(installedDaysAgo(3, 12, 90, 120, 200, 365, 400, 800), now))
    }

    @Test
    fun `the window is 30 days - day 31 is not recent`() {
        assertFalse(RecentSetup.looksRecentlySetUp(installedDaysAgo(31, 31, 31), now))
        assertTrue(RecentSetup.looksRecentlySetUp(installedDaysAgo(29, 29, 29), now))
    }

    @Test
    fun `no apps, or unknown install times, is not a new phone`() {
        assertFalse(RecentSetup.looksRecentlySetUp(emptyList(), now))
        val unknown = installedDaysAgo(1, 1).map { it.copy(firstInstallTime = 0) }
        assertFalse(RecentSetup.looksRecentlySetUp(unknown, now))
    }
}
