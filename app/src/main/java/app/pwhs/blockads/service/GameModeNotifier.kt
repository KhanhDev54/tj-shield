package app.pwhs.blockads.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.pwhs.blockads.MainActivity
import app.pwhs.blockads.R
import app.pwhs.blockads.data.repository.GameModeState
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Game Mode notification. When a game has an ad blocked it offers
 * "Unblock for 2 minutes"; while unblocked it shows the end time and
 * "Block again".
 */
class GameModeNotifier(
    private val context: Context,
    private val state: GameModeState,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    companion object {
        const val CHANNEL_ID = "game_mode_channel"
        const val UNBLOCK_DURATION_MS = 2 * 60 * 1000L

        /** Do not offer the unblock again for the same game within this time. */
        const val REMIND_INTERVAL_MS = 10 * 60 * 1000L
        private const val NOTIFICATION_ID_BASE = 0x474D_0000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val lastOffered = ConcurrentHashMap<String, Long>()

    /**
     * Called by the tunnel each time a game gets a blocked answer, so it must
     * stay cheap: it posts at most once per [REMIND_INTERVAL_MS] per game.
     */
    fun onAdBlocked(packageName: String) {
        if (packageName.isEmpty() || state.isUnblocked(packageName)) return
        val now = nowMs()
        val last = lastOffered[packageName]
        if (last != null && now - last < REMIND_INTERVAL_MS) return
        if (last == null) {
            if (lastOffered.putIfAbsent(packageName, now) != null) return
        } else if (!lastOffered.replace(packageName, last, now)) {
            return
        }
        showOffer(packageName)
    }

    /** Starts a temporary unblock of [packageName] and shows its end time. */
    fun unblock(packageName: String) {
        val until = state.unblock(packageName, UNBLOCK_DURATION_MS)
        ensureChannel()
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until))
        val notification = baseBuilder(packageName)
            .setContentTitle(context.getString(R.string.game_mode_notification_unblocked_title, appLabel(packageName)))
            .setContentText(context.getString(R.string.game_mode_notification_unblocked_text, time))
            .setOngoing(true)
            .setTimeoutAfter(UNBLOCK_DURATION_MS)
            .addAction(
                0,
                context.getString(R.string.game_mode_action_block_again),
                actionIntent(packageName, GameModeReceiver.ACTION_RELOCK)
            )
            .build()
        runCatching { notificationManager.notify(notificationId(packageName), notification) }
    }

    /** Ends a temporary unblock of [packageName] now and removes the notification. */
    fun relock(packageName: String) {
        state.relock(packageName)
        notificationManager.cancel(notificationId(packageName))
    }

    private fun showOffer(packageName: String) {
        ensureChannel()
        val notification = baseBuilder(packageName)
            .setContentTitle(context.getString(R.string.game_mode_notification_blocked_title, appLabel(packageName)))
            .setContentText(context.getString(R.string.game_mode_notification_blocked_text))
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(R.string.game_mode_action_unblock_2_min),
                actionIntent(packageName, GameModeReceiver.ACTION_UNBLOCK)
            )
            .build()
        runCatching { notificationManager.notify(notificationId(packageName), notification) }
    }

    private fun baseBuilder(packageName: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(packageName))

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun notificationId(packageName: String) = NOTIFICATION_ID_BASE + (packageName.hashCode() and 0xFFFF)

    private fun actionIntent(packageName: String, action: String): PendingIntent {
        val intent = Intent(context, GameModeReceiver::class.java).apply {
            this.action = action
            putExtra(GameModeReceiver.EXTRA_PACKAGE_NAME, packageName)
        }
        return PendingIntent.getBroadcast(
            context, (action + packageName).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(packageName: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, ("game_mode" + packageName).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.game_mode_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.game_mode_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
