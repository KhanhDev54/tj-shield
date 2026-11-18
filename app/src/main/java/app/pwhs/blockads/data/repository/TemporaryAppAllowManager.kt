package app.pwhs.blockads.data.repository

import app.pwhs.blockads.data.dao.CustomDnsRuleDao
import app.pwhs.blockads.data.entities.CustomDnsRule
import app.pwhs.blockads.data.entities.RuleType
import timber.log.Timber

/**
 * Persists and serves time-limited per-app ALLOW rules (Game Mode): e.g. let one game
 * reach its ad SDK domains for two minutes so a rewarded ad can load, then block again.
 */
class TemporaryAppAllowManager(
    private val customDnsRuleDao: CustomDnsRuleDao,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val store: TemporaryAppAllowStore = TemporaryAppAllowStore(nowMs)
) {

    /** Allows [domains] for [packageName] for [durationMs]; returns the expiry time in epoch millis. */
    suspend fun allow(packageName: String, domains: Collection<String>, durationMs: Long): Long {
        require(durationMs > 0) { "durationMs must be positive" }
        val pkg = packageName.trim()
        require(pkg.isNotEmpty()) { "packageName must not be empty" }
        val expiresAt = nowMs() + durationMs
        domains.map { it.trim().trimEnd('.').lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { domain ->
                customDnsRuleDao.deleteTemporaryForApp(pkg, domain)
                customDnsRuleDao.insert(
                    CustomDnsRule(
                        rule = "@@||$domain^",
                        ruleType = RuleType.ALLOW,
                        domain = domain,
                        packageName = pkg,
                        expiresAt = expiresAt
                    )
                )
                store.put(pkg, domain, expiresAt)
            }
        Timber.d("Temporarily allowed ${domains.size} domains for $pkg until $expiresAt")
        return expiresAt
    }

    /** Ends every temporary rule of [packageName] immediately. */
    suspend fun revoke(packageName: String) {
        customDnsRuleDao.deleteAllTemporaryForApp(packageName.trim())
        store.clear(packageName)
    }

    /** Drops expired rows and rebuilds the in-memory view from the rules still active. */
    suspend fun reload(perAppRules: List<CustomDnsRule>) {
        val now = nowMs()
        val removed = customDnsRuleDao.deleteExpired(now)
        if (removed > 0) Timber.d("Removed $removed expired temporary rules")
        store.replaceAll(
            perAppRules
                .filter { it.ruleType == RuleType.ALLOW && (it.expiresAt ?: 0L) > now }
                .map { TemporaryAppAllowStore.Entry(it.packageName, it.domain, it.expiresAt!!) }
        )
    }

    fun isAllowed(packageName: String, domain: String): Boolean = store.isAllowed(packageName, domain)

    fun activeUntil(packageName: String): Long? = store.activeUntil(packageName)
}
