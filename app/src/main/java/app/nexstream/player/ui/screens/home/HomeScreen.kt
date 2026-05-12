package app.nexstream.player.ui.screens.home


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddPlaylist: () -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NexStream") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (selectedPlaylistId == null) {
                FloatingActionButton(onClick = onNavigateToAddPlaylist) {
                    Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                selectedPlaylistId == null -> {
                    if (playlists.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Welcome to NexStream",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add a playlist to get started",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                var showMenu by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectPlaylist(playlist.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "${playlist.type} • Added ${
                                                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                                        .format(Date(playlist.addedDate))
                                                }",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Options"
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit") },
                                                    onClick = {
                                                        showMenu = false
                                                        playlistToEdit = playlist
                                                        showEditDialog = true
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Edit, contentDescription = null)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        showMenu = false
                                                        viewModel.deletePlaylist(playlist.id)
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Delete, contentDescription = null)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { viewModel.selectPlaylist(null) },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("← Back to Playlists")
                        }

                        if (channels.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No channels found",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(channels, key = { it.id }) { channel ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToPlayer(channel.streamUrl) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            if (!channel.groupTitle.isNullOrEmpty()) {
                                                Text(
                                                    text = channel.groupTitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    playlistToEdit?.let { playlist ->
        if (showEditDialog) {
            var editedName by remember { mutableStateOf(playlist.name) }

            AlertDialog(
                onDismissRequest = {
                    showEditDialog = false
                    playlistToEdit = null
                },
                title = { Text("Edit Playlist") },
                text = {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updatePlaylistName(playlist.id, editedName)
                            showEditDialog = false
                            playlistToEdit = null
                        },
                        enabled = editedName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showEditDialog = false
                            playlistToEdit = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: StateFlow<List<ChannelEntity>> = _selectedPlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId != null) {
                repository.getChannelsByPlaylist(playlistId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun updatePlaylistName(playlistId: String, newName: String) {
        viewModelScope.launch {
            val currentPlaylists = playlists.value
            val playlist = currentPlaylists.find { it.id == playlistId }
            playlist?.let {
                val updated = it.copy(name = newName)
                repository.updatePlaylist(updated)
            }
        }
    }
}