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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.dao.WatchlistDao
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.nexstream.player.ui.theme.applySyncedPlayerPrefs
import app.nexstream.player.ui.theme.applySyncedThemePrefs
import app.nexstream.player.ui.theme.collectSyncablePlayerPrefs
import app.nexstream.player.ui.theme.collectSyncableThemePrefs
import app.nexstream.player.ui.theme.saveCloudSyncEnabled
import app.nexstream.player.update.AutoUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    val syncedCount: Int? = null,
    val profileStats: List<ProfileSyncStat> = emptyList(),
    val syncCloudEnabled: Boolean = true,
    val syncChannelFolders: Boolean = true,
    val syncAppearance: Boolean = true,
    val syncPlayerSettings: Boolean = true,
    val activeProfileId: String = "",
    val isRefreshingEpg: Boolean = false,
    val epgRefreshResult: String? = null,
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val profileSyncManager: ProfileSyncManager,
    private val watchlistSyncManager: WatchlistSyncManager,
    private val profileManager: ProfileManager,
    private val profileDao: ProfileDao,
    private val watchlistDao: WatchlistDao,
    private val playlistRepository: PlaylistRepository,
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
            _uiState.update { it.copy(isSyncing = true, syncedCount = null) }
            profileSyncManager.syncFromServer()
            profileSyncManager.pushProfiles()
            watchlistSyncManager.pushAllToServer()
            val profiles = profileDao.getAllProfilesOnce()
            profiles.forEach { profile -> watchlistSyncManager.syncFromServer(profile.id) }
            refreshStats()
            _uiState.update { it.copy(isSyncing = false, syncedCount = profiles.size) }
        }
    }

    fun refreshEpg() {
        if (_uiState.value.isRefreshingEpg) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingEpg = true, epgRefreshResult = null) }
            try {
                playlistRepository.refreshAllEpg()
                val count = playlistRepository.epgProgramCount.value
                _uiState.update { it.copy(epgRefreshResult = if (count > 0) "Loaded $count programmes" else (playlistRepository.epgFetchError.value ?: "No programmes found — check EPG source")) }
            } catch (e: Exception) {
                _uiState.update { it.copy(epgRefreshResult = "Failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isRefreshingEpg = false) }
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

    val firstFR   = firstItemFocusRequester ?: remember { FocusRequester() }
    val syncBtnFR = remember { FocusRequester() }
    val scope     = rememberCoroutineScope()
    var isCheckingUpdate  by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<String?>(null) }
    val isRefreshingEpg = uiState.isRefreshingEpg
    val epgRefreshResult = uiState.epgRefreshResult

    val strProfileCountOne   = stringResource(R.string.sync_profile_count_one)
    val strProfileCountOther = stringResource(R.string.sync_profile_count_other)
    val strExportSuccess     = stringResource(R.string.sync_export_success)
    val strExportFailed      = stringResource(R.string.sync_export_failed)
    val strRestoreSuccess    = stringResource(R.string.sync_restore_success)
    val strImportFailed      = stringResource(R.string.sync_import_failed)

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
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
            // ── Updates ───────────────────────────────────────────────────────
            SettingsSectionContainer(title = stringResource(R.string.sync_section_updates), icon = Icons.Default.SystemUpdate, uiStyle = uiStyle) {
                SettingsActionItem(
                    label          = "Check for Update",
                    description    = if (isCheckingUpdate) "Downloading…" else (updateCheckResult ?: "Check for a newer version of nexStream"),
                    value          = "",
                    uiStyle        = uiStyle,
                    focusRequester = firstFR,
                    showDivider    = false,
                    onClick        = {
                        if (!isCheckingUpdate) {
                            updateCheckResult = null
                            scope.launch {
                                isCheckingUpdate = true
                                updateCheckResult = try {
                                    AutoUpdateManager.checkNow(context)
                                } catch (_: Exception) {
                                    "Check failed — try again"
                                }
                                isCheckingUpdate = false
                            }
                        }
                    }
                )
            }

            // ── TV Guide (EPG) ────────────────────────────────────────────────
            SettingsSectionContainer(title = "TV Guide", icon = Icons.Default.CalendarToday, uiStyle = uiStyle) {
                SettingsActionItem(
                    label       = if (isRefreshingEpg) "Refreshing…" else "Refresh TV Guide",
                    description = epgRefreshResult ?: "Re-download programme schedule from your playlist provider",
                    value       = "",
                    uiStyle     = uiStyle,
                    showDivider = false,
                    onClick     = { viewModel.refreshEpg() }
                )
            }

            // ── Cloud Sync section ────────────────────────────────────────────
            SettingsSectionContainer(title = stringResource(R.string.sync_section_cloud), icon = Icons.Default.Cloud, uiStyle = uiStyle) {
                SettingsToggle(
                    label       = stringResource(R.string.sync_cloud_label),
                    description = stringResource(R.string.sync_cloud_desc),
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
                        stringResource(R.string.sync_what_syncs),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    SettingsToggle(
                        label       = stringResource(R.string.sync_channel_folders_label),
                        description = stringResource(R.string.sync_channel_folders_desc),
                        checked     = uiState.syncChannelFolders,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncChannelFolders(!uiState.syncChannelFolders) },
                    )
                    SettingsToggle(
                        label       = stringResource(R.string.sync_appearance_label),
                        description = stringResource(R.string.sync_appearance_desc),
                        checked     = uiState.syncAppearance,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncAppearance(!uiState.syncAppearance) },
                    )
                    SettingsToggle(
                        label       = stringResource(R.string.sync_player_settings_label),
                        description = stringResource(R.string.sync_player_settings_desc),
                        checked     = uiState.syncPlayerSettings,
                        uiStyle     = uiStyle,
                        onToggle    = { viewModel.setSyncPlayerSettings(!uiState.syncPlayerSettings) },
                    )

                    // ── Manual sync button ────────────────────────────────────
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    SettingsActionItem(
                        label          = if (uiState.isSyncing) stringResource(R.string.sync_syncing) else stringResource(R.string.sync_now),
                        description    = stringResource(R.string.sync_cloud_desc),
                        uiStyle        = uiStyle,
                        focusRequester = syncBtnFR,
                        showDivider    = false,
                        onClick        = { if (!uiState.isSyncing) viewModel.manualSync() }
                    )

                    uiState.syncedCount?.let { count ->
                        val msg = if (count == 1) strProfileCountOne
                                  else String.format(strProfileCountOther, count)
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
                SettingsSectionContainer(title = stringResource(R.string.sync_section_my_list), icon = Icons.Default.Favorite, uiStyle = uiStyle) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val statCount = uiState.profileStats.size
                        Text(
                            if (statCount == 1) strProfileCountOne
                            else String.format(strProfileCountOther, statCount),
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

            // ── Backup & Restore ──────────────────────────────────────────────
            var backupStatus by remember { mutableStateOf<String?>(null) }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        val playerPrefs = withContext(Dispatchers.IO) { context.collectSyncablePlayerPrefs() }
                        val themePrefs  = withContext(Dispatchers.IO) { context.collectSyncableThemePrefs() }
                        val json = JSONObject().apply {
                            put("version", 1)
                            playerPrefs.forEach { (k, v) ->
                                when (v) {
                                    is Boolean -> put(k, v)
                                    is Float   -> put(k, v.toDouble())
                                    is Int     -> put(k, v)
                                    is String  -> put(k, v)
                                    else       -> put(k, v.toString())
                                }
                            }
                            themePrefs.forEach { (k, v) ->
                                when (v) {
                                    is Boolean -> put(k, v)
                                    is Float   -> put(k, v.toDouble())
                                    is Int     -> put(k, v)
                                    is String  -> put(k, v)
                                    else       -> put(k, v.toString())
                                }
                            }
                        }
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(json.toString(2).toByteArray())
                            }
                        }
                        backupStatus = strExportSuccess
                    } catch (e: Exception) {
                        backupStatus = String.format(strExportFailed, e.message)
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        val jsonStr = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        } ?: return@launch
                        val obj = JSONObject(jsonStr)
                        val settingsMap = mutableMapOf<String, Any?>()
                        for (key in obj.keys()) { settingsMap[key] = obj.get(key) }
                        withContext(Dispatchers.IO) {
                            context.applySyncedPlayerPrefs(settingsMap)
                            context.applySyncedThemePrefs(settingsMap)
                        }
                        backupStatus = strRestoreSuccess
                    } catch (e: Exception) {
                        backupStatus = String.format(strImportFailed, e.message)
                    }
                }
            }

            SettingsSectionContainer(title = stringResource(R.string.sync_section_backup), icon = Icons.Default.SaveAlt, uiStyle = uiStyle) {
                val java8Date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                SettingsActionItem(
                    label       = stringResource(R.string.sync_export_label),
                    description = stringResource(R.string.sync_export_desc),
                    value       = "",
                    uiStyle     = uiStyle,
                    onClick     = { exportLauncher.launch("nexstream_backup_$java8Date.json") }
                )
                SettingsActionItem(
                    label       = stringResource(R.string.sync_import_label),
                    description = stringResource(R.string.sync_import_desc),
                    value       = "",
                    uiStyle     = uiStyle,
                    onClick     = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    showDivider = false
                )
                backupStatus?.let { status ->
                    Text(
                        text     = status,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileStatRow(stat: ProfileSyncStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stat.profileEmoji, style = MaterialTheme.typography.bodySmall)
        Text(
            stat.profileName,
            style    = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip(label = stringResource(R.string.sync_stat_movies),  count = stat.movies)
            StatChip(label = stringResource(R.string.sync_stat_series),  count = stat.series)
            StatChip(label = stringResource(R.string.sync_stat_live_tv), count = stat.liveTV)
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
