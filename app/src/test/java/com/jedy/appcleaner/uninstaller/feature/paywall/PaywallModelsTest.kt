package com.jedy.appcleaner.uninstaller.feature.paywall

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.data.billing.OfferState
import com.jedy.appcleaner.uninstaller.data.billing.WeeklyOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallModelsTest {

    private fun loaded(trialDays: Int?) = OfferState.Loaded(WeeklyOffer("$2.99", trialDays, "premium_weekly:weekly"))

    @Test
    fun `trial-eligible account sees the free trial`() {
        val plan = PaywallPlan.from(loaded(trialDays = 3), debugBuild = false)
        assertEquals(PaywallPlan.Trial(price = "$2.99", trialDays = 3), plan)
        assertTrue(plan.canPurchase)
    }

    @Test
    fun `account that used its trial is never promised free days`() {
        val plan = PaywallPlan.from(loaded(trialDays = null), debugBuild = false)
        assertEquals(PaywallPlan.Weekly(price = "$2.99"), plan)
        assertTrue(plan.canPurchase)
    }

    @Test
    fun `offline offering shows the connect card and cannot purchase`() {
        val plan = PaywallPlan.from(OfferState.Unavailable(offline = true), debugBuild = true)
        assertEquals(PaywallPlan.Offline, plan)
        assertFalse(plan.canPurchase)
    }

    @Test
    fun `store without an offering shows the neutral card`() {
        assertEquals(PaywallPlan.Unavailable, PaywallPlan.from(OfferState.Unavailable(offline = false), debugBuild = false))
    }

    @Test
    fun `loading cannot purchase`() {
        assertFalse(PaywallPlan.from(OfferState.Loading, debugBuild = true).canPurchase)
    }

    @Test
    fun `not configured unlocks locally in debug only`() {
        val debug = PaywallPlan.from(OfferState.NotConfigured, debugBuild = true)
        val release = PaywallPlan.from(OfferState.NotConfigured, debugBuild = false)
        assertEquals(PaywallPlan.NotConfigured(debugUnlock = true), debug)
        assertTrue(debug.canPurchase)
        assertEquals(PaywallPlan.NotConfigured(debugUnlock = false), release)
        assertFalse(release.canPurchase)
    }

    @Test
    fun `largest finding sums the top ten by best available size`() {
        val apps = (1..12).map { app("p$it", apkBytes = it * 100L) }
        // p1 has a measured total far above its APK size, so it enters the top ten.
        val sizes = mapOf("p1" to AppSize(appBytes = 5_000, dataBytes = 0, cacheBytes = 0, measuredAt = 0))
        val headline = PaywallHeadline.largest(apps, sizes)
        val expected = 5_000L + (4..12).sumOf { it * 100L }
        assertEquals(PaywallHeadline.Large(count = 10, bytes = expected), headline)
    }

    @Test
    fun `largest finding with fewer apps counts what exists`() {
        val headline = PaywallHeadline.largest(listOf(app("a", 10), app("b", 20)), emptyMap())
        assertEquals(PaywallHeadline.Large(count = 2, bytes = 30), headline)
    }

    @Test
    fun `no apps means the default headline`() {
        assertEquals(PaywallHeadline.Default, PaywallHeadline.largest(emptyList(), emptyMap()))
    }

    private fun app(packageName: String, apkBytes: Long) = InstalledApp(
        packageName = packageName,
        label = packageName,
        versionName = null,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        installerPackage = null,
        apkBytes = apkBytes,
        storageUuid = null,
        uid = 0,
    )
}
