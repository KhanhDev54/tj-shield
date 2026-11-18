package app.pwhs.blockads.data.repository

import app.pwhs.blockads.data.dao.DnsLogDao
import app.pwhs.blockads.data.entities.TopBlockedDomain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lists the domains blocked for one app in a recent time window, as plain text
 * (one domain per line, '#' comments on top). Used to collect ad SDK domains
 * while a rewarded ad is shown, so they can become a Game Mode preset.
 */
class AppBlockedDomainsExporter(
    private val dnsLogDao: DnsLogDao,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    suspend fun export(packageName: String, windowMs: Long): String {
        val to = nowMs()
        val from = to - windowMs
        val domains = dnsLogDao.getBlockedDomainsForApp(packageName.trim(), from)
        return format(packageName.trim(), domains, from, to)
    }

    companion object {
        fun format(packageName: String, domains: List<TopBlockedDomain>, fromMs: Long, toMs: Long): String {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return buildString {
                append("# TJ Shield: domains blocked for ").append(packageName).append('\n')
                append("# From ").append(iso.format(Date(fromMs)))
                    .append(" to ").append(iso.format(Date(toMs)))
                    .append(", ").append(domains.size).append(" domains\n")
                domains.forEach { append(it.domain).append('\n') }
            }
        }
    }
}
