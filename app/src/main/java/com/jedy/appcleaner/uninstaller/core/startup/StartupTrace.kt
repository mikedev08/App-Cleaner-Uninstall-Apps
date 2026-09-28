package com.jedy.appcleaner.uninstaller.core.startup

import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.jedy.appcleaner.uninstaller.BuildConfig

/**
 * Debug-only cold-start milestones, logged as "+<ms since process start> <label> [thread]" under
 * [TAG] (`adb logcat -s AppCleanerStartup`). Release builds compile the calls down to a no-op.
 */
object StartupTrace {
    const val TAG = "AppCleanerStartup"

    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        val elapsed = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        val thread = if (Looper.myLooper() == Looper.getMainLooper()) "main" else Thread.currentThread().name
        Log.d(TAG, "+${elapsed}ms $label [$thread]")
    }
}
