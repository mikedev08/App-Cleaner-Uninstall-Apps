package com.jedy.appcleaner.uninstaller.data.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds @Singleton abstract fun bindCleanupScan(impl: DefaultCleanupScan): CleanupScan
}
