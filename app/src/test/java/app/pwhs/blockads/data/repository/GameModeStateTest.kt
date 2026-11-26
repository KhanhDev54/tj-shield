package app.pwhs.blockads.data.repository

import android.content.pm.ApplicationInfo
import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import app.pwhs.blockads.data.datastore.prefs.newPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameModeStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `nothing is a game while game mode is off`() {
        val state = GameModeState()
        state.update(enabled = false, gamePackages = setOf("com.game"))
        assertFalse(state.isGameApp("com.game"))
    }

    @Test
    fun `only chosen packages are games when game mode is on`() {
        val state = GameModeState()
        state.update(enabled = true, gamePackages = setOf("com.game"))
        assertTrue(state.isGameApp("com.game"))
        assertFalse(state.isGameApp("com.browser"))
        assertFalse(state.isGameApp(""))
    }

    @Test
    fun `unblock lasts for the given time and relock ends it`() {
        var now = 1_000L
        val state = GameModeState(nowMs = { now })

        assertEquals(121_000L, state.unblock("com.game", 120_000L))
        assertTrue(state.isUnblocked("com.game"))
        assertFalse(state.isUnblocked("com.other"))

        now = 120_999L
        assertTrue(state.isUnblocked("com.game"))
        now = 121_000L
        assertFalse(state.isUnblocked("com.game"))
        assertEquals(null, state.unblockedUntil("com.game"))

        state.unblock("com.game", 120_000L)
        state.relock("com.game")
        assertFalse(state.isUnblocked("com.game"))
    }

    @Test
    fun `bind uses detected games until the user picks a list`() = runBlocking {
        val prefs = GameModePreferences(tempFolder.newPreferencesDataStore())
        val detector = GameAppDetector {
            listOf(ApplicationInfo().apply {
                packageName = "com.detected.game"
                category = ApplicationInfo.CATEGORY_GAME
            })
        }
        val state = GameModeState()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            state.bind(scope, prefs, detector)

            prefs.setEnabled(true)
            awaitUntil { state.isGameApp("com.detected.game") }

            prefs.setSelectedPackages(setOf("com.picked.game"))
            awaitUntil { state.isGameApp("com.picked.game") }
            assertFalse(state.isGameApp("com.detected.game"))

            prefs.setEnabled(false)
            awaitUntil { !state.enabled }
            assertEquals(setOf("com.picked.game"), state.gamePackages)
            assertFalse(state.isGameApp("com.picked.game"))
        } finally {
            scope.cancel()
        }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }
}
