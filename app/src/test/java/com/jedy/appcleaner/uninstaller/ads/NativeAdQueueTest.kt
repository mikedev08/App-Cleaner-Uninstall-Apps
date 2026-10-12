package com.jedy.appcleaner.uninstaller.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAdQueueTest {

    private var loads = 0
    private val destroyed = mutableListOf<String>()
    private var clock = 0L
    private val queue = NativeAdQueue<String>(
        startLoad = { loads++ },
        destroy = { destroyed += it },
        now = { clock },
        maxAgeMs = 1_000L,
    )

    @Test
    fun `a preloaded ad is handed over at once and the next one starts loading`() {
        queue.preload()
        queue.onLoaded("first")
        val got = mutableListOf<String>()
        var missed = false
        queue.request(onReady = { got += it }, onMiss = { missed = true })
        assertEquals(listOf("first"), got)
        assertEquals(false, missed)
        assertEquals(2, loads)
    }

    @Test
    fun `preload does not stack loads`() {
        queue.preload()
        queue.preload()
        assertEquals(1, loads)
    }

    @Test
    fun `a request before the preload lands waits for it rather than loading twice`() {
        queue.preload()
        val got = mutableListOf<String>()
        var missed = false
        queue.request(onReady = { got += it }, onMiss = { missed = true })
        assertTrue(missed)
        assertEquals(1, loads)
        queue.onLoaded("ad")
        assertEquals(listOf("ad"), got)
    }

    @Test
    fun `every screen gets its own ad`() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        queue.request(onReady = { first += it })
        queue.request(onReady = { second += it })
        assertEquals(2, loads)
        queue.onLoaded("a")
        queue.onLoaded("b")
        assertEquals(listOf("a"), first)
        assertEquals(listOf("b"), second)
    }

    @Test
    fun `a failed load leaves its screen without an ad`() {
        val got = mutableListOf<String>()
        queue.request(onReady = { got += it })
        queue.onFailed()
        queue.onLoaded("late")
        assertTrue(got.isEmpty())
    }

    @Test
    fun `a screen that left never receives its ad, which is kept for the next one`() {
        val got = mutableListOf<String>()
        val ticket = queue.request(onReady = { got += it })
        ticket.cancel()
        queue.onLoaded("ad")
        assertTrue(got.isEmpty())
        val next = mutableListOf<String>()
        queue.request(onReady = { next += it })
        assertEquals(listOf("ad"), next)
    }

    @Test
    fun `an expired preloaded ad is thrown away`() {
        queue.preload()
        queue.onLoaded("old")
        clock = 1_000L
        val got = mutableListOf<String>()
        queue.request(onReady = { got += it })
        assertEquals(listOf("old"), destroyed)
        assertTrue(got.isEmpty())
    }

    @Test
    fun `clear destroys the preloaded ad`() {
        queue.preload()
        queue.onLoaded("spare")
        queue.clear()
        assertEquals(listOf("spare"), destroyed)
    }

    @Test
    fun `a discarded load frees its place, so preloading resumes afterwards`() {
        queue.preload()
        queue.onDiscarded()
        queue.preload()
        assertEquals(2, loads)
    }
}
