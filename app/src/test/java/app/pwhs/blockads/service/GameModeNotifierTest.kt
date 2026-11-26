package app.pwhs.blockads.service

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.pwhs.blockads.R
import app.pwhs.blockads.data.repository.GameModeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GameModeNotifierTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var now = 1_000_000L
    private val state = GameModeState(nowMs = { now })
    private val notifier = GameModeNotifier(app, state, nowMs = { now })

    private fun posted() = shadowOf(manager).allNotifications

    @Test
    fun `a blocked ad offers the unblock once per interval`() {
        notifier.onAdBlocked("com.game")
        assertEquals(1, posted().size)
        val action = posted().single().actions.single()
        assertEquals(app.getString(R.string.game_mode_action_unblock_2_min), action.title.toString())

        manager.cancelAll()
        now += GameModeNotifier.REMIND_INTERVAL_MS - 1
        notifier.onAdBlocked("com.game")
        assertTrue(posted().isEmpty())

        now += 1
        notifier.onAdBlocked("com.game")
        assertEquals(1, posted().size)
    }

    @Test
    fun `unblock lets the game through for two minutes and offers block again`() {
        notifier.unblock("com.game")

        assertTrue(state.isUnblocked("com.game"))
        assertEquals(now + GameModeNotifier.UNBLOCK_DURATION_MS, state.unblockedUntil("com.game"))
        val action = posted().single().actions.single()
        assertEquals(app.getString(R.string.game_mode_action_block_again), action.title.toString())

        // No new offer while the game is unblocked.
        manager.cancelAll()
        notifier.onAdBlocked("com.game")
        assertTrue(posted().isEmpty())
    }

    @Test
    fun `block again ends the unblock and removes the notification`() {
        notifier.unblock("com.game")
        notifier.relock("com.game")

        assertFalse(state.isUnblocked("com.game"))
        assertTrue(posted().isEmpty())
    }

    @Test
    fun `an empty package name is ignored`() {
        notifier.onAdBlocked("")
        assertTrue(posted().isEmpty())
    }
}
