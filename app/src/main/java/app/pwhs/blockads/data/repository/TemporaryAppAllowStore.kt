package app.pwhs.blockads.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory view of per-app ALLOW rules that expire, read on the DNS hot path.
 * Expiry is checked at lookup time so a rule stops applying exactly when it ends,
 * without waiting for the database to be reloaded.
 */
class TemporaryAppAllowStore(
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    // packageName -> (domain -> expiresAt epoch millis)
    private val rules = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    fun put(packageName: String, domain: String, expiresAt: Long) {
        val pkg = packageName.trim()
        val host = normalize(domain)
        if (pkg.isEmpty() || host.isEmpty() || expiresAt <= nowMs()) return
        rules.getOrPut(pkg) { ConcurrentHashMap() }[host] = expiresAt
    }

    fun replaceAll(entries: List<Entry>) {
        rules.clear()
        entries.forEach { put(it.packageName, it.domain, it.expiresAt) }
    }

    fun clear(packageName: String) {
        rules.remove(packageName.trim())
    }

    fun isAllowed(packageName: String, domain: String): Boolean {
        val domains = rules[packageName] ?: return false
        val now = nowMs()
        var host = normalize(domain)
        while (host.isNotEmpty()) {
            val expiresAt = domains[host]
            if (expiresAt != null) {
                if (expiresAt > now) return true
                domains.remove(host, expiresAt)
            }
            val dot = host.indexOf('.')
            if (dot < 0) break
            host = host.substring(dot + 1)
        }
        if (domains.isEmpty()) rules.remove(packageName, domains)
        return false
    }

    /** Latest expiry among the app's still-active rules, or null when none are active. */
    fun activeUntil(packageName: String): Long? {
        val now = nowMs()
        return rules[packageName]?.values?.filter { it > now }?.maxOrNull()
    }

    private fun normalize(domain: String) = domain.trim().trimEnd('.').lowercase()

    data class Entry(val packageName: String, val domain: String, val expiresAt: Long)
}
