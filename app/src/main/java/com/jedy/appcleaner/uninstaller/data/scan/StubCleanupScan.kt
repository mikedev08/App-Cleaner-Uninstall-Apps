package com.jedy.appcleaner.uninstaller.data.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** STUB — replaced by the Home/Scan feature. */
@Singleton
class StubCleanupScan @Inject constructor() : CleanupScan {
    override val result: StateFlow<ScanResult?> = MutableStateFlow(null)
    override val progress: StateFlow<ScanProgress?> = MutableStateFlow(null)
    override suspend fun run(): ScanResult = error("stub")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds @Singleton abstract fun bindCleanupScan(impl: StubCleanupScan): CleanupScan
}
