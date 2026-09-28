package com.jedy.appcleaner.uninstaller.data.usage

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usage Access through AppOpsManager (PRD Feature 3, "Access check").
 *
 * The grant lives in Settings, outside the app, so it can flip at any time without us being told.
 * That is why [recheck] exists and is called from every resume (PRD §6 item 12): a revoked grant
 * must drop the Unused and Large tabs back to "Allow access" on the very next screen the user sees.
 */
@Singleton
class AppOpsUsageAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UsageAccess {

    private val appOps = context.getSystemService(AppOpsManager::class.java)

    private val _isGranted = MutableStateFlow(check())
    override val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    override fun recheck() {
        _isGranted.value = check()
    }

    /**
     * PRD §6 item 13: land on our own toggle where the OEM supports the `package:` page, otherwise
     * on the plain list with [UsageAccessIntent.usesFallbackPage] set so the UI can say where to look.
     */
    override fun settingsIntent(): UsageAccessIntent {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri())
        val resolves = runCatching { direct.resolveActivity(context.packageManager) != null }.getOrDefault(false)
        return if (resolves) {
            UsageAccessIntent(direct, usesFallbackPage = false)
        } else {
            UsageAccessIntent(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), usesFallbackPage = true)
        }
    }

    private fun check(): Boolean {
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // MODE_DEFAULT defers to the permission itself (e.g. granted with `pm grant` on some builds).
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }
}
