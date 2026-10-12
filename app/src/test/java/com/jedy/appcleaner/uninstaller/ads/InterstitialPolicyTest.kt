package com.jedy.appcleaner.uninstaller.ads

import com.jedy.appcleaner.uninstaller.core.analytics.AdTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {

    private val policy = InterstitialPolicy(minIntervalMs = 45_000L)

    @Test
    fun `in-session shows on Home before any impression`() {
        assertTrue(policy.allows(AdTrigger.IN_SESSION, AdSurface.HOME, now = 1_000L, isShowing = false))
    }

    @Test
    fun `in-session waits 45 seconds after the last impression`() {
        policy.onImpression(now = 10_000L)
        assertFalse(policy.allows(AdTrigger.IN_SESSION, AdSurface.HOME, now = 54_999L, isShowing = false))
        assertTrue(policy.allows(AdTrigger.IN_SESSION, AdSurface.HOME, now = 55_000L, isShowing = false))
    }

    @Test
    fun `resume and after-offer ignore the cooldown`() {
        policy.onImpression(now = 10_000L)
        assertTrue(policy.allows(AdTrigger.RESUME, AdSurface.HOME, now = 11_000L, isShowing = false))
        assertTrue(policy.allows(AdTrigger.AFTER_OFFER, AdSurface.HOME, now = 11_000L, isShowing = false))
    }

    @Test
    fun `an impression from any trigger restarts the in-session clock`() {
        policy.onImpression(now = 100_000L)
        policy.onImpression(now = 150_000L) // say, a resume
        assertFalse(policy.allows(AdTrigger.IN_SESSION, AdSurface.HOME, now = 160_000L, isShowing = false))
        assertTrue(policy.allows(AdTrigger.IN_SESSION, AdSurface.HOME, now = 195_000L, isShowing = false))
    }

    @Test
    fun `nothing shows over an ad already on screen`() {
        AdTrigger.entries.forEach { trigger ->
            assertFalse(policy.allows(trigger, AdSurface.HOME, now = 0L, isShowing = true))
        }
    }

    @Test
    fun `never on the uninstall path, onboarding, the paywall or the Usage access disclosure`() {
        val blocked = listOf(
            AdSurface.ONBOARDING,
            AdSurface.PAYWALL,
            AdSurface.USAGE_ACCESS,
            AdSurface.REVIEW,
            AdSurface.UNINSTALL,
        )
        blocked.forEach { surface ->
            assertFalse(surface.name, policy.allows(AdTrigger.RESUME, surface, now = 0L, isShowing = false))
            assertFalse(surface.name, policy.allows(AdTrigger.IN_SESSION, surface, now = 0L, isShowing = false))
        }
    }

    @Test
    fun `in-session only on Home, resume everywhere else it is allowed`() {
        assertEquals(listOf(AdSurface.HOME), AdSurface.entries.filter { it.allowsInSession })
        listOf(AdSurface.RESULT, AdSurface.SCAN, AdSurface.APPS, AdSurface.HISTORY, AdSurface.SETTINGS).forEach {
            assertTrue(it.name, policy.allows(AdTrigger.RESUME, it, now = 0L, isShowing = false))
            assertFalse(it.name, policy.allows(AdTrigger.IN_SESSION, it, now = 0L, isShowing = false))
        }
    }
}
