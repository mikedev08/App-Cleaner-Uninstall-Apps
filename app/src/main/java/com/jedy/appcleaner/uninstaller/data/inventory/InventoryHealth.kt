package com.jedy.appcleaner.uninstaller.data.inventory

import kotlinx.coroutines.flow.StateFlow

/**
 * Inventory diagnostics, kept off the frozen [AppInventory] contract so other features' fakes
 * don't have to grow a member they never read. Bound to the same singleton as [AppInventory].
 */
interface InventoryHealth {
    /**
     * PRD §6 item 11: the last scan found fewer than five user apps, which on a real phone means
     * `QUERY_ALL_PACKAGES` was stripped. Home shows "Some apps may be hidden" while this is true.
     */
    val isIncomplete: StateFlow<Boolean>
}
