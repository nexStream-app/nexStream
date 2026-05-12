package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Data ─────────────────────────────────────────────────────────────────────

data class AccountInfo(
    val username: String,
    val host: String,
    val status: String,
    val expiry: String,
    val isTrial: Boolean,
    val maxConnections: Int,
    val activeConnections: Int,
    val timezone: String,
    val serverProtocol: String,
    val port: String
)

sealed class AccountScreenState {
    object Loading : AccountScreenState()
    data class Success(val info: AccountInfo) : AccountScreenState()
    data class Error(val message: String) : AccountScreenState()
    object NoPlaylist : AccountScreenState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AccountScreenState>(AccountScreenState.Loading)
    val state: StateFlow<AccountScreenState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = AccountScreenState.Loading
            try {
                val playlists = repository.getAllPlaylists().first()
                val playlist = playlists.firstOrNull { it.type == "XTREAM" }
                if (playlist == null) {
                    _state.value = AccountScreenState.NoPlaylist
                    return@launch
                }

                val host     = playlist.xtreamHost     ?: run { _state.value = AccountScreenState.NoPlaylist; return@launch }
                val username = playlist.xtreamUsername ?: run { _state.value = AccountScreenState.NoPlaylist; return@launch }
                val password = playlist.xtreamPassword ?: run { _state.value = AccountScreenState.NoPlaylist; return@launch }

                // Requires: suspend fun getXtreamAccountInfo(host, username, password) in PlaylistRepository
                // Add this to PlaylistRepository.kt:
                //   suspend fun getXtreamAccountInfo(host: String, username: String, password: String): XtreamAccountInfo? {
                //       return try { buildRetrofit(host).create(XtreamApiService::class.java).getAccountInfo(username, password) }
                //       catch (e: Exception) { null }
                //   }
                val info = try {
                    repository.getXtreamAccountInfo(host, username, password)
                } catch (e: Exception) {
                    _state.value = AccountScreenState.Error("Could not reach the server: ${e.message}")
                    return@launch
                }
                if (info == null) {
                    _state.value = AccountScreenState.Error("Could not reach the server. Check your connection.")
                    return@launch
                }

                val expiry = info.userInfo?.expDate?.let { ts ->
                    ts.toLongOrNull()?.let { epoch ->
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epoch * 1000L))
                    } ?: ts
                } ?: "No expiry"

                _state.value = AccountScreenState.Success(
                    AccountInfo(
                        username          = info.userInfo?.username ?: username,
                        host              = host,
                        status            = info.userInfo?.status?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                        expiry            = expiry,
                        isTrial           = info.userInfo?.isTrial == "1",
                        maxConnections    = info.userInfo?.maxConnections?.toIntOrNull() ?: 0,
                        activeConnections = info.userInfo?.activeCons?.toIntOrNull() ?: 0,
                        timezone          = info.serverInfo?.timezone ?: "Unknown",
                        serverProtocol    = info.serverInfo?.serverProtocol?.uppercase() ?: "Unknown",
                        port              = info.serverInfo?.port ?: info.serverInfo?.httpsPort ?: "Unknown"
                    )
                )
            } catch (e: Exception) {
                _state.value = AccountScreenState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AccountScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Refresh button
            val refreshFr = remember { FocusRequester() }
            var refreshFocused by remember { mutableStateOf(false) }
            FilledTonalButton(
                onClick = { viewModel.load() },
                modifier = Modifier
                    .focusRequester(refreshFr)
                    .then(if (firstItemFocusRequester == null) Modifier else Modifier),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (refreshFocused) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }

        when (val s = state) {
            is AccountScreenState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is AccountScreenState.NoPlaylist -> {
                AccountInfoCard(icon = Icons.Default.Warning, title = "No Xtream Playlist") {
                    Text(
                        "No Xtream account found. Add an Xtream playlist in Settings → Playlists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is AccountScreenState.Error -> {
                AccountInfoCard(icon = Icons.Default.ErrorOutline, title = "Error", iconTint = MaterialTheme.colorScheme.error) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is AccountScreenState.Success -> {
                val info = s.info

                // ── User Info ─────────────────────────────────────────────
                AccountInfoCard(icon = Icons.Default.Person, title = "User Info") {
                    AccountRow("Username",   info.username)
                    AccountRow("Status",     info.status,
                        valueColor = if (info.status.lowercase() == "active")
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    AccountRow("Expiry",     info.expiry)
                    if (info.isTrial) {
                        AccountRow("Plan", "Trial", valueColor = MaterialTheme.colorScheme.tertiary)
                    }
                }

                // ── Connections ───────────────────────────────────────────
                AccountInfoCard(icon = Icons.Default.DeviceHub, title = "Connections") {
                    AccountRow("Active",     "${info.activeConnections}")
                    AccountRow("Maximum",    "${info.maxConnections}")
                    // Visual connection bar
                    val used = if (info.maxConnections > 0)
                        info.activeConnections.toFloat() / info.maxConnections.toFloat()
                    else 0f
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Connections used",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${info.activeConnections} / ${info.maxConnections}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { used.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = when {
                                used >= 1f   -> MaterialTheme.colorScheme.error
                                used >= 0.8f -> MaterialTheme.colorScheme.tertiary
                                else         -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                // ── Server Info ───────────────────────────────────────────
                AccountInfoCard(icon = Icons.Default.Dns, title = "Server Info") {
                    AccountRow("Host",     info.host)
                    AccountRow("Protocol", info.serverProtocol)
                    AccountRow("Port",     info.port)
                    AccountRow("Timezone", info.timezone)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun AccountInfoCard(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun AccountRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(1.5f),
            maxLines = 1)
    }
}