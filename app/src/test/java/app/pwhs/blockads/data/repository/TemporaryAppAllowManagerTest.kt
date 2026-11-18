package app.pwhs.blockads.data.repository

import app.pwhs.blockads.data.entities.CustomDnsRule
import app.pwhs.blockads.data.entities.RuleType
import app.pwhs.blockads.ui.FakeCustomDnsRuleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAppAllowManagerTest {

    private var now = 10_000L
    private val dao = FakeCustomDnsRuleDao()
    private val manager = TemporaryAppAllowManager(dao, nowMs = { now })

    @Test
    fun `allow persists expiring ALLOW rules and applies them at once`() = runTest {
        val expiresAt = manager.allow("com.game", listOf("ads.example", "ADS.example", "sdk.example"), 120_000)

        assertEquals(130_000L, expiresAt)
        assertTrue(manager.isAllowed("com.game", "ads.example"))
        val stored = dao.rules.value
        assertEquals(2, stored.size)
        assertTrue(stored.all { it.ruleType == RuleType.ALLOW && it.packageName == "com.game" && it.expiresAt == 130_000L })
    }

    @Test
    fun `allowing again extends instead of duplicating`() = runTest {
        manager.allow("com.game", listOf("ads.example"), 60_000)
        now = 20_000
        manager.allow("com.game", listOf("ads.example"), 60_000)

        assertEquals(1, dao.rules.value.size)
        assertEquals(80_000L, manager.activeUntil("com.game"))
    }

    @Test
    fun `revoke ends temporary rules but keeps permanent ones`() = runTest {
        dao.insert(CustomDnsRule(rule = "@@||keep.example^", ruleType = RuleType.ALLOW, domain = "keep.example", packageName = "com.game"))
        manager.allow("com.game", listOf("ads.example"), 60_000)

        manager.revoke("com.game")

        assertFalse(manager.isAllowed("com.game", "ads.example"))
        assertEquals(listOf("keep.example"), dao.rules.value.map { it.domain })
    }

    @Test
    fun `reload deletes expired rows and restores active ones`() = runTest {
        dao.insert(CustomDnsRule(rule = "@@||old.example^", ruleType = RuleType.ALLOW, domain = "old.example", packageName = "com.game", expiresAt = 5_000))
        dao.insert(CustomDnsRule(rule = "@@||new.example^", ruleType = RuleType.ALLOW, domain = "new.example", packageName = "com.game", expiresAt = 50_000))

        manager.reload(dao.getPerAppRules())

        assertEquals(listOf("new.example"), dao.rules.value.map { it.domain })
        assertTrue(manager.isAllowed("com.game", "new.example"))
        assertFalse(manager.isAllowed("com.game", "old.example"))
    }
}
