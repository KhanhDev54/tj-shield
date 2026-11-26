package app.pwhs.blockads.data.repository

import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory view of Game Mode that the tunnel engine reads on every DNS
 * query, so lookups never touch disk.
 */
class GameModeState(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    // packageName -> epoch millis until which ads are let through for that game
    private val unblockEnds = ConcurrentHashMap<String, Long>()

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var gamePackages: Set<String> = emptySet()
        private set

    fun update(enabled: Boolean, gamePackages: Set<String>) {
        this.gamePackages = gamePackages
        this.enabled = enabled
    }

    /** True when Game Mode is on and [packageName] is one of the chosen games. */
    fun isGameApp(packageName: String): Boolean =
        enabled && packageName.isNotEmpty() && packageName in gamePackages

    /**
     * Lets every domain through for [packageName] for [durationMs], so a
     * rewarded ad can load. Returns the end time in epoch millis.
     */
    fun unblock(packageName: String, durationMs: Long): Long {
        require(durationMs > 0) { "durationMs must be positive" }
        val until = nowMs() + durationMs
        if (packageName.isNotEmpty()) unblockEnds[packageName] = until
        return until
    }

    /** Ends a temporary unblock of [packageName] now. */
    fun relock(packageName: String) {
        unblockEnds.remove(packageName)
    }

    /** True while a temporary unblock of [packageName] is running. */
    fun isUnblocked(packageName: String): Boolean {
        val until = unblockEnds[packageName] ?: return false
        if (until > nowMs()) return true
        unblockEnds.remove(packageName, until)
        return false
    }

    /** End of the running unblock of [packageName], or null when none runs. */
    fun unblockedUntil(packageName: String): Long? =
        unblockEnds[packageName]?.takeIf { it > nowMs() }

    /**
     * Keeps this state in step with [prefs] while [scope] lives. When the user
     * has not chosen games yet, the apps found by [detector] are used.
     */
    fun bind(scope: CoroutineScope, prefs: GameModePreferences, detector: GameAppDetector) {
        scope.launch {
            combine(prefs.enabled, prefs.selectedPackages) { on, selected -> on to selected }
                .collect { (on, selected) ->
                    val packages = selected ?: if (on) {
                        withContext(Dispatchers.IO) { detector.installedGamePackages().toSet() }
                    } else {
                        emptySet()
                    }
                    update(on, packages)
                }
        }
    }
}
