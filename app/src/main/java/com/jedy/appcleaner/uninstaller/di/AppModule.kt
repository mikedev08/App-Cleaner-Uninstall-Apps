package com.jedy.appcleaner.uninstaller.di

import android.content.Context
import androidx.room.Room
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.LogcatAnalytics
import com.jedy.appcleaner.uninstaller.data.local.AppCleanerDatabase
import com.jedy.appcleaner.uninstaller.data.local.AppSizeDao
import com.jedy.appcleaner.uninstaller.data.local.HistoryDao
import com.jedy.appcleaner.uninstaller.data.local.InventoryDao
import com.jedy.appcleaner.uninstaller.data.local.UninstallDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Process-lifetime scope for work that must outlive a screen (inventory refresh, billing). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppCleanerDatabase =
        Room.databaseBuilder(context, AppCleanerDatabase::class.java, "appcleaner.db")
            // Pre-release: schema still moving. Replace with real migrations before V1 ships.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideInventoryDao(db: AppCleanerDatabase): InventoryDao = db.inventoryDao()
    @Provides fun provideAppSizeDao(db: AppCleanerDatabase): AppSizeDao = db.appSizeDao()
    @Provides fun provideUninstallDao(db: AppCleanerDatabase): UninstallDao = db.uninstallDao()
    @Provides fun provideHistoryDao(db: AppCleanerDatabase): HistoryDao = db.historyDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    /** Swap for a Firebase-backed implementation once google-services.json is added (PRD §2). */
    @Binds @Singleton
    abstract fun bindAnalytics(impl: LogcatAnalytics): Analytics
}
