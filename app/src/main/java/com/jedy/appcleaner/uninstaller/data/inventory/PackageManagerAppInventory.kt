package com.jedy.appcleaner.uninstaller.data.inventory

import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** STUB — replaced by the Inventory feature. */
@Singleton
class PackageManagerAppInventory @Inject constructor() : AppInventory {
    override val apps: StateFlow<List<InstalledApp>> = MutableStateFlow(emptyList())
    override val isInitialLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun refresh() = Unit
    override suspend fun find(packageName: String): InstalledApp? = null
    override fun isInstalled(packageName: String): Boolean = false
}
