package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.license.TrialManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(
    onNavigateToAddPlaylist: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val isRefreshingTV by viewModel.isRefreshingTV.collectAsState()
    val isRefreshingMovies by viewModel.isRefreshingMovies.collectAsState()
    val isRefreshingSeries by viewModel.isRefreshingSeries.collectAsState()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    var showDeleteDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    val addButtonFocus = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null) {
            kotlinx.coroutines.delay(100)
            try {
                if (playlists.isEmpty()) addButtonFocus.requestFocus()
                else firstCardFocus.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header matching DownloadsScreen style
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Playlists", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = sTheme.categoryText)
        }

        HorizontalDivider(color = sTheme.divider)

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.PlaylistPlay, null,
                        modifier = Modifier.size(64.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text("No playlists added yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Button(
                        onClick = onNavigateToAddPlaylist,
                        modifier = Modifier.focusRequester(addButtonFocus)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Playlist")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        firstButtonFocus = if (index == 0) firstCardFocus else null,
                        isRefreshingTV = isRefreshingTV,
                        isRefreshingMovies = isRefreshingMovies,
                        isRefreshingSeries = isRefreshingSeries,
                        onRefreshTV = { viewModel.refreshTVAndEPG(playlist) },
                        onRefreshMovies = { viewModel.refreshMovies(playlist) },
                        onRefreshSeries = { viewModel.refreshSeries(playlist) },
                        onDelete = { playlistToDelete = playlist; showDeleteDialog = true }
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onNavigateToAddPlaylist,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Another Playlist")
                    }
                }
            }
        }
    }

    if (showDeleteDialog && playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist?") },
            text = { Text("Are you sure you want to delete \"${playlistToDelete!!.name}\"? This will remove all channels, movies, series and EPG data.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePlaylist(playlistToDelete!!); showDeleteDialog = false; playlistToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Playlist card ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    firstButtonFocus: FocusRequester? = null,
    isRefreshingTV: Boolean,
    isRefreshingMovies: Boolean,
    isRefreshingSeries: Boolean,
    onRefreshTV: () -> Unit,
    onRefreshMovies: () -> Unit,
    onRefreshSeries: () -> Unit,
    onDelete: () -> Unit
) {
    val sTheme = LocalNexStreamTheme.current.sidebar

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Playlist info ─────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = sTheme.categoryText)
                    Text(playlist.type, style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    if (!playlist.xtreamUsername.isNullOrEmpty()) {
                        Text(playlist.xtreamUsername!!, style = MaterialTheme.typography.bodySmall,
                            color = sTheme.categoryText.copy(alpha = 0.5f))
                    }
                }
                // Delete button — individually focusable
                SettingsFocusableIconButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = "Delete playlist"
                )
            }

            HorizontalDivider(color = sTheme.divider.copy(alpha = 0.5f))

            // ── Refresh buttons — each individually focusable ─────────────────
            Text("Refresh", style = MaterialTheme.typography.labelSmall,
                color = sTheme.categoryText.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsFocusableButton(
                    label = "TV & EPG",
                    icon = Icons.Default.Tv,
                    isLoading = isRefreshingTV,
                    enabled = !isRefreshingTV,
                    focusRequester = firstButtonFocus,
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshTV
                )
                SettingsFocusableButton(
                    label = "Movies",
                    icon = Icons.Default.Movie,
                    isLoading = isRefreshingMovies,
                    enabled = !isRefreshingMovies,
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshMovies
                )
                SettingsFocusableButton(
                    label = "Series",
                    icon = Icons.Default.VideoLibrary,
                    isLoading = isRefreshingSeries,
                    enabled = !isRefreshingSeries,
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshSeries
                )
            }
        }
    }
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            },
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
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
    ) {
        Icon(icon, contentDescription, tint = if (isFocused) primary else tint)
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
    private val _isRefreshingTV = MutableStateFlow(false)
    val isRefreshingTV: StateFlow<Boolean> = _isRefreshingTV.asStateFlow()
    private val _isRefreshingMovies = MutableStateFlow(false)
    val isRefreshingMovies: StateFlow<Boolean> = _isRefreshingMovies.asStateFlow()
    private val _isRefreshingSeries = MutableStateFlow(false)
    val isRefreshingSeries: StateFlow<Boolean> = _isRefreshingSeries.asStateFlow()

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
                if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                    && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                    repository.fetchAndStoreMovies(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
            } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "Movies refresh failed", e) }
            finally { _isRefreshingMovies.value = false; repository.setLoadingVOD(false) }
        }
    }

    fun refreshSeries(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isRefreshingSeries.value = true; repository.setLoadingSeries(true)
            try {
                if (playlist.type == "XTREAM" && !playlist.xtreamHost.isNullOrEmpty()
                    && !playlist.xtreamUsername.isNullOrEmpty() && !playlist.xtreamPassword.isNullOrEmpty())
                    repository.fetchAndStoreSeries(playlist.id, playlist.xtreamHost!!, playlist.xtreamUsername!!, playlist.xtreamPassword!!)
            } catch (e: Exception) { android.util.Log.e("SettingsViewModel", "Series refresh failed", e) }
            finally { _isRefreshingSeries.value = false; repository.setLoadingSeries(false) }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.deletePlaylist(playlist.id) }
    }
}