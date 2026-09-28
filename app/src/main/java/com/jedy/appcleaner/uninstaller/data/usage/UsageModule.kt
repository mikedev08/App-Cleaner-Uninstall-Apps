package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.data.reminders.CleanupReminders
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageModule {
    @Binds @Singleton abstract fun bindUsageAccess(impl: StubUsageAccess): UsageAccess
    @Binds @Singleton abstract fun bindUsageInsights(impl: StubUsageInsights): UsageInsights
    @Binds @Singleton abstract fun bindStorageBreakdown(impl: StubStorageBreakdown): StorageBreakdown
    @Binds @Singleton abstract fun bindCleanupReminders(impl: StubCleanupReminders): CleanupReminders
}
