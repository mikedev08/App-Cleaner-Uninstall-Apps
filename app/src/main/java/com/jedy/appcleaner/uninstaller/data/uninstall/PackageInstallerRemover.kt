package com.jedy.appcleaner.uninstaller.data.uninstall

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PackageRemover] over `PackageInstaller.uninstall()` (PRD Feature 2).
 *
 * The status comes back through a PendingIntent broadcast to a receiver registered on the
 * application context, not in the manifest. The only thing a manifest receiver would add is
 * delivery after process death, and the engine does not need it: an item still IN_PROGRESS on
 * restart is resolved by checking whether the package is gone (see [Preflight.REMOVED_BY_US]),
 * which is the same verification a delivered STATUS_SUCCESS would get.
 *
 * The broadcast Intent names our package, so it is explicit, and the PendingIntent is
 * FLAG_MUTABLE: the installer must fill EXTRA_STATUS / EXTRA_INTENT into it, and on API 31+ an
 * immutable one arrives empty. Android 14 only allows a mutable PendingIntent for an explicit
 * Intent, which `setPackage` satisfies.
 */
@Singleton
class PackageInstallerRemover @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PackageRemover {

    private val _events = MutableSharedFlow<RemovalEvent>(extraBufferCapacity = 64)
    override val events: Flow<RemovalEvent> = _events.asSharedFlow()

    private val registered = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
            if (itemId < 0) return
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                _events.tryEmit(
                    if (confirm != null) RemovalEvent.NeedsConfirmation(itemId, launcherFor(confirm))
                    else RemovalEvent.Finished(itemId, PackageInstaller.STATUS_FAILURE)
                )
            } else {
                _events.tryEmit(RemovalEvent.Finished(itemId, status))
            }
        }
    }

    private fun launcherFor(confirm: Intent) = ConfirmationLauncher { activityContext ->
        try {
            activityContext.startActivity(confirm)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Confirmation dialog unavailable", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Confirmation dialog refused", e)
            false
        }
    }

    private fun ensureRegistered() {
        if (registered.compareAndSet(false, true)) {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun requestUninstall(itemId: Long, packageName: String) {
        ensureRegistered()
        val intent = Intent(ACTION_STATUS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_ITEM_ID, itemId)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            context, itemId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        try {
            context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
        } catch (e: RuntimeException) {
            // IllegalArgumentException / SecurityException: report it as a plain failure so the
            // queue moves on instead of waiting for a broadcast that will never come.
            Log.w(TAG, "uninstall() rejected", e)
            _events.tryEmit(RemovalEvent.Finished(itemId, PackageInstaller.STATUS_FAILURE))
        }
    }

    override fun isDeviceAdmin(packageName: String): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins.orEmpty().any { it.packageName == packageName }
    }

    private companion object {
        const val TAG = "UninstallEngine"
        const val ACTION_STATUS = "com.jedy.appcleaner.uninstaller.action.UNINSTALL_STATUS"
        const val EXTRA_ITEM_ID = "com.jedy.appcleaner.uninstaller.extra.ITEM_ID"
    }
}
