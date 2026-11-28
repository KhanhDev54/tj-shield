package app.pwhs.blockads.ui.gamemode

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.httpsfiltering.component.AppIcon
import app.pwhs.blockads.ui.settings.component.SectionHeader
import app.pwhs.blockads.ui.settings.component.SettingsCard
import app.pwhs.blockads.ui.settings.component.SettingsToggleItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeScreen(
    modifier: Modifier = Modifier,
    viewModel: GameModeViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_mode_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Filled.SportsEsports,
                        iconTint = Color(0xFF7C3AED),
                        title = stringResource(R.string.game_mode_enable_title),
                        subtitle = stringResource(R.string.game_mode_enable_desc),
                        isChecked = state.enabled,
                        onCheckedChange = viewModel::setEnabled
                    )
                }
                if (state.enabled && !state.protectionRunning) {
                    Text(
                        text = stringResource(R.string.game_mode_protection_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.game_mode_games_header),
                    description = stringResource(R.string.game_mode_games_desc)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setShowAllApps(!state.showAllApps) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.game_mode_show_all_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.showAllApps, onCheckedChange = viewModel::setShowAllApps)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                state.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                state.items.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.game_mode_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                    )
                }

                else -> items(state.items, key = { it.packageName }) { item ->
                    GameAppRow(
                        item = item,
                        onToggle = { viewModel.setGameSelected(item.packageName, !item.isSelected) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameAppRow(item: GameAppItem, onToggle: () -> Unit) {
    val context = LocalContext.current
    val icon: Drawable? = remember(item.packageName) {
        runCatching { context.packageManager.getApplicationIcon(item.packageName) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(drawable = icon, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.isDetectedGame) {
                Text(
                    text = stringResource(R.string.game_mode_detected_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = item.isSelected, onCheckedChange = { onToggle() })
    }
}
