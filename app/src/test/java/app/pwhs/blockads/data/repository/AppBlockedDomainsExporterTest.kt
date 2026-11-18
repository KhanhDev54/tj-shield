package app.pwhs.blockads.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pwhs.blockads.data.AppDatabase
import app.pwhs.blockads.data.entities.DnsLogEntry
import app.pwhs.blockads.data.entities.TopBlockedDomain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppBlockedDomainsExporterTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun log(domain: String, pkg: String = "com.game", at: Long = 9_000, blocked: Boolean = true) =
        db.dnsLogDao().insert(DnsLogEntry(domain = domain, timestamp = at, isBlocked = blocked, packageName = pkg))

    @Test
    fun `query keeps only blocked domains of the app inside the window, most frequent first`() = runTest {
        log("ads.example")
        log("ads.example")
        log("b-sdk.example")
        log("a-sdk.example")
        log("allowed.example", blocked = false)
        log("other.example", pkg = "com.other")
        log("old.example", at = 1_000)

        val domains = db.dnsLogDao().getBlockedDomainsForApp("com.game", since = 5_000)

        assertEquals(
            listOf(
                TopBlockedDomain("ads.example", 2),
                TopBlockedDomain("a-sdk.example", 1),
                TopBlockedDomain("b-sdk.example", 1)
            ),
            domains
        )
    }

    @Test
    fun `export lists domains under a comment header`() = runTest {
        log("ads.example", at = 50_000)
        val exporter = AppBlockedDomainsExporter(db.dnsLogDao()) { 60_000 }

        val text = exporter.export(" com.game ", windowMs = 30_000)

        assertEquals(
            "# TJ Shield: domains blocked for com.game\n" +
                "# From 1970-01-01T00:00:30Z to 1970-01-01T00:01:00Z, 1 domains\n" +
                "ads.example\n",
            text
        )
    }
}
