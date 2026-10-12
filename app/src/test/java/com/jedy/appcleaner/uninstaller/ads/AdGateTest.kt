package com.jedy.appcleaner.uninstaller.ads

import com.jedy.appcleaner.uninstaller.core.analytics.AdFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdGateTest {

    @Test
    fun `pro suppresses ads during and after onboarding`() {
        assertEquals(AdGate.SUPPRESSED_PRO, adGate(isPro = true, pastLanguageStep = false))
        assertEquals(AdGate.SUPPRESSED_PRO, adGate(isPro = true, pastLanguageStep = true))
    }

    @Test
    fun `a first-run free user waits for the language step`() {
        assertEquals(AdGate.WAIT_FOR_LANGUAGE, adGate(isPro = false, pastLanguageStep = false))
    }

    @Test
    fun `a free user past the language step gets ads`() {
        assertEquals(AdGate.ALLOW, adGate(isPro = false, pastLanguageStep = true))
    }

    @Test
    fun `a suppressed placement is logged once per visit`() {
        val log = SuppressionLog()
        assertTrue(log.shouldLog(AdFormat.NATIVE, "home"))
        assertFalse(log.shouldLog(AdFormat.NATIVE, "home"))
        assertTrue(log.shouldLog(AdFormat.NATIVE, "apps"))
        assertTrue(log.shouldLog(AdFormat.INTERSTITIAL, "home"))
        log.newVisit()
        assertTrue(log.shouldLog(AdFormat.NATIVE, "home"))
    }
}
