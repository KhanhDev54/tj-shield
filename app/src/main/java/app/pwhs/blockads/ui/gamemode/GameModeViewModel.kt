package app.pwhs.blockads.ui.gamemode

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.data.datastore.prefs.GameModePreferences
import app.pwhs.blockads.data.repository.GameAppDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed app that can be picked as a game. */
data class GameAppCandidate(
    val packageName: String,
    val label: String,
    val isDetectedGame: Boolean,
)

/** One row of the Game Mode list. */
data class GameAppItem(
    val packageName: String,
    val label: String,
    val isDetectedGame: Boolean,
    val isSelected: Boolean,
)

data class GameModeUiState(
    val enabled: Boolean = false,
    val isLoading: Boolean = true,
    val showAllApps: Boolean = false,
    val items: List<GameAppItem> = emptyList(),
)

class GameModeViewModel(
    private val prefs: GameModePreferences,
    application: Application,
    private val loadCandidates: suspend () -> List<GameAppCandidate> = { loadInstalledCandidates(application) },
) : AndroidViewModel(application) {

    private val candidates = MutableStateFlow<List<GameAppCandidate>?>(null)
    private val showAllApps = MutableStateFlow(false)

    val uiState: StateFlow<GameModeUiState> = combine(
        prefs.enabled,
        prefs.selectedPackages,
        candidates,
        showAllApps,
    ) { enabled, selected, apps, showAll ->
        GameModeUiState(
            enabled = enabled,
            isLoading = apps == null,
            showAllApps = showAll,
            items = buildGameAppItems(apps.orEmpty(), selected, showAll),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameModeUiState())

    init {
        viewModelScope.launch { candidates.value = loadCandidates() }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setEnabled(enabled) }
    }

    fun setShowAllApps(show: Boolean) {
        showAllApps.value = show
    }

    fun setGameSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            val current = effectiveSelection(
                prefs.selectedPackages.first(),
                candidates.value.orEmpty(),
            )
            prefs.setSelectedPackages(if (selected) current + packageName else current - packageName)
        }
    }

    companion object {
        /** The chosen games, or the detected ones while the user has not chosen yet. */
        fun effectiveSelection(selected: Set<String>?, apps: List<GameAppCandidate>): Set<String> =
            selected ?: apps.filter { it.isDetectedGame }.map { it.packageName }.toSet()

        /**
         * Rows to show: detected games and chosen apps, or every app when
         * [showAll] is on. Chosen apps come first, then by name.
         */
        fun buildGameAppItems(
            apps: List<GameAppCandidate>,
            selected: Set<String>?,
            showAll: Boolean,
        ): List<GameAppItem> {
            val chosen = effectiveSelection(selected, apps)
            return apps
                .filter { showAll || it.isDetectedGame || it.packageName in chosen }
                .map { GameAppItem(it.packageName, it.label, it.isDetectedGame, it.packageName in chosen) }
                .sortedWith(compareBy({ !it.isSelected }, { it.label.lowercase() }))
        }

        private suspend fun loadInstalledCandidates(application: Application): List<GameAppCandidate> =
            withContext(Dispatchers.IO) {
                val pm = application.packageManager
                pm.getInstalledApplications(0)
                    .asSequence()
                    .filter { info ->
                        info.packageName != application.packageName &&
                            ((info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                pm.getLaunchIntentForPackage(info.packageName) != null)
                    }
                    .map { info ->
                        GameAppCandidate(
                            packageName = info.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            isDetectedGame = GameAppDetector.isGame(info),
                        )
                    }
                    .distinctBy { it.packageName }
                    .toList()
            }
    }
}
