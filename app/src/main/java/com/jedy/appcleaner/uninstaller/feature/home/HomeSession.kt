package com.jedy.appcleaner.uninstaller.feature.home

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-process analytics sampling for Home (PRD §9). `home_viewed` is once per session and
 * `app_selected` is sampled to the first selection per session. A ViewModel is recreated every
 * time Home re-enters the back stack, so the flags live in this singleton instead.
 */
@Singleton
class HomeSession @Inject constructor() {
    private val homeViewed = AtomicBoolean(false)
    private val appSelected = AtomicBoolean(false)

    /** True exactly once per process: the caller that gets true logs `home_viewed`. */
    fun claimHomeViewed(): Boolean = homeViewed.compareAndSet(false, true)

    /** True exactly once per process: the caller that gets true logs `app_selected`. */
    fun claimFirstSelection(): Boolean = appSelected.compareAndSet(false, true)
}
