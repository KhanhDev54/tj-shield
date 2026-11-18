package app.pwhs.blockads.data.datastore.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Game Mode settings: whether it is on, and which apps count as games.
 *
 * [selectedPackages] is null until the user edits the list. Until then the
 * apps that declare themselves as games are used.
 */
class GameModePreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_GAME_MODE_ENABLED = booleanPreferencesKey("game_mode_enabled")
        val KEY_GAME_MODE_PACKAGES = stringSetPreferencesKey("game_mode_packages")

        fun reset(prefs: MutablePreferences) {
            prefs.remove(KEY_GAME_MODE_ENABLED)
            prefs.remove(KEY_GAME_MODE_PACKAGES)
        }
    }

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_GAME_MODE_ENABLED] ?: false }

    val selectedPackages: Flow<Set<String>?> = dataStore.data.map { it[KEY_GAME_MODE_PACKAGES] }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GAME_MODE_ENABLED] = enabled }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        dataStore.edit { it[KEY_GAME_MODE_PACKAGES] = packages }
    }

    suspend fun isEnabledSnapshot(): Boolean = enabled.first()
}
