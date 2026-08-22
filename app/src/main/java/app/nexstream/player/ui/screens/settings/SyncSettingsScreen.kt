package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.dao.WatchlistDao
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getAutoUpdateEnabledFlow
import app.nexstream.player.ui.theme.saveAutoUpdateEnabled
import app.nexstream.player.ui.theme.saveCloudSyncEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSyncStat(
    val profileId: String,
    val profileName: String,
    val profileEmoji: String,
    val movies: Int,
    val series: Int,
    val liveTV: Int,
)

data class SyncSettingsUiState(
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val profileStats: List<ProfileSyncStat> = emptyList(),
    val syncCloudEnabled: Boolean = true,
    val syncChannelFolders: Boolean = true,
    val syncAppearance: Boolean = true,
    val syncPlayerSettings: Boolean = true,
    val activeProfileId: String = "",
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val profileSyncManager: ProfileSyncManager,
    private val watchlistSyncManager: WatchlistSyncManager,
    private val profileManager: ProfileManager,
    private val profileDao: ProfileDao,
    private val watchlistDao: WatchlistDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            profileManager.activeProfile.collect { profile ->
                if (profile != null) {
                    _uiState.update {
                        it.copy(
                            activeProfileId    = profile.id,
                            syncCloudEnabled   = profile.syncCloudEnabled,
                            syncChannelFolders = profile.syncChannelFolders,
                            syncAppearance     = profile.syncAppearance,
                            syncPlayerSettings = profile.syncPlayerSettings,
                        )
                    }
                }
            }
        }
        refreshStats()
    }

    fun setSyncCloudEnabled(enabled: Boolean, context: android.content.Context) {
        _uiState.update { it.copy(syncCloudEnabled = enabled) }
        persistSync { it.copy(syncCloudEnabled = enabled) }
        // Keep global DataStore gate in sync with active profile's preference
        viewModelScope.launch { context.saveCloudSyncEnabled(enabled) }
    }

    fun setSyncChannelFolders(enabled: Boolean) {
        _uiState.update { it.copy(syncChannelFolders = enabled) }
        persistSync { it.copy(syncChannelFolders = enabled) }
    }

    fun setSyncAppearance(enabled: Boolean) {
        _uiState.update { it.copy(syncAppearance = enabled) }
        persistSync { it.copy(syncAppearance = enabled) }
    }

    fun setSyncPlayerSettings(enabled: Boolean) {
        _uiState.update { it.copy(syncPlayerSettings = enabled) }
        persistSync { it.copy(syncPlayerSettings = enabled) }
    }

    private fun persistSync(transform: (ProfileEntity) -> ProfileEntity) {
        viewModelScope.launch {
            val profileId = _uiState.value.activeProfileId.takeIf { it.isNotEmpty() } ?: return@launch
            val profile = profileDao.getProfileById(profileId) ?: return@launch
            profileDao.upsertProfile(transform(profile).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val profiles = profileDao.getAllProfilesOnce()
            val allItems = watchlistDao.getAllItemsSuspend()
            val stats = profiles.map { profile ->
                val items = allItems.filter { it.profileId == profile.id }
                ProfileSyncStat(
                    profileId    = profile.id,
                    profileName  = profile.name,
                    profileEmoji = profile.emoji,
                    movies  = items.count { it.type == WatchlistType.MOVIE },
                    series  = items.count { it.type == WatchlistType.SERIES },
                    liveTV  = items.count { it.type == WatchlistType.CHANNEL },
                )
            }
            _uiState.update { it.copy(profileStats = stats) }
        }
    }

    fun manualSync() {
        if (!_uiState.value.syncCloudEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
            profileSyncManager.syncFromServer()
            profileSyncManager.pushProfiles()
            watchlistSyncManager.pushAllToServer()
            val profiles = profileDao.getAllProfilesOnce()
            profiles.forEach { profile -> watchlistSyncManager.syncFromServer(profile.id) }
            refreshStats()
            val count = profiles.size
            _uiState.update {
                it.copy(
                    isSyncing   = false,
                    syncMessage = "Synced $count profile${if (count == 1) "" else "s"}"
                )
            }
        }
    }
}

