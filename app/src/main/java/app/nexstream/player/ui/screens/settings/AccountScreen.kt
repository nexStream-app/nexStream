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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.R
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
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
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val refreshFr = remember { FocusRequester() }
    var refreshFocused by remember { mutableStateOf(false) }
    val uiStyle = rememberUiStyle()

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.account_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = sTheme.categoryText,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { viewModel.load() },
                    modifier = Modifier
                        .focusRequester(refreshFr)
                        .onFocusChanged { refreshFocused = it.isFocused },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (refreshFocused) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_refresh))
                }
            }
        }
        HorizontalDivider(color = sTheme.divider)
        } // end if uiStyle != MODERN

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                is AccountScreenState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is AccountScreenState.NoPlaylist -> {
                    SettingsSectionContainer(
                        title = stringResource(R.string.account_no_playlist_title),
                        icon = Icons.Default.Warning,
                        uiStyle = uiStyle
                    ) {
                        Text(
                            stringResource(R.string.account_no_playlist_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                is AccountScreenState.Error -> {
                    SettingsSectionContainer(
                        title = stringResource(R.string.common_error),
                        icon = Icons.Default.ErrorOutline,
                        uiStyle = uiStyle
                    ) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                is AccountScreenState.Success -> {
                    val info = s.info

                    // ── User Info ─────────────────────────────────────────────
                    SettingsSectionContainer(
                        title = stringResource(R.string.account_section_user_info),
                        icon = Icons.Default.Person,
                        uiStyle = uiStyle
                    ) {
                        SettingsInfoRow(stringResource(R.string.account_username), info.username)
                        SettingsInfoRow(
                            label = stringResource(R.string.account_status),
                            value = info.status,
                            valueColor = if (info.status.lowercase() == "active")
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        SettingsInfoRow(stringResource(R.string.account_expiry), info.expiry)
                        if (info.isTrial) {
                            SettingsInfoRow(stringResource(R.string.account_plan), stringResource(R.string.account_plan_trial))
                        }
                    }

                    // ── Connections ───────────────────────────────────────────
                    SettingsSectionContainer(
                        title = stringResource(R.string.account_section_connections),
                        icon = Icons.Default.DeviceHub,
                        uiStyle = uiStyle
                    ) {
                        SettingsInfoRow(stringResource(R.string.account_connections_active),  "${info.activeConnections}")
                        SettingsInfoRow(stringResource(R.string.account_connections_maximum), "${info.maxConnections}")
                        // Visual connection bar
                        val used = if (info.maxConnections > 0)
                            info.activeConnections.toFloat() / info.maxConnections.toFloat()
                        else 0f
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.account_connections_used),
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
                    SettingsSectionContainer(
                        title = stringResource(R.string.account_section_server),
                        icon = Icons.Default.Dns,
                        uiStyle = uiStyle
                    ) {
                        SettingsInfoRow(stringResource(R.string.account_server_host),     info.host)
                        SettingsInfoRow(stringResource(R.string.account_server_protocol), info.serverProtocol)
                        SettingsInfoRow(stringResource(R.string.account_server_port),     info.port)
                        SettingsInfoRow(stringResource(R.string.account_server_timezone), info.timezone)
                    }

                    Spacer(Modifier.height(120.dp))
                }
            }  // end when
        }      // end inner Column
    }          // end outer Column
}

// ── Legacy reusable components — kept for backward compatibility ───────────────

@Composable
private fun AccountInfoCard(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
