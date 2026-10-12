package com.jedy.appcleaner.uninstaller.ads

/**
 * Hands out native ads, one fresh ad per screen appearance (AdMob PRD §6).
 *
 * One ad is kept preloaded so the next screen shows its ad at once. A screen that asks before
 * one is ready waits for the next load to finish; if that load fails it is dropped and stays
 * hidden until it next appears. Loaded ads nobody is waiting for become the preloaded one.
 * Kept free of SDK types so it can be tested; [NativeAdManager] plugs the SDK in. Main thread only.
 */
internal class NativeAdQueue<T : Any>(
    private val startLoad: () -> Unit,
    private val destroy: (T) -> Unit,
    private val now: () -> Long,
    private val maxAgeMs: Long = AdConfig.AD_MAX_AGE_MS,
) {
    inner class Ticket internal constructor(internal val onReady: (T) -> Unit) {
        fun cancel() {
            waiting.remove(this)
        }
    }

    private var spare: T? = null
    private var spareLoadedAt = 0L
    private var inFlight = 0
    private val waiting = ArrayDeque<Ticket>()

    /** Starts a load unless an ad is already preloaded or on its way. */
    fun preload() {
        if (freshSpare() == null && inFlight <= waiting.size) load()
    }

    /**
     * Delivers an ad to [onReady], straight away when one is preloaded. [onMiss] runs when the
     * screen has to wait, the preload-miss rate the PRD tracks.
     */
    fun request(onReady: (T) -> Unit, onMiss: () -> Unit = {}): Ticket {
        val ticket = Ticket(onReady)
        val ready = freshSpare()
        if (ready != null) {
            spare = null
            onReady(ready)
            preload()
        } else {
            onMiss()
            waiting.addLast(ticket)
            if (inFlight < waiting.size) load()
        }
        return ticket
    }

    fun onLoaded(ad: T) {
        inFlight = (inFlight - 1).coerceAtLeast(0)
        val ticket = waiting.removeFirstOrNull()
        when {
            ticket != null -> {
                ticket.onReady(ad)
                preload()
            }
            spare == null -> {
                spare = ad
                spareLoadedAt = now()
            }
            else -> destroy(ad)
        }
    }

    fun onFailed() {
        inFlight = (inFlight - 1).coerceAtLeast(0)
        // A single attempt per appearance: whoever is first in line stays hidden.
        waiting.removeFirstOrNull()
    }

    /** A load that was not sent, or whose ad was thrown away on arrival. */
    fun onDiscarded() {
        inFlight = (inFlight - 1).coerceAtLeast(0)
    }

    fun clear() {
        waiting.clear()
        spare?.let(destroy)
        spare = null
    }

    private fun load() {
        inFlight++
        startLoad()
    }

    private fun freshSpare(): T? {
        val ad = spare ?: return null
        if (now() - spareLoadedAt < maxAgeMs) return ad
        destroy(ad)
        spare = null
        return null
    }
}
