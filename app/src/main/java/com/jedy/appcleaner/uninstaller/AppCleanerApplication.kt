package com.jedy.appcleaner.uninstaller

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import com.jedy.appcleaner.uninstaller.core.startup.StartupTrace
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class AppCleanerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * A [Provider], not the set itself: resolving the set builds every startup's dependency graph
     * (billing's persisted entitlement, Room, WorkManager, usage services), which must not happen
     * on the main thread while the splash is up.
     */
    @Inject lateinit var startups: Provider<Set<@JvmSuppressWildcards AppStartup>>

    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    /** Completed by [MainActivity] once the first real frame is on screen. */
    private val firstFrame = CompletableDeferred<Unit>()

    fun onFirstFrameDrawn() {
        firstFrame.complete(Unit)
    }

    override fun onCreate() {
        super.onCreate()
        StartupTrace.mark("Application.onCreate")
        appScope.launch {
            // The rescans compete with the first frame for CPU, so they wait for it. A process
            // started without UI (a reminder worker, a package broadcast) starts them after 2 s.
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { firstFrame.await() }
            startups.get().forEach(AppStartup::start)
            StartupTrace.mark("AppStartups started")
        }
    }

    /** Workers (cleanup reminders) get their dependencies from Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private companion object {
        const val FIRST_FRAME_TIMEOUT_MS = 2_000L
    }
}