@Composable
fun SyncSettingsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val context  = LocalContext.current
    val nsTheme  = LocalNexStreamTheme.current
    val sTheme   = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle  = rememberUiStyle()

    val uiState by viewModel.uiState.collectAsState()
    val autoUpdateEnabled by context.getAutoUpdateEnabledFlow().collectAsState(initial = false)

    val firstFR   = firstItemFocusRequester ?: remember { FocusRequester() }
    val syncBtnFR = remember { FocusRequester() }
    val scope     = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Sync and Update", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Auto Update ───────────────────────────────────────────────────
            SettingsSectionContainer(title = "Updates", icon = Icons.Default.SystemUpdate, uiStyle = uiStyle) {
                SettingsToggle(
                    label          = "Auto Update",
                    description    = "Automatically check for and install app updates on startup. Off by default.",
                    checked        = autoUpdateEnabled,
                    uiStyle        = uiStyle,
                    focusRequester = firstFR,
                    onToggle       = { scope.launch { context.saveAutoUpdateEnabled(!autoUpdateEnabled) } },
                )
            }

            // ── Cloud Sync section ────────────────────────────────────────────
            SettingsSectionContainer(title = "Cloud Sync", icon = Icons.Default.Cloud, uiStyle = uiStyle) {
                SettingsToggle(
                    label       = "Cloud Sync",
                    description = "Sync data to nexstream.uk across your devices. Turn off to keep all data on this device only.",
                    checked     = uiState.syncCloudEnabled,
                    uiStyle     = uiStyle,
                    onToggle    = { viewModel.setSyncCloudEnabled(!uiState.syncCloudEnabled, context) },
                )

                if (uiState.syncCloudEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        "Choose what syncs for this profile",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    SettingsToggle(
                        label       = "Channel Folders",
                        description = "Sync your channel folder and group layout",
                        checked     = uiState.syncChannelFolders,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncChannelFolders(!uiState.syncChannelFolders) },
                    )
                    SettingsToggle(
                        label       = "Appearance",
                        description = "Sync theme, font and layout settings",
                        checked     = uiState.syncAppearance,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncAppearance(!uiState.syncAppearance) },
                    )
                    SettingsToggle(
                        label       = "Player Settings",
                        description = "Sync playback, buffer and subtitle settings",
                        checked     = uiState.syncPlayerSettings,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncPlayerSettings(!uiState.syncPlayerSettings) },
                    )

                    // ── Manual sync button ────────────────────────────────────
                    Spacer(Modifier.height(4.dp))
                    var syncFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick  = { if (!uiState.isSyncing) viewModel.manualSync() },
                        enabled  = !uiState.isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .focusRequester(syncBtnFR)
                            .onFocusChanged { syncFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)) {
                                    if (!uiState.isSyncing) viewModel.manualSync()
                                    true
                                } else false
                            },
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }

                    uiState.syncMessage?.let { msg ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── My List stats per profile ─────────────────────────────────────
            if (uiState.syncCloudEnabled && uiState.profileStats.isNotEmpty()) {
                SettingsSectionContainer(title = "My List", icon = Icons.Default.Favorite, uiStyle = uiStyle) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${uiState.profileStats.size} profile${if (uiState.profileStats.size == 1) "" else "s"} synced",
                            style      = MaterialTheme.typography.bodySmall,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        uiState.profileStats.forEach { stat ->
                            ProfileStatRow(stat)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatRow(stat: ProfileSyncStat) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stat.profileEmoji, style = MaterialTheme.typography.bodyMedium)
            Text(stat.profileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(label = "Movies",  count = stat.movies)
            StatChip(label = "Series",  count = stat.series)
            StatChip(label = "Live TV", count = stat.liveTV)
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$count",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
