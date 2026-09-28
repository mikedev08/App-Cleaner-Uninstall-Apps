package com.jedy.appcleaner.uninstaller.data.uninstall

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UninstallModule {
    @Binds @Singleton abstract fun bindPackageRemover(impl: PackageInstallerRemover): PackageRemover
    @Binds @Singleton abstract fun bindIconSnapshotStore(impl: FileIconSnapshotStore): IconSnapshotStore
    @Binds @Singleton abstract fun bindAppWarnings(impl: SystemAppWarnings): AppWarnings

    companion object {
        @Provides fun provideClock(): Clock = Clock(System::currentTimeMillis)
    }
}
