package com.jedy.appcleaner.uninstaller.data.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen). The single `premium` RevenueCat entitlement (PRD §2): weekly subscription
 * with a 3-day trial. Everything outside the Paywall and Settings only ever reads [isPremium].
 */
interface Premium {
    val isPremium: StateFlow<Boolean>
}
