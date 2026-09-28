package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import com.jedy.appcleaner.uninstaller.data.reminders.CleanupReminders
import com.jedy.appcleaner.uninstaller.data.reminders.WorkManagerCleanupReminders
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.storage.StorageStatsBreakdown
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** The Insights feature's bindings (PRD Features 3 and 4). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UsageModule {
    @Binds @Singleton abstract fun bindUsageAccess(impl: AppOpsUsageAccess): UsageAccess
    @Binds @Singleton abstract fun bindUsageInsights(impl: UsageStatsUsageInsights): UsageInsights
    @Binds @Singleton abstract fun bindStorageBreakdown(impl: StorageStatsBreakdown): StorageBreakdown
    @Binds @Singleton abstract fun bindCleanupReminders(impl: WorkManagerCleanupReminders): CleanupReminders

    /** Access re-checks, insight/size recomputation, reminder scheduling (see [InsightsSync]). */
    @Binds @IntoSet abstract fun bindInsightsStartup(impl: InsightsSync): AppStartup
}
