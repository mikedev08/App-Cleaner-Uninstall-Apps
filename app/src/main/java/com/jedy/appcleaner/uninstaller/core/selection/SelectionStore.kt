package com.jedy.appcleaner.uninstaller.core.selection

import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one global selection (PRD §6 item 21): a set of package names shared by every tab, search
 * and sort. Switching views never clears it; only an explicit clear or a started batch does.
 *
 * Also carries a one-shot Home tab request, used by the reminder notification deep link.
 */
@Singleton
class SelectionStore @Inject constructor() {
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _requestedTab = MutableStateFlow<HomeTab?>(null)
    val requestedTab: StateFlow<HomeTab?> = _requestedTab.asStateFlow()

    fun toggle(packageName: String) = _selected.update { if (packageName in it) it - packageName else it + packageName }
    fun select(packages: Collection<String>) = _selected.update { it + packages }
    fun deselect(packages: Collection<String>) = _selected.update { it - packages.toSet() }
    fun retainOnly(installed: Set<String>) = _selected.update { it intersect installed }
    fun clear() { _selected.value = emptySet() }

    fun requestTab(tab: HomeTab) { _requestedTab.value = tab }
    fun consumeTabRequest() { _requestedTab.value = null }
}
