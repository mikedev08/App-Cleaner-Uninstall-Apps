package com.jedy.appcleaner.uninstaller

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import com.jedy.appcleaner.uninstaller.core.startup.StartupTrace
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    override fun onCreate() {
        super.onCreate()
        StartupTrace.mark("Application.onCreate")
        appScope.launch {
            startups.get().forEach(AppStartup::start)
            StartupTrace.mark("AppStartups started")
        }
    }

    /** Workers (cleanup reminders) get their dependencies from Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
