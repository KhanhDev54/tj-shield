package app.pwhs.blockads.data.repository

import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory view of Game Mode that the tunnel engine reads on every DNS
 * query, so lookups never touch disk.
 */
class GameModeState {

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
