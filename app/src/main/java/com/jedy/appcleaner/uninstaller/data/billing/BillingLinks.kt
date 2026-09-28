package com.jedy.appcleaner.uninstaller.data.billing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Every outbound link the paywall and Settings use, in one place so legal can change a URL
 * without hunting through screens.
 */
object BillingLinks {
    // Placeholder hosting until the legal pages are final; the paywall footer and Settings both read these.
    const val TERMS_URL = "https://jedyapps.com/appcleaner/terms"
    const val PRIVACY_URL = "https://jedyapps.com/appcleaner/privacy"

    /**
     * Play's subscription centre (PRD §4 Screen 13). With `sku` Play opens this exact
     * subscription; without it, the list of the account's subscriptions for this app.
     */
    fun manageSubscription(packageName: String, sku: String?): String = buildString {
        append("https://play.google.com/store/account/subscriptions?package=").append(packageName)
        if (!sku.isNullOrBlank()) append("&sku=").append(sku)
    }

    fun playListingMarket(packageName: String) = "market://details?id=$packageName"
    fun playListingWeb(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
}

/**
 * Opens [url] in whatever handles it, trying [fallbackUrl] when nothing does (e.g. `market://`
 * on a phone without the Play Store). Returns false when neither could be opened so the caller
 * can say so instead of silently doing nothing.
 */
fun Context.openExternalUrl(url: String, fallbackUrl: String? = null): Boolean {
    fun tryOpen(target: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, target.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
    return tryOpen(url) || (fallbackUrl != null && tryOpen(fallbackUrl))
}

/** Same as [openExternalUrl] for a ready-made intent (system settings pages). */
fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
