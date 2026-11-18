package app.pwhs.blockads.data.repository

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameAppDetectorTest {

    private fun app(pkg: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED, flags: Int = 0) =
        ApplicationInfo().apply {
            packageName = pkg
            this.category = category
            this.flags = flags
        }

    @Test
    fun `play store game category counts as a game`() {
        assertTrue(GameAppDetector.isGame(app("com.game", category = ApplicationInfo.CATEGORY_GAME)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy isGame flag counts as a game`() {
        assertTrue(GameAppDetector.isGame(app("com.old.game", flags = ApplicationInfo.FLAG_IS_GAME)))
    }

    @Test
    fun `other categories are not games`() {
        assertFalse(GameAppDetector.isGame(app("com.social", category = ApplicationInfo.CATEGORY_SOCIAL)))
        assertFalse(GameAppDetector.isGame(app("com.plain")))
    }

    @Test
    fun `installed games are listed sorted and without duplicates`() {
        val detector = GameAppDetector {
            listOf(
                app("com.z.game", category = ApplicationInfo.CATEGORY_GAME),
                app("com.mail"),
                app("com.a.game", category = ApplicationInfo.CATEGORY_GAME),
                app("com.a.game", category = ApplicationInfo.CATEGORY_GAME)
            )
        }

        assertEquals(listOf("com.a.game", "com.z.game"), detector.installedGamePackages())
    }
}
