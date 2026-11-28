package app.pwhs.blockads.ui.browser.rules

import app.pwhs.blockads.ui.browser.interceptor.BrowserAdBlocker
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Repository interface for managing browser ad blocking rules dynamically.
 */
interface BrowserRuleRepository {
    val currentRules: StateFlow<BrowserRulePackage>
    val updateStatus: StateFlow<BrowserRuleUpdateStatus>

    suspend fun checkAndUpdate(customUrl: String? = null): Result<Boolean>
    suspend fun checkAndUpdateModules(customUrl: String? = null): Result<Boolean>
    suspend fun resetToDefaults()
}

/**
 * Implementation of [BrowserRuleRepository].
 */
class BrowserRuleRepositoryImpl(
    private val storage: BrowserRuleStorage,
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BrowserRuleRepository {

    companion object {
        const val DEFAULT_RULES_URL =
            "https://filter.pwhs.app/rules/browser_rules.json"
        const val DEFAULT_MODULES_URL =
            "https://filter.pwhs.app/rules/modules.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _currentRules = MutableStateFlow(storage.getActivePackage())
    override val currentRules: StateFlow<BrowserRulePackage> = _currentRules.asStateFlow()

    private val _updateStatus = MutableStateFlow<BrowserRuleUpdateStatus>(BrowserRuleUpdateStatus.Idle)
    override val updateStatus: StateFlow<BrowserRuleUpdateStatus> = _updateStatus.asStateFlow()

    init {
        // Initialize BrowserAdBlocker with the active rule set immediately
        BrowserAdBlocker.applyRulePackage(_currentRules.value)
    }

    override suspend fun checkAndUpdate(customUrl: String?): Result<Boolean> = withContext(ioDispatcher) {
        val targetUrl = customUrl ?: DEFAULT_RULES_URL
        _updateStatus.value = BrowserRuleUpdateStatus.Checking

        try {
            Timber.d("Checking for browser rule updates from: %s", targetUrl)
            val response = client.get(targetUrl)
            if (response.status.value !in 200..299) {
                val err = "Server returned HTTP ${response.status.value}"
                Timber.w("Failed to fetch browser rules: %s", err)
                _updateStatus.value = BrowserRuleUpdateStatus.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val responseText = response.bodyAsText()
            if (responseText.isBlank()) {
                val err = "Empty response received from rule server"
                _updateStatus.value = BrowserRuleUpdateStatus.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val remotePackage = json.decodeFromString<BrowserRulePackage>(responseText)
            val active = _currentRules.value

            if (remotePackage.version <= active.version && remotePackage.adDomains.size <= active.adDomains.size) {
                Timber.i("Browser rules are already up to date (v%d)", active.version)
                _updateStatus.value = BrowserRuleUpdateStatus.UpToDate(active.version)
                return@withContext Result.success(false)
            }

            // cosmeticCss and scriptletsJs may be given as URLs. Only HTTPS URLs are
            // fetched: the result is injected into every page, so it must not come
            // over plain HTTP. A URL that is refused or cannot be fetched keeps the
            // text that is active now instead of being stored as CSS or JS itself.
            val finalPackage = remotePackage.copy(
                cosmeticCss = resolveRemoteText(remotePackage.cosmeticCss, active.cosmeticCss, "cosmeticCss"),
                scriptletsJs = resolveRemoteText(remotePackage.scriptletsJs, active.scriptletsJs, "scriptletsJs"),
            )

            // Save and hot-reload in-memory rules
            val saved = storage.savePackage(finalPackage)
            if (saved) {
                _currentRules.value = finalPackage
                BrowserAdBlocker.applyRulePackage(finalPackage)
                _updateStatus.value = BrowserRuleUpdateStatus.Updated(
                    version = finalPackage.version,
                    domainsCount = finalPackage.adDomains.size
                )
                Timber.i("Updated browser rules to v%d with %d domains", finalPackage.version, finalPackage.adDomains.size)
                Result.success(true)
            } else {
                val err = "Failed to save updated rules to local storage"
                _updateStatus.value = BrowserRuleUpdateStatus.Error(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating browser rules")
            _updateStatus.value = BrowserRuleUpdateStatus.Error(e.localizedMessage ?: "Network error")
            Result.failure(e)
        }
    }

    override suspend fun checkAndUpdateModules(customUrl: String?): Result<Boolean> = withContext(ioDispatcher) {
        val targetUrl = customUrl ?: DEFAULT_MODULES_URL
        try {
            Timber.d("Checking for modular rule updates from: %s", targetUrl)
            val response = client.get(targetUrl)
            if (response.status.value !in 200..299) {
                val err = "Server returned HTTP ${response.status.value}"
                Timber.w("Failed to fetch modules.json: %s", err)
                return@withContext Result.failure(IllegalStateException(err))
            }
            val text = response.bodyAsText()
            if (text.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty modules response"))
            }
            json.decodeFromString<app.pwhs.blockads.data.models.ModulesPackage>(text)
            val saved = storage.saveModulesJson(text)
            Timber.i("Successfully updated modules.json from remote")
            Result.success(saved)
        } catch (e: Exception) {
            Timber.e(e, "Error updating modules.json")
            Result.failure(e)
        }
    }

    override suspend fun resetToDefaults(): Unit = withContext(ioDispatcher) {
        val defaults = storage.resetToDefaults()
        _currentRules.value = defaults
        BrowserAdBlocker.applyRulePackage(defaults)
        _updateStatus.value = BrowserRuleUpdateStatus.Idle
        Timber.i("Reset browser rules to baseline defaults (v%d)", defaults.version)
    }

    private suspend fun resolveRemoteText(value: String?, current: String?, field: String): String? {
        val trimmed = value?.trim() ?: return value
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            Timber.w("Refusing plain HTTP %s URL: %s", field, trimmed)
            return current
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) return value
        return runCatching {
            val res = client.get(trimmed)
            if (res.status.value in 200..299) res.bodyAsText() else current
        }.onFailure { Timber.w(it, "Failed to resolve remote %s URL: %s", field, trimmed) }
            .getOrDefault(current)
    }
}
