package app.pwhs.blockads.service

import android.os.Looper
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import app.pwhs.blockads.data.datastore.prefs.newPreferencesDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GameModeTileServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameMode: GameModePreferences

    @Before
    fun setUp() {
        gameMode = GameModePreferences(tempFolder.newPreferencesDataStore())
        val prefs: AppPreferences = mockk(relaxed = true) { every { gameMode } returns this@GameModeTileServiceTest.gameMode }
        startKoin { modules(module { single { prefs } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun awaitEnabled(expected: Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (runBlocking { gameMode.isEnabledSnapshot() } == expected) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `tapping the tile toggles game mode`() {
        val service = Robolectric.buildService(GameModeTileService::class.java).create().get()
        assertFalse(runBlocking { gameMode.isEnabledSnapshot() })

        service.onClick()
        awaitEnabled(true)
        assertTrue(runBlocking { gameMode.isEnabledSnapshot() })

        service.onClick()
        awaitEnabled(false)
        assertFalse(runBlocking { gameMode.isEnabledSnapshot() })
    }
}
