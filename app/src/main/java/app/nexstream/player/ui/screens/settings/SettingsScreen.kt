package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import app.nexstream.player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.license.TrialManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getDeduplicateContentFlow
import app.nexstream.player.ui.theme.saveDeduplicateContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@Composable
fun SettingsScreen(
    onNavigateToAddPlaylist: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val playlistStatus by viewModel.playlistStatus.collectAsState()
    val isRefreshingTV by viewModel.isRefreshingTV.collectAsState()
    val isRefreshingMovies by viewModel.isRefreshingMovies.collectAsState()
    val isRefreshingSeries by viewModel.isRefreshingSeries.collectAsState()
    val isRefreshingAll by viewModel.isRefreshingAll.collectAsState()
    val isAnyRefreshing = isRefreshingTV || isRefreshingMovies || isRefreshingSeries || isRefreshingAll
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()

    val deduplicateContent by context.getDeduplicateContentFlow().collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    val addButtonFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.playlists_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.PlaylistPlay, null,
                        modifier = Modifier.size(64.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text(stringResource(R.string.playlists_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Button(
                        onClick = onNavigateToAddPlaylist,
                        modifier = Modifier.focusRequester(addButtonFocus)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.playlists_add))
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (playlists.size > 1) {
                    item(key = "dedup_toggle") {
                        DeduplicateToggleRow(
                            enabled = deduplicateContent,
                            onToggle = { viewModel.setDeduplicateContent(context, it) }
                        )
                    }
                }
                itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        playlistCount = playlists.size,
                        firstButtonFocus = if (index == 0) firstItemFocusRequester ?: firstCardFocus else null,
                        isRefreshingTV = isRefreshingTV,
                        isRefreshingMovies = isRefreshingMovies,
                        isRefreshingSeries = isRefreshingSeries,
                        isRefreshingAll = isRefreshingAll,
                        isAnyRefreshing = isAnyRefreshing,
                        connectivityStatus = playlistStatus[playlist.id],
                        onRefreshAll = { viewModel.refreshAll(playlist) },
                        onRefreshTV = { viewModel.refreshTVAndEPG(playlist) },
                        onRefreshMovies = { viewModel.refreshMovies(playlist) },
                        onRefreshSeries = { viewModel.refreshSeries(playlist) },
                        onDelete = { playlistToDelete = playlist; showDeleteDialog = true },
                        onMoveUp = if (index > 0) { { viewModel.movePriority(playlists, index, index - 1) } } else null,
                        onMoveDown = if (index < playlists.lastIndex) { { viewModel.movePriority(playlists, index, index + 1) } } else null,
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    var addPlaylistFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = onNavigateToAddPlaylist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { addPlaylistFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown &&
                                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                                ) { onNavigateToAddPlaylist(); true } else false
                            },
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (addPlaylistFocused) 2.dp else 1.dp,
                            color = if (addPlaylistFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (addPlaylistFocused) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor   = if (addPlaylistFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.playlists_add_another))
                    }
                }
            }
        }
    }

    if (showDeleteDialog && playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.playlists_delete_title)) },
            text = { Text(stringResource(R.string.playlists_delete_message, playlistToDelete!!.name)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePlaylist(playlistToDelete!!); showDeleteDialog = false; playlistToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; playlistToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

// ── Playlist card ─────────────────────────────────────────────────────────────

@Composable
private fun DeduplicateToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.playlists_deduplicate_label), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = sTheme.categoryText)
            Text(stringResource(R.string.playlists_deduplicate_description),
                style = MaterialTheme.typography.bodySmall,
                color = sTheme.categoryText.copy(alpha = 0.6f))
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    playlistCount: Int = 1,
    firstButtonFocus: FocusRequester? = null,
    isRefreshingTV: Boolean,
    isRefreshingMovies: Boolean,
    isRefreshingSeries: Boolean,
    isRefreshingAll: Boolean,
    isAnyRefreshing: Boolean,
    connectivityStatus: Boolean? = null,
    onRefreshAll: () -> Unit,
    onRefreshTV: () -> Unit,
    onRefreshMovies: () -> Unit,
    onRefreshSeries: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    val uiStyle = rememberUiStyle()

    val cardModifier = if (uiStyle == UiStyle.MODERN)
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(4.dp)
    else
        Modifier.fillMaxWidth()

    Column(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectivityDot(status = connectivityStatus)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = sTheme.categoryText, maxLines = 1)
                val meta = buildList {
                    add(playlist.type)
                    if (!playlist.xtreamUsername.isNullOrEmpty()) add(playlist.xtreamUsername!!)
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall,
                    color = sTheme.categoryText.copy(alpha = 0.55f), maxLines = 1)
            }
            if (playlistCount > 1) {
                SettingsFocusableIconButton(
                    onClick = { onMoveUp?.invoke() }, icon = Icons.Default.KeyboardArrowUp,
                    tint = if (onMoveUp != null) sTheme.categoryText else sTheme.categoryText.copy(alpha = 0.2f),
                    contentDescription = stringResource(R.string.playlists_priority_higher)
                )
                SettingsFocusableIconButton(
                    onClick = { onMoveDown?.invoke() }, icon = Icons.Default.KeyboardArrowDown,
                    tint = if (onMoveDown != null) sTheme.categoryText else sTheme.categoryText.copy(alpha = 0.2f),
                    contentDescription = stringResource(R.string.playlists_priority_lower)
                )
            }
            SettingsFocusableIconButton(
                onClick = onDelete, icon = Icons.Default.Delete,
                tint = MaterialTheme.colorScheme.error, contentDescription = stringResource(R.string.playlists_delete_playlist)
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.playlists_refresh_label),
                style = MaterialTheme.typography.labelSmall,
                color = sTheme.categoryText.copy(alpha = 0.55f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (playlist.type) {
                    "JELLYFIN" -> {
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_movies), icon = Icons.Default.Movie,
                            isLoading = isRefreshingMovies, enabled = !isAnyRefreshing,
                            focusRequester = firstButtonFocus, onClick = onRefreshMovies
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_series), icon = Icons.Default.VideoLibrary,
                            isLoading = isRefreshingSeries, enabled = !isAnyRefreshing,
                            onClick = onRefreshSeries
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_music), icon = Icons.Default.MusicNote,
                            isLoading = false, enabled = !isAnyRefreshing,
                            onClick = {}
                        )
                    }
                    "PLEX" -> {
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_movies), icon = Icons.Default.Movie,
                            isLoading = isRefreshingMovies, enabled = !isAnyRefreshing,
                            focusRequester = firstButtonFocus, onClick = onRefreshMovies
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_series), icon = Icons.Default.VideoLibrary,
                            isLoading = isRefreshingSeries, enabled = !isAnyRefreshing,
                            onClick = onRefreshSeries
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_music), icon = Icons.Default.MusicNote,
                            isLoading = false, enabled = !isAnyRefreshing,
                            onClick = {}
                        )
                    }
                    "M3U" -> {
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_tv), icon = Icons.Default.Tv,
                            isLoading = isRefreshingTV, enabled = !isAnyRefreshing,
                            focusRequester = firstButtonFocus, onClick = onRefreshTV
                        )
                    }
                    else -> { // XTREAM
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_all), icon = Icons.Default.Refresh,
                            isLoading = isRefreshingAll, enabled = !isAnyRefreshing,
                            focusRequester = firstButtonFocus, onClick = onRefreshAll
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_tv), icon = Icons.Default.Tv,
                            isLoading = isRefreshingTV, enabled = !isAnyRefreshing,
                            onClick = onRefreshTV
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_movies), icon = Icons.Default.Movie,
                            isLoading = isRefreshingMovies, enabled = !isAnyRefreshing,
                            onClick = onRefreshMovies
                        )
                        SettingsFocusableButton(
                            label = stringResource(R.string.playlists_btn_series), icon = Icons.Default.VideoLibrary,
                            isLoading = isRefreshingSeries, enabled = !isAnyRefreshing,
                            onClick = onRefreshSeries
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = LocalNexStreamTheme.current.sidebar.divider.copy(alpha = 0.4f))
    }
}

