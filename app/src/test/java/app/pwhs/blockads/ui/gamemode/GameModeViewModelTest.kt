package app.pwhs.blockads.ui.gamemode

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import app.pwhs.blockads.data.datastore.prefs.newPreferencesDataStore
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GameModeViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: GameModePreferences

    private val apps = listOf(
        GameAppCandidate("com.racing", "Racing", isDetectedGame = true),
        GameAppCandidate("com.puzzle", "Puzzle", isDetectedGame = true),
        GameAppCandidate("com.browser", "Browser", isDetectedGame = false),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = GameModePreferences(tempFolder.newPreferencesDataStore())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        GameModeViewModel(prefs, mockk<Application>(relaxed = true), loadCandidates = { apps })

    // Joins only jobs launched by [action]; stateIn's sharing coroutines never complete.
    private fun GameModeViewModel.runAndSettle(action: GameModeViewModel.() -> Unit) {
        val job = viewModelScope.coroutineContext[Job]!!
        val before = job.children.toSet()
        action()
        runBlocking { (job.children.toSet() - before).joinAll() }
    }

    @Test
    fun `detected games are selected until the user picks a list`() {
        val items = GameModeViewModel.buildGameAppItems(apps, selected = null, showAll = false)
        assertEquals(listOf("com.puzzle", "com.racing"), items.map { it.packageName })
        assertTrue(items.all { it.isSelected })
    }

    @Test
    fun `show all lists every app with chosen ones first`() {
        val items = GameModeViewModel.buildGameAppItems(apps, selected = setOf("com.browser"), showAll = true)
        assertEquals(listOf("com.browser", "com.puzzle", "com.racing"), items.map { it.packageName })
        assertEquals(listOf(true, false, false), items.map { it.isSelected })
    }

    @Test
    fun `a chosen app that is not a detected game stays listed`() {
        val items = GameModeViewModel.buildGameAppItems(apps, selected = setOf("com.browser"), showAll = false)
        assertEquals(listOf("com.browser", "com.puzzle", "com.racing"), items.map { it.packageName })
    }

    @Test
    fun `unchecking a detected game saves the rest of the detected games`() {
        assertNull(runBlocking { prefs.selectedPackages.first() })
        val vm = newViewModel()

        vm.runAndSettle { setGameSelected("com.racing", false) }

        assertEquals(setOf("com.puzzle"), runBlocking { prefs.selectedPackages.first() })
    }

    @Test
    fun `checking another app adds it to the chosen games`() {
        runBlocking { prefs.setSelectedPackages(setOf("com.racing")) }
        val vm = newViewModel()

        vm.runAndSettle { setGameSelected("com.browser", true) }

        assertEquals(setOf("com.racing", "com.browser"), runBlocking { prefs.selectedPackages.first() })
    }

    @Test
    fun `the switch turns game mode on`() {
        val vm = newViewModel()

        vm.runAndSettle { setEnabled(true) }

        assertTrue(runBlocking { prefs.isEnabledSnapshot() })
    }
}
