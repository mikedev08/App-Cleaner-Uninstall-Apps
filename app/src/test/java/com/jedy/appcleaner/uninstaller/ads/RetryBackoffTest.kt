package com.jedy.appcleaner.uninstaller.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `backs off 30, 60 and 90 seconds`() {
        assertEquals(30_000L, retryDelayMs(1))
        assertEquals(60_000L, retryDelayMs(2))
        assertEquals(90_000L, retryDelayMs(3))
    }

    @Test
    fun `stops after the maximum consecutive failures`() {
        assertNull(retryDelayMs(AdConfig.MAX_CONSECUTIVE_FAILS + 1))
    }

    @Test
    fun `no retry without a failure`() {
        assertNull(retryDelayMs(0))
    }

    @Test
    fun `never waits longer than the cap`() {
        (1..AdConfig.MAX_CONSECUTIVE_FAILS).forEach { fails ->
            assertTrue(retryDelayMs(fails)!! <= AdConfig.RETRY_MAX_DELAY_MS)
        }
    }
}
