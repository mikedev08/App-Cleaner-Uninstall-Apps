package com.jedy.appcleaner.uninstaller

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AppCleanerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var startups: Set<@JvmSuppressWildcards AppStartup>

    override fun onCreate() {
        super.onCreate()
        startups.forEach(AppStartup::start)
    }

    /** Workers (cleanup reminders) get their dependencies from Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
