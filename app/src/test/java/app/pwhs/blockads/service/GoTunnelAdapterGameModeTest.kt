package app.pwhs.blockads.service

import app.pwhs.blockads.testutil.ShadowGoSeq
import io.mockk.every
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowGoSeq::class], instrumentedPackages = ["go", "tunnel"])
class GoTunnelAdapterGameModeTest {

    private val f = GoTunnelAdapterFixture()

    @After
    fun tearDown() {
        f.scope.cancel()
        unmockkAll()
    }

    @Test
    fun `game checker reports games and offers the unblock for them`() {
        f.gameApps += "com.game"
        f.adapter.start(f.tunFd)

        assertTrue(f.gameAppChecker.captured.isGameApp("com.game"))
        assertFalse(f.gameAppChecker.captured.isGameApp("com.browser"))
        assertEquals(listOf("com.game"), f.adBlockedGames.toList())
    }

    @Test
    fun `an unblocked game skips ad lists but keeps security lists`() {
        every { f.filterRepo.checkAppRule(any(), any()) } returns -1L
        f.unblockedGames += "com.game"
        f.adapter.start(f.tunFd)

        assertEquals(2L, f.appDomainChecker.captured.checkAppRule("ads.example", "com.game"))
        assertEquals(-1L, f.appDomainChecker.captured.checkAppRule("ads.example", "com.other"))
        verify(exactly = 0) { f.filterRepo.checkAppRule("ads.example", "com.game") }
    }

    @Test
    fun `after the unblock ends per-app rules apply again`() {
        every { f.filterRepo.checkAppRule("ads.example", "com.game") } returns 1L
        f.adapter.start(f.tunFd)

        assertEquals(1L, f.appDomainChecker.captured.checkAppRule("ads.example", "com.game"))
    }
}
