package com.jedy.appcleaner.uninstaller.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.billingStore: DataStore<Preferences> by preferencesDataStore(name = "billing_state")

/** The last entitlement RevenueCat reported, as we persisted it. */
data class CachedEntitlement(
    val active: Boolean = false,
    val expiresAtMillis: Long? = null,
    val willRenew: Boolean = false,
    /** Play subscription id (no base plan), for the `sku` of the manage-subscription link. */
    val productId: String? = null,
) {
    fun isStillActive(nowMillis: Long): Boolean =
        BillingRules.cachedEntitlementActive(active, expiresAtMillis, willRenew, nowMillis)
}

/**
 * Billing's own tiny store, separate from AppPreferences so the entitlement survives exactly as
 * RevenueCat last reported it.
 *
 * Why persist at all when RevenueCat caches CustomerInfo itself: RevenueCat's cache is only
 * reachable asynchronously and only once the SDK is configured. Premium has to be right on the
 * very first frame of a cold start (no flash of blurred rows for a paying user), offline (PRD §6
 * item 17), and inside a reminder worker running in a fresh process.
 */
@Singleton
class EntitlementCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val active = booleanPreferencesKey("entitlement_active")
        val expiresAt = longPreferencesKey("entitlement_expires_at")
        val willRenew = booleanPreferencesKey("entitlement_will_renew")
        val productId = stringPreferencesKey("entitlement_product_id")
    }

    suspend fun read(): CachedEntitlement {
        // A corrupt or unreadable file means "no cached premium", never a crash on launch.
        val prefs = try {
            context.billingStore.data.first()
        } catch (_: IOException) {
            return CachedEntitlement()
        }
        return CachedEntitlement(
            active = prefs[Keys.active] ?: false,
            expiresAtMillis = prefs[Keys.expiresAt],
            willRenew = prefs[Keys.willRenew] ?: false,
            productId = prefs[Keys.productId],
        )
    }

    suspend fun write(entitlement: CachedEntitlement) {
        try {
            context.billingStore.edit { prefs ->
                prefs[Keys.active] = entitlement.active
                prefs[Keys.willRenew] = entitlement.willRenew
                entitlement.expiresAtMillis?.let { prefs[Keys.expiresAt] = it } ?: prefs.remove(Keys.expiresAt)
                entitlement.productId?.let { prefs[Keys.productId] = it } ?: prefs.remove(Keys.productId)
            }
        } catch (_: IOException) {
            // Next CustomerInfo update retries; the in-memory state is already correct.
        }
    }
}
