package com.jedy.appcleaner.uninstaller.data.billing

import com.jedy.appcleaner.uninstaller.data.billing.BillingRules.PeriodUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingRulesTest {

    @Test
    fun `premium is the entitlement in release, whatever the debug flag says`() {
        assertTrue(BillingRules.isPremium(entitlementActive = true, debugBuild = false, debugForcePremium = false))
        assertFalse(BillingRules.isPremium(entitlementActive = false, debugBuild = false, debugForcePremium = true))
    }

    @Test
    fun `debug override unlocks only in a debug build`() {
        assertTrue(BillingRules.isPremium(entitlementActive = false, debugBuild = true, debugForcePremium = true))
        assertFalse(BillingRules.isPremium(entitlementActive = false, debugBuild = true, debugForcePremium = false))
        assertTrue(BillingRules.isPremium(entitlementActive = true, debugBuild = true, debugForcePremium = false))
    }

    @Test
    fun `inactive cached entitlement never counts`() {
        assertFalse(BillingRules.cachedEntitlementActive(false, expiresAtMillis = null, willRenew = true, nowMillis = 0))
    }

    @Test
    fun `auto-renewing subscription stays active offline past its stored expiry`() {
        assertTrue(BillingRules.cachedEntitlementActive(true, expiresAtMillis = 1_000, willRenew = true, nowMillis = 5_000))
    }

    @Test
    fun `cancelled subscription stops at its expiry`() {
        assertTrue(BillingRules.cachedEntitlementActive(true, expiresAtMillis = 5_000, willRenew = false, nowMillis = 4_999))
        assertFalse(BillingRules.cachedEntitlementActive(true, expiresAtMillis = 5_000, willRenew = false, nowMillis = 5_000))
    }

    @Test
    fun `entitlement without expiry is treated as lifetime`() {
        assertTrue(BillingRules.cachedEntitlementActive(true, expiresAtMillis = null, willRenew = false, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun `trial periods convert to days`() {
        assertEquals(3, BillingRules.periodToDays(3, PeriodUnit.DAY))
        assertEquals(7, BillingRules.periodToDays(1, PeriodUnit.WEEK))
        assertEquals(30, BillingRules.periodToDays(1, PeriodUnit.MONTH))
        assertNull(BillingRules.periodToDays(3, PeriodUnit.UNKNOWN))
        assertNull(BillingRules.periodToDays(0, PeriodUnit.DAY))
    }

    @Test
    fun `manage subscription link names the sku only when known`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.example",
            BillingLinks.manageSubscription("com.example", null),
        )
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.example&sku=premium_weekly",
            BillingLinks.manageSubscription("com.example", "premium_weekly"),
        )
    }
}
