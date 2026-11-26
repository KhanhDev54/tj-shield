package app.pwhs.blockads.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Quick Settings tile that turns Game Mode on or off. */
class GameModeTileService : TileService() {

    private val appPrefs: AppPreferences by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(appPrefs.gameMode.enabled.first()) }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val enabled = !appPrefs.gameMode.enabled.first()
            appPrefs.gameMode.setEnabled(enabled)
            render(enabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.game_mode_title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (enabled) R.string.game_mode_tile_on else R.string.game_mode_tile_off)
        }
        tile.updateTile()
    }
}
