package app.pwhs.blockads.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAppAllowStoreTest {

    private var now = 1_000L
    private val store = TemporaryAppAllowStore { now }

    @Test
    fun `allows the domain and its subdomains only for that app`() {
        store.put("com.game", "ads.example", expiresAt = 5_000)

        assertTrue(store.isAllowed("com.game", "ads.example"))
        assertTrue(store.isAllowed("com.game", "cdn.ads.example"))
        assertFalse(store.isAllowed("com.game", "example"))
        assertFalse(store.isAllowed("com.other", "ads.example"))
    }

    @Test
    fun `stops applying once the expiry time is reached`() {
        store.put("com.game", "ads.example", expiresAt = 5_000)

        now = 4_999
        assertTrue(store.isAllowed("com.game", "ads.example"))
        now = 5_000
        assertFalse(store.isAllowed("com.game", "ads.example"))
        assertNull(store.activeUntil("com.game"))
    }

    @Test
    fun `ignores rules that are already expired and normalizes input`() {
        store.put("com.game", "old.example", expiresAt = 500)
        store.put(" com.game ", "Ads.Example.", expiresAt = 5_000)

        assertFalse(store.isAllowed("com.game", "old.example"))
        assertTrue(store.isAllowed("com.game", "ads.example"))
    }

    @Test
    fun `clear and replaceAll reset the app's rules`() {
        store.put("com.game", "ads.example", expiresAt = 5_000)
        store.clear("com.game")
        assertFalse(store.isAllowed("com.game", "ads.example"))

        store.replaceAll(listOf(TemporaryAppAllowStore.Entry("com.game", "b.example", 9_000)))
        assertTrue(store.isAllowed("com.game", "b.example"))
        assertEquals(9_000L, store.activeUntil("com.game"))
    }
}
