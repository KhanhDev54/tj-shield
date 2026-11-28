package app.pwhs.blockads.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.pwhs.blockads.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action
        if (intentAction != Intent.ACTION_BOOT_COMPLETED &&
            intentAction != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intentAction != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                val isLocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        userManager != null && !userManager.isUserUnlocked

                val (autoReconnect, wasEnabled, routingMode) = if (isLocked) {
                    val directPrefs = app.pwhs.blockads.data.datastore.DirectBootPreferences(context)
                    Triple(directPrefs.autoReconnect, directPrefs.wasVpnEnabled, directPrefs.routingMode)
                } else {
                    val prefs = AppPreferences(context)
                    Triple(prefs.autoReconnect.first(), prefs.vpnEnabled.first(), prefs.routingMode.first())
                }

                // Root mode reads filters and settings from credential-protected
                // storage, which is unreadable until the user unlocks. Starting it
                // on locked boot fails, and that failed start used to leave the
                // service marked as starting, so the real start on BOOT_COMPLETED
                // was skipped (#179). Wait for BOOT_COMPLETED instead.
                val deferRootUntilUnlock = isLocked && routingMode == AppPreferences.ROUTING_MODE_ROOT
                if (deferRootUntilUnlock) {
                    Timber.d("Root Proxy waits for the user to unlock before starting")
                }

                if (autoReconnect && wasEnabled && !deferRootUntilUnlock) {
                    val trigger = when (intentAction) {
                        Intent.ACTION_MY_PACKAGE_REPLACED -> "app update"
                        Intent.ACTION_LOCKED_BOOT_COMPLETED -> "locked direct boot"
                        else -> "boot"
                    }
                    if (routingMode == AppPreferences.ROUTING_MODE_ROOT) {
                        Timber.d("Auto-starting Root Proxy mode after $trigger")
                        val serviceIntent = Intent(context, RootProxyService::class.java).apply {
                            action = RootProxyService.ACTION_START
                            putExtra(RootProxyService.EXTRA_STARTED_FROM_BOOT, true)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        Timber.d("Auto-reconnecting VPN after $trigger")
                        val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_START
                            putExtra(AdBlockVpnService.EXTRA_STARTED_FROM_BOOT, true)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting service after boot")
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
