package com.jedy.appcleaner.uninstaller.core.startup

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Process-start hook. A feature that must run at launch (configure RevenueCat, observe prefs to
 * schedule workers, register package receivers) contributes one with `@Binds @IntoSet` in its own
 * module, so no feature ever has to edit [com.jedy.appcleaner.uninstaller.AppCleanerApplication].
 * Keep [start] cheap: launch coroutines on the application scope rather than blocking.
 */
interface AppStartup {
    fun start()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppStartupModule {
    /** Declares the set so it resolves even when no feature has contributed yet. */
    @Multibinds abstract fun appStartups(): Set<AppStartup>
}