// ── Connectivity dot ──────────────────────────────────────────────────────────

@Composable
private fun ConnectivityDot(status: Boolean?) {
    val infiniteTransition = rememberInfiniteTransition(label = "connectivity")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )
    val dotColor = when (status) {
        true  -> Color(0xFF22C55E)
        false -> Color(0xFFEF4444)
        null  -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                dotColor.copy(alpha = if (status == null) pulseAlpha else 1f),
                CircleShape
            )
    )
}

// ── Focusable button helpers ──────────────────────────────────────────────────

@Composable
private fun SettingsFocusableButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            },
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) primary else outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, null, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsFocusableIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (isFocused) primary else tint, modifier = Modifier.size(20.dp))
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    val trialManager: TrialManager
) : ViewModel() {
    val playlists = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playlistStatus = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val playlistStatus: StateFlow<Map<String, Boolean?>> = _playlistStatus.asStateFlow()

    fun checkPlaylistConnectivity(playlists: List<PlaylistEntity>) {
        viewModelScope.launch {
            playlists.forEach { playlist ->
                launch(Dispatchers.IO) {
                    val urlStr = when (playlist.type) {
                        "XTREAM" -> playlist.xtreamHost
                        else -> playlist.url.takeIf { it.isNotBlank() }
                    }
                    val reachable = if (urlStr.isNullOrBlank()) false else try {
                        val connection = URL(urlStr).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "HEAD"
                        connection.instanceFollowRedirects = true
                        val code = connection.responseCode
                        connection.disconnect()
                        code in 100..499
                    } catch (_: Exception) { false }
                    _playlistStatus.update { it + (playlist.id to reachable) }
                }
            }
        }
    }

    private val _isRefreshingTV = MutableStateFlow(false)
    val isRefreshingTV: StateFlow<Boolean> = _isRefreshingTV.asStateFlow()
    private val _isRefreshingMovies = MutableStateFlow(false)
    val isRefreshingMovies: StateFlow<Boolean> = _isRefreshingMovies.asStateFlow()
    private val _isRefreshingSeries = MutableStateFlow(false)
    val isRefreshingSeries: StateFlow<Boolean> = _isRefreshingSeries.asStateFlow()
    private val _isRefreshingAll = MutableStateFlow(false)
    val isRefreshingAll: StateFlow<Boolean> = _isRefreshingAll.asStateFlow()

    fun refreshAll(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isRefreshingAll.value = true
            try {
                _isRefreshingTV.value = true; repository.setLoadingEPG(true)
                try {
                    if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                        && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                        repository.refreshChannelsAndEPG(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
                } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "All: TV/EPG failed", e) }
                finally { _isRefreshingTV.value = false; repository.setLoadingEPG(false) }

                _isRefreshingMovies.value = true; repository.setLoadingVOD(true)
                try {
                    if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                        && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                        repository.fetchAndStoreMovies(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
                } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "All: Movies failed", e) }
                finally { _isRefreshingMovies.value = false; repository.setLoadingVOD(false) }
                launch { runCatching { repository.fetchCertificationsForMovies(playlist.id) } }

                _isRefreshingSeries.value = true; repository.setLoadingSeries(true)
                try {
                    if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                        && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                        repository.fetchAndStoreSeries(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
                } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "All: Series failed", e) }
                finally { _isRefreshingSeries.value = false; repository.setLoadingSeries(false) }
                launch { runCatching { repository.fetchCertificationsForSeries(playlist.id) } }
            } finally {
                _isRefreshingAll.value = false
            }
        }
    }

    fun refreshTVAndEPG(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isRefreshingTV.value = true; repository.setLoadingEPG(true)
            try {
                if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                    && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                    repository.refreshChannelsAndEPG(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
            } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "TV/EPG refresh failed", e) }
            finally { _isRefreshingTV.value = false; repository.setLoadingEPG(false) }
        }
    }

    fun refreshMovies(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isRefreshingMovies.value = true; repository.setLoadingVOD(true)
            try {
                when {
                    playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                        && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty() ->
                        repository.fetchAndStoreMovies(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
                    playlist.type == "PLEX" ->
                        repository.syncPlexLibrary(playlist)
                }
            } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "Movies refresh failed", e) }
            finally { _isRefreshingMovies.value = false; repository.setLoadingVOD(false) }
            // Fetch missing certifications in background after refresh
            launch { runCatching { repository.fetchCertificationsForMovies(playlist.id) } }
        }
    }

    fun refreshSeries(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isRefreshingSeries.value = true; repository.setLoadingSeries(true)
            try {
                when {
                    playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                        && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty() ->
                        repository.fetchAndStoreSeries(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
                    playlist.type == "PLEX" ->
                        repository.syncPlexLibrary(playlist)
                }
            } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "Series refresh failed", e) }
            finally { _isRefreshingSeries.value = false; repository.setLoadingSeries(false) }
            // Fetch missing certifications in background after refresh
            launch { runCatching { repository.fetchCertificationsForSeries(playlist.id) } }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.deletePlaylist(playlist.id) }
    }

    fun movePriority(playlists: List<PlaylistEntity>, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val reordered = playlists.toMutableList().also { list ->
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            reordered.forEachIndexed { index, playlist ->
                repository.updatePlaylistSortIndex(playlist.id, index)
            }
        }
    }

    fun setDeduplicateContent(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch { context.saveDeduplicateContent(enabled) }
    }
}