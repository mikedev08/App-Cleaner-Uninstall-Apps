package com.jedy.appcleaner.uninstaller.data.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** STUB — replaced by the Billing feature. */
@Singleton
class RevenueCatPremium @Inject constructor() : Premium {
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(false)
}
