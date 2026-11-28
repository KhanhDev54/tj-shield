package app.pwhs.blockads.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Emits whenever an app is installed, updated or removed, while collected.
 * Package broadcasts come from the system, so a receiver registered at
 * runtime as not exported still gets them.
 */
fun packageChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    ContextCompat.registerReceiver(
        context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
    )
    awaitClose { runCatching { context.applicationContext.unregisterReceiver(receiver) } }
}.conflate()
