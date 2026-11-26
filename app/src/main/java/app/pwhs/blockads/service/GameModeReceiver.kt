package app.pwhs.blockads.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Handles the "Unblock for 2 minutes" and "Block again" notification buttons. */
class GameModeReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val ACTION_UNBLOCK = "app.pwhs.blockads.GAME_MODE_UNBLOCK"
        const val ACTION_RELOCK = "app.pwhs.blockads.GAME_MODE_RELOCK"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private val notifier: GameModeNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotEmpty() } ?: return
        when (intent.action) {
            ACTION_UNBLOCK -> notifier.unblock(packageName)
            ACTION_RELOCK -> notifier.relock(packageName)
        }
    }
}
