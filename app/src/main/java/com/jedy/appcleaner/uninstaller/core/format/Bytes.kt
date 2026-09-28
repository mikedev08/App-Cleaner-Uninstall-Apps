package com.jedy.appcleaner.uninstaller.core.format

import android.content.Context
import android.text.format.Formatter

/**
 * Same SI units as system Settings, so our numbers match what the user sees there (PRD Feature 1).
 * Pass the *localized* context (LocalContext.current) so digits follow the chosen language.
 */
fun formatBytes(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

/** PRD §9: byte values in analytics are bucketed, never exact. */
fun bytesBucket(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return when {
        mb < 100 -> "<100MB"
        mb < 500 -> "100MB-500MB"
        mb < 1_000 -> "500MB-1GB"
        mb < 5_000 -> "1-5GB"
        else -> ">5GB"
    }
}

const val DAY_MILLIS = 24L * 60 * 60 * 1000
