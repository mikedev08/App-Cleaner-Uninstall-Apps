package com.jedy.appcleaner.uninstaller.data.inventory

import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Process-start hook for the inventory (PRD Feature 1). Creating the inventory loads the Room
 * snapshot; this then registers the live package receiver and kicks the background rescan that
 * diffs PackageManager against it. Returns immediately — the scan runs on the application scope.
 */
class InventoryStartup @Inject constructor(
    private val inventory: PackageManagerAppInventory,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AppStartup {
    override fun start() {
        inventory.registerPackageReceiver()
        scope.launch { inventory.refresh() }
    }
}
