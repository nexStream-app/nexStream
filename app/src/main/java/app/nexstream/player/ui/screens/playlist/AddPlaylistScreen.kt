package app.nexstream.player.ui.screens.playlist

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.ui.components.TvKeyboard
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import androidx.compose.ui.input.key.*

// -- Device type detection -----------------------------------------------------

fun isTvDevice(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)
                ?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

// -- QR code helper ------------------------------------------------------------

fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bmp ->
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (bits[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
} catch (_: Exception) { null }

// -- Poll state ----------------------------------------------------------------

sealed class MacPollState {
    object Idle              : MacPollState()
    object Polling           : MacPollState()
    object Found             : MacPollState()
    object LicenceActivating : MacPollState()
    object LicenceActivated  : MacPollState()
    data class Error(val message: String) : MacPollState()
}

// -- ViewModel -----------------------------------------------------------------

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val licenceManager: LicenceManager
) : ViewModel() {

    var isLoading        by mutableStateOf(false); private set
    var errorMessage     by mutableStateOf<String?>(null); private set
    var importStarted    by mutableStateOf(false); private set
    var importIsXtream   by mutableStateOf(false); private set
    var importIsJellyfin by mutableStateOf(false); private set
    var importIsPlex     by mutableStateOf(false); private set

    // ── Plex PIN flow state ───────────────────────────────────────────────────
    enum class PlexPinState { IDLE, REQUESTING, WAITING, FETCHING_SERVERS, READY, ERROR, EXPIRED }

    var plexPinCode        by mutableStateOf<String?>(null); private set
    var plexPinId          by mutableStateOf<Long?>(null); private set
    var plexToken          by mutableStateOf<String?>(null); private set
    var plexServers        by mutableStateOf<List<app.nexstream.player.data.remote.PlexDevice>>(emptyList()); private set
    var plexSelectedServer by mutableStateOf<app.nexstream.player.data.remote.PlexDevice?>(null)
    var plexPinState       by mutableStateOf(PlexPinState.IDLE); private set

    // Start in Polling state so DeviceBar immediately shows "Listening for playlist..."
    private val _pollState = MutableStateFlow<MacPollState>(MacPollState.Polling)
    val pollState: StateFlow<MacPollState> = _pollState

    // ── Import progress (forwarded from repository) ───────────────────────
    val channelImportedCount: StateFlow<Int> = repository.channelImportedCount
    val isLoadingEPG: StateFlow<Boolean>     = repository.isLoadingEPG
    val epgProgramCount: StateFlow<Int>      = repository.epgProgramCount
    val isLoadingVOD: StateFlow<Boolean>     = repository.isLoadingVOD
    val vodLoadedCount: StateFlow<Int>       = repository.vodLoadedCount
    val isLoadingSeries: StateFlow<Boolean>  = repository.isLoadingSeries
    val seriesLoadedCount: StateFlow<Int>    = repository.seriesLoadedCount
    val isLoadingMusic: StateFlow<Boolean>   = repository.isLoadingMusic
    val musicLoadedCount: StateFlow<Int>     = repository.musicLoadedCount
    val isBackgroundSyncComplete: StateFlow<Boolean> = repository.isBackgroundSyncComplete

    fun getDeviceId(): String = licenceManager.getDeviceId()

    init {
        val deviceId   = licenceManager.getDeviceId()
        val screenOpenedAt = System.currentTimeMillis()

        // Observe FCM-delivered playlist assignments from NexStreamFirebaseService
        viewModelScope.launch {
            repository.pendingPlaylistAssignment
                .filterNotNull()
                .collect { event ->
                    if (System.currentTimeMillis() - event.triggeredAt > 10 * 60 * 1000L) {
                        repository.clearPendingPlaylistAssignment()
                        return@collect
                    }
                    repository.clearPendingPlaylistAssignment()
                    handlePlaylistEvent(event)
                }
        }

        // HTTP polling fallback — fires every 4 s in case FCM is unavailable
        viewModelScope.launch {
            delay(2000)
            // On fresh install look back 30 minutes only — enough for the QR/web setup flow
            // (~15 min QR window + ~5 min to submit) without auto-importing stale credentials
            // from a previous install that could hijack the UI before the user can enter new ones.
            val hasPlaylists = repository.getAllPlaylists().first().isNotEmpty()
            val firstSince   = if (hasPlaylists) screenOpenedAt else (screenOpenedAt - 30L * 60 * 1000L)
            val firstEvent   = repository.pollForPendingPlaylist(deviceId, firstSince)
            if (firstEvent != null && _pollState.value == MacPollState.Polling) {
                handlePlaylistEvent(firstEvent); return@launch
            }
            delay(2000)
            while (_pollState.value == MacPollState.Polling) {
                val event = repository.pollForPendingPlaylist(deviceId, screenOpenedAt)
                if (event != null && _pollState.value == MacPollState.Polling) {
                    handlePlaylistEvent(event)
                    break
                }
                delay(4000)
            }
        }
    }

    private suspend fun handlePlaylistEvent(event: PlaylistRepository.PlaylistAssignedEvent) {
        _pollState.update { MacPollState.Found }
        repository.resetImportState()
        importIsXtream   = event.type == "xtream"
        importIsJellyfin = event.type == "jellyfin"
        importStarted    = true
        val result = when (event.type) {
            "xtream" -> repository.addXtreamPlaylist(
                username = event.username,
                host     = event.serverUrl,
                password = event.password
            )
            "m3u" -> repository.addM3UPlaylist(
                name = "My Playlist",
                url  = event.m3uUrl
            )
            "jellyfin" -> repository.addJellyfinPlaylist(
                host     = event.serverUrl,
                username = event.username,
                password = event.password
            )
            else -> Result.failure(Exception("Unknown playlist type: ${event.type}"))
        }
        result.onFailure { e ->
            importStarted = false
            _pollState.update { MacPollState.Error(e.message ?: "Import failed") }
        }
    }

    fun addM3UPlaylist(name: String, url: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.resetImportState()
            importIsXtream = false; importStarted = true
            repository.addM3UPlaylist(name, url)
                .onSuccess { isLoading = false }
                .onFailure { isLoading = false; importStarted = false; errorMessage = it.message ?: "Failed" }
        }
    }

    fun addXtreamPlaylist(username: String, host: String, password: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.resetImportState()
            importIsXtream = true; importStarted = true
            repository.addXtreamPlaylist(username = username, host = host, password = password)
                .onSuccess { isLoading = false }
                .onFailure { isLoading = false; importStarted = false; errorMessage = it.message ?: "Failed" }
        }
    }

    fun addJellyfinPlaylist(host: String, username: String, password: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.resetImportState()
            importIsJellyfin = true; importStarted = true
            repository.addJellyfinPlaylist(host = host, username = username, password = password)
                .onSuccess { isLoading = false }
                .onFailure { isLoading = false; importStarted = false; errorMessage = it.message ?: "Connection failed" }
        }
    }

    fun requestPlexPin(clientId: String) {
        viewModelScope.launch {
            plexPinState = PlexPinState.REQUESTING
            repository.requestPlexPin(clientId)
                .onSuccess { (id, code) ->
                    plexPinId = id; plexPinCode = code
                    plexPinState = PlexPinState.WAITING
                    pollForPlexToken(id, clientId)
                }
                .onFailure { plexPinState = PlexPinState.ERROR; errorMessage = it.message }
        }
    }

    private fun pollForPlexToken(pinId: Long, clientId: String) {
        viewModelScope.launch {
            repeat(150) { // 5 minutes at 2s intervals
                kotlinx.coroutines.delay(2000)
                repository.checkPlexPin(pinId, clientId)
                    .onSuccess { token ->
                        if (token != null) {
                            plexToken = token
                            plexPinState = PlexPinState.FETCHING_SERVERS
                            repository.getPlexServers(token)
                                .onSuccess { servers ->
                                    plexServers = servers.filter { it.connections?.isNotEmpty() == true }
                                    plexSelectedServer = plexServers.firstOrNull()
                                    plexPinState = PlexPinState.READY
                                }
                                .onFailure { plexPinState = PlexPinState.ERROR; errorMessage = it.message }
                            return@launch
                        }
                    }
                if (plexPinState != PlexPinState.WAITING) return@launch
            }
            if (plexPinState == PlexPinState.WAITING) plexPinState = PlexPinState.EXPIRED
        }
    }

    fun addPlexServer() {
        val server = plexSelectedServer ?: return
        val token  = plexToken ?: return
        // Pick best connection: prefer non-relay HTTPS, then non-relay HTTP, then relay
        val conn = server.connections?.firstOrNull { !it.relay && it.uri.startsWith("https") }
            ?: server.connections?.firstOrNull { !it.relay }
            ?: server.connections?.firstOrNull()
            ?: return
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.resetImportState()
            importIsJellyfin = false; importIsXtream = false; importIsPlex = true
            importStarted = true
            repository.addPlexPlaylist(server.name, conn.uri, token)
                .onSuccess { isLoading = false }
                .onFailure { isLoading = false; importStarted = false; importIsPlex = false; errorMessage = it.message }
        }
    }

}

// -- Screen --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    onBack: () -> Unit,
    isFirstRun: Boolean = false,
    viewModel: AddPlaylistViewModel = hiltViewModel()
) {
    val context    = LocalContext.current
    val pollState  by viewModel.pollState.collectAsState()
    val isTv       = remember { isTvDevice(context) }
    val deviceId   = remember { viewModel.getDeviceId() }
    val cancelLabel = if (isFirstRun) "Quit" else "Cancel"
    val onCancel: () -> Unit = if (isFirstRun) {
        { (context as? android.app.Activity)?.finish() }
    } else onBack

    var keyboardTarget by remember { mutableStateOf<String?>(null) }
    var keyboardValue  by remember { mutableStateOf("") }
    var showKeyboard   by remember { mutableStateOf(false) }

    var showQrDialog by remember { mutableStateOf(false) }

    var host       by remember { mutableStateOf("") }
    var username   by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var m3uName    by remember { mutableStateOf("") }
    var m3uUrl     by remember { mutableStateOf("") }
    var jfHost     by remember { mutableStateOf("") }
    var jfUsername by remember { mutableStateOf("") }
    var jfPassword by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val importStarted = viewModel.importStarted
    if (importStarted) {
        PlaylistImportProgressScreen(viewModel = viewModel, onDone = onBack)
        return
    }

    fun openKeyboard(fieldName: String, current: String) {
        keyboardTarget = fieldName; keyboardValue = current; showKeyboard = true
    }

    fun commitKeyboard() {
        when (keyboardTarget) {
            "host"       -> host       = keyboardValue
            "username"   -> username   = keyboardValue
            "password"   -> password   = keyboardValue
            "m3uName"    -> m3uName    = keyboardValue
            "m3uUrl"     -> m3uUrl     = keyboardValue
            "jfHost"     -> jfHost     = keyboardValue
            "jfUsername" -> jfUsername = keyboardValue
            "jfPassword" -> jfPassword = keyboardValue
        }
        showKeyboard = false
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // -- Device ID / status bar --------------------------------------------
        DeviceBar(deviceId = deviceId, pollState = pollState)

        // -- Tabs + forms ------------------------------------------------------
        TabRow(selectedTabIndex = selectedTab) {
            listOf("Xtream Codes", "M3U URL", "Jellyfin", "Plex").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(title, fontSize = 13.sp) })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedTab == 0) {
                InputField(isTv = isTv, label = "Server URL", value = host,
                    placeholder = "http://example.com:8080", onValueChange = { host = it },
                    onFocusSelect = { openKeyboard("host", host) })
                InputField(isTv = isTv, label = "Username", value = username,
                    onValueChange = { username = it },
                    onFocusSelect = { openKeyboard("username", username) })
                InputField(isTv = isTv, label = "Password", value = password,
                    onValueChange = { password = it }, isPassword = true,
                    onFocusSelect = { openKeyboard("password", password) })
                FormButtons(
                    isLoading = viewModel.isLoading, errorMessage = viewModel.errorMessage,
                    isValid = host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    onBack = onCancel, cancelLabel = cancelLabel,
                    onSubmit = { viewModel.addXtreamPlaylist(username, host, password) }
                )
            } else if (selectedTab == 1) {
                InputField(isTv = isTv, label = "Playlist Name", value = m3uName,
                    onValueChange = { m3uName = it },
                    onFocusSelect = { openKeyboard("m3uName", m3uName) })
                InputField(isTv = isTv, label = "M3U URL", value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    placeholder = "http://example.com/playlist.m3u",
                    onFocusSelect = { openKeyboard("m3uUrl", m3uUrl) })
                FormButtons(
                    isLoading = viewModel.isLoading, errorMessage = viewModel.errorMessage,
                    isValid = m3uName.isNotBlank() && m3uUrl.isNotBlank(),
                    onBack = onCancel, cancelLabel = cancelLabel,
                    onSubmit = { viewModel.addM3UPlaylist(m3uName, m3uUrl) }
                )
            } else if (selectedTab == 2) {
                InputField(isTv = isTv, label = "Server URL", value = jfHost,
                    placeholder = "http://jellyfin.local:8096", onValueChange = { jfHost = it },
                    onFocusSelect = { openKeyboard("jfHost", jfHost) })
                InputField(isTv = isTv, label = "Username", value = jfUsername,
                    onValueChange = { jfUsername = it },
                    onFocusSelect = { openKeyboard("jfUsername", jfUsername) })
                InputField(isTv = isTv, label = "Password", value = jfPassword,
                    onValueChange = { jfPassword = it }, isPassword = true,
                    onFocusSelect = { openKeyboard("jfPassword", jfPassword) })
                FormButtons(
                    isLoading = viewModel.isLoading, errorMessage = viewModel.errorMessage,
                    isValid = jfHost.isNotBlank() && jfUsername.isNotBlank(),
                    onBack = onCancel, cancelLabel = cancelLabel,
                    onSubmit = { viewModel.addJellyfinPlaylist(jfHost, jfUsername, jfPassword) }
                )
            } else {
                PlexTab(
                    viewModel = viewModel,
                    deviceId  = deviceId,
                    onCancel  = onCancel,
                    cancelLabel = cancelLabel
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(4.dp))

            QrButton(
                deviceId = deviceId,
                onShowQr = { showQrDialog = true }
            )
        }
    }

    // -- TV Keyboard -----------------------------------------------------------
    if (showKeyboard && isTv) {
        ModalBottomSheet(
            onDismissRequest = { showKeyboard = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle       = null,
            containerColor   = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(keyboardTarget?.replaceFirstChar { it.uppercase() } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (keyboardValue.isEmpty()) "..." else keyboardValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (keyboardValue.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                }
                TvKeyboard(value = keyboardValue, onValueChange = { keyboardValue = it },
                    onDone = { commitKeyboard() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // -- QR Dialog -------------------------------------------------------------
    if (showQrDialog) {
        QrDialog(deviceId = deviceId, onDismiss = { showQrDialog = false }, onPlaylistDetected = { onBack() })
    }
}

// -- Plex tab ------------------------------------------------------------------

@Composable
private fun PlexTab(
    viewModel: AddPlaylistViewModel,
    deviceId: String,
    onCancel: () -> Unit,
    cancelLabel: String
) {
    val pinState = viewModel.plexPinState
    val pinCode  = viewModel.plexPinCode
    val servers  = viewModel.plexServers

    val clipboardManager = LocalClipboardManager.current
    var pinCopied by remember { mutableStateOf(false) }
    LaunchedEffect(pinCopied) {
        if (pinCopied) { kotlinx.coroutines.delay(2000); pinCopied = false }
    }

    when (pinState) {
        AddPlaylistViewModel.PlexPinState.IDLE -> {
            var connectFocused by remember { mutableStateOf(false) }
            Text(
                "Sign in to your Plex account to link your media server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.requestPlexPin(deviceId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { connectFocused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                        ) { viewModel.requestPlexPin(deviceId); true } else false
                    },
                border = ButtonDefaults.outlinedButtonBorder.copy(width = if (connectFocused) 2.dp else 1.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (connectFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor   = if (connectFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            ) { Text("Connect with Plex", fontSize = 14.sp) }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCancel, enabled = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text(cancelLabel, fontSize = 13.sp) }
        }

        AddPlaylistViewModel.PlexPinState.REQUESTING -> {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Requesting PIN…", style = MaterialTheme.typography.bodySmall)
            }
        }

        AddPlaylistViewModel.PlexPinState.WAITING -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Your Plex PIN", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = pinCode ?: "----",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(pinCode ?: ""))
                            pinCopied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (pinCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (pinCopied) "Copied!" else "Copy PIN")
                    }
                    Text(
                        "Visit app.plex.tv/desktop on any device and sign in to link your account",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Waiting for sign-in…", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        AddPlaylistViewModel.PlexPinState.FETCHING_SERVERS -> {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Fetching servers…", style = MaterialTheme.typography.bodySmall)
            }
        }

        AddPlaylistViewModel.PlexPinState.READY -> {
            Text("Select your Plex Media Server", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            servers.forEach { server ->
                val isSelected = viewModel.plexSelectedServer?.clientIdentifier == server.clientIdentifier
                var itemFocused by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { itemFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                            ) { viewModel.plexSelectedServer = server; true } else false
                        },
                    onClick = { viewModel.plexSelectedServer = server },
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isSelected  -> MaterialTheme.colorScheme.primaryContainer
                        itemFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else        -> MaterialTheme.colorScheme.surface
                    },
                    border = if (isSelected || itemFocused)
                        ButtonDefaults.outlinedButtonBorder.copy(width = if (isSelected) 2.dp else 1.dp)
                    else null,
                    tonalElevation = if (itemFocused) 4.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface)
                            val connUri = server.connections?.firstOrNull { !it.relay }?.uri
                                ?: server.connections?.firstOrNull()?.uri ?: ""
                            if (connUri.isNotEmpty()) {
                                Text(connUri,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1)
                            }
                        }
                        if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (servers.isEmpty()) {
                Text("No Plex servers found on your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            FormButtons(
                isLoading = viewModel.isLoading,
                errorMessage = viewModel.errorMessage,
                isValid = viewModel.plexSelectedServer != null,
                onBack = onCancel,
                cancelLabel = cancelLabel,
                onSubmit = { viewModel.addPlexServer() }
            )
        }

        AddPlaylistViewModel.PlexPinState.EXPIRED -> {
            Text("PIN expired. Please try again.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FormButtons(
                isLoading = false, errorMessage = null, isValid = true,
                onBack = onCancel, cancelLabel = cancelLabel,
                onSubmit = { viewModel.requestPlexPin(deviceId) }
            )
        }

        AddPlaylistViewModel.PlexPinState.ERROR -> {
            Text(viewModel.errorMessage ?: "An error occurred.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FormButtons(
                isLoading = false, errorMessage = null, isValid = true,
                onBack = onCancel, cancelLabel = cancelLabel,
                onSubmit = { viewModel.requestPlexPin(deviceId) }
            )
        }
    }
}

// -- Device ID bar -------------------------------------------------------------

@Composable
private fun DeviceBar(deviceId: String, pollState: MacPollState) {
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Auto-configure via nexstream.uk", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)) {
                    Text("Device ID", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(deviceId, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        letterSpacing = 1.5.sp)
                }
                FilledTonalIconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(deviceId)); copied = true },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (copied) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) { Text(if (copied) "OK" else "C", fontSize = 10.sp) }
            }

            val (statusColor, statusText, spinning) = when (pollState) {
                is MacPollState.Polling          -> Triple(MaterialTheme.colorScheme.primary, "Listening for playlist...", true)
                is MacPollState.Found            -> Triple(MaterialTheme.colorScheme.secondary, "Playlist found! Importing...", false)
                is MacPollState.LicenceActivating -> Triple(MaterialTheme.colorScheme.tertiary, "Activating licence...", true)
                is MacPollState.LicenceActivated  -> Triple(MaterialTheme.colorScheme.primary, "Licence activated!", false)
                is MacPollState.Error            -> Triple(MaterialTheme.colorScheme.error, "Error: ${pollState.message}", false)
                else                             -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "", false)
            }
            if (statusText.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (spinning) CircularProgressIndicator(modifier = Modifier.size(11.dp),
                        color = statusColor, strokeWidth = 2.dp)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
        }
    }
}

// -- QR button -----------------------------------------------------------------

@Composable
private fun QrButton(deviceId: String, onShowQr: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    OutlinedButton(
        onClick = onShowQr,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester) // ADD THIS
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e -> // ADD THIS
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter ||
                            e.key == Key.NumPadEnter ||
                            e.key == Key.DirectionCenter)
                ) {
                    onShowQr()
                    true
                } else false
            },
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (isFocused) 2.dp else 1.dp
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isFocused)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
            contentColor = if (isFocused)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.primary
        )
    ) {
        Text("Scan QR to add playlist from your phone", fontSize = 13.sp)
    }
}

// -- QR dialog -----------------------------------------------------------------

@Composable
private fun QrDialog(deviceId: String, onDismiss: () -> Unit, onPlaylistDetected: () -> Unit) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expired  by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf<String?>(null) } // ADD THIS

    LaunchedEffect(deviceId) {
        try {
            val json = JSONObject(withContext(Dispatchers.IO) {
                URL("https://nexstream.uk/api/generate_token.php?device_id=$deviceId").readText()
            })
            val token = json.optString("token")
            if (token.isNotEmpty()) {
                val url = "https://nexstream.uk/add?token=$token"
                qrBitmap = generateQrBitmap(url)
                delay(15 * 60 * 1000L)
                expired = true
            } else {
                error = "No token returned" // ADD THIS
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to generate QR" // ADD THIS
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp) {
            Column(
                modifier = Modifier.padding(24.dp).widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Playlist from Phone", style = MaterialTheme.typography.titleMedium)
                when {
                    expired -> Text("QR code expired. Press back and try again.",
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall)
                    error != null -> { // ADD THIS BRANCH
                        Text("Could not generate QR code.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall)
                        Text(error ?: "", // Shows actual error for debugging
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall)
                        OutlinedButton( // Retry button
                            onClick = { error = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Retry") }
                    }
                    qrBitmap != null -> {
                        Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR Code",
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color.White).padding(8.dp))
                        Text("Scan with your phone.\nFill in your playlist details on nexstream.uk\nThe app will auto-configure when submitted.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Valid for 15 minutes", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Generating QR code...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

// -- InputField ----------------------------------------------------------------

@Composable
private fun InputField(
    isTv: Boolean, label: String, value: String, placeholder: String = "",
    isPassword: Boolean = false, onValueChange: (String) -> Unit, onFocusSelect: () -> Unit
) {
    if (isTv) {
        TvField(label = label, value = value, placeholder = placeholder, onFocusSelect = onFocusSelect)
    } else {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

// -- TV field ------------------------------------------------------------------

@Composable
private fun TvField(
    label: String, value: String, placeholder: String = "",
    modifier: Modifier = Modifier, onFocusSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }

    Surface(
        modifier = modifier.fillMaxWidth().focusRequester(fr)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                ) { onFocusSelect(); true } else false
            },
        onClick = onFocusSelect, shape = RoundedCornerShape(6.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        border = if (isFocused) ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp) else null,
        tonalElevation = if (isFocused) 4.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(2.dp))
            Text(value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontFamily = FontFamily.Monospace,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
        }
    }
}

// -- Form buttons --------------------------------------------------------------

@Composable
private fun FormButtons(
    isLoading: Boolean, errorMessage: String?, isValid: Boolean,
    onBack: () -> Unit, onSubmit: () -> Unit, cancelLabel: String = "Cancel"
) {
    var cancelFocused by remember { mutableStateOf(false) }
    var addFocused    by remember { mutableStateOf(false) }

    if (errorMessage != null) {
        Text(errorMessage, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, enabled = !isLoading,
            modifier = Modifier.weight(1f).onFocusChanged { cancelFocused = it.isFocused },
            border = ButtonDefaults.outlinedButtonBorder.copy(width = if (cancelFocused) 2.dp else 1.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (cancelFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor   = if (cancelFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        ) { Text(cancelLabel, fontSize = 13.sp) }

        OutlinedButton(onClick = onSubmit, enabled = !isLoading && isValid,
            modifier = Modifier.weight(1f).onFocusChanged { addFocused = it.isFocused },
            border = ButtonDefaults.outlinedButtonBorder.copy(width = if (addFocused) 2.dp else 1.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (addFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor   = if (addFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (addFocused) 8.dp else 2.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("Add", fontSize = 13.sp)
        }
    }
}

// -- Playlist import progress screen ------------------------------------------

@Composable
private fun PlaylistImportProgressScreen(
    viewModel: AddPlaylistViewModel,
    onDone: () -> Unit
) {
    val channelCount  by viewModel.channelImportedCount.collectAsState()
    val isLoadingEPG  by viewModel.isLoadingEPG.collectAsState()
    val epgCount      by viewModel.epgProgramCount.collectAsState()
    val isLoadingVOD by viewModel.isLoadingVOD.collectAsState()
    val vodCount     by viewModel.vodLoadedCount.collectAsState()
    val isLoadingSeries by viewModel.isLoadingSeries.collectAsState()
    val seriesCount  by viewModel.seriesLoadedCount.collectAsState()
    val isLoadingMusic by viewModel.isLoadingMusic.collectAsState()
    val musicCount   by viewModel.musicLoadedCount.collectAsState()
    val isDone       by viewModel.isBackgroundSyncComplete.collectAsState()
    val isXtream     = viewModel.importIsXtream
    val isJellyfin   = viewModel.importIsJellyfin
    val isPlex       = viewModel.importIsPlex

    // Auto-close: once import completes, navigate away after a short pause so the
    // user can see the "Import Complete" confirmation without needing to press Done.
    LaunchedEffect(isDone) {
        if (isDone) {
            kotlinx.coroutines.delay(5000L)
            onDone()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDone) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isDone) "Import Complete" else "Importing Playlist…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Progress rows
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isJellyfin && !isPlex) {
                        ImportProgressRow(
                            icon = Icons.Default.Tv,
                            label = "Live Channels",
                            isLoading = false,
                            isDone = channelCount > 0 || isDone,
                            count = channelCount,
                            unit = "channels"
                        )
                    }
                    if (isXtream) {
                        ImportProgressRow(
                            icon = Icons.Default.DateRange,
                            label = "EPG Guide",
                            isLoading = isLoadingEPG,
                            isDone = !isLoadingEPG && (isLoadingVOD || vodCount > 0 || isLoadingSeries || seriesCount > 0 || isDone),
                            count = epgCount,
                            unit = "programmes"
                        )
                    }
                    if (isXtream || isJellyfin || isPlex) {
                        ImportProgressRow(
                            icon = Icons.Default.Movie,
                            label = "Movies",
                            isLoading = isLoadingVOD,
                            isDone = !isLoadingVOD && (vodCount > 0 || isDone),
                            count = vodCount,
                            unit = "movies"
                        )
                        ImportProgressRow(
                            icon = Icons.Default.VideoLibrary,
                            label = "Series",
                            isLoading = isLoadingSeries,
                            isDone = !isLoadingSeries && (seriesCount > 0 || isDone),
                            count = seriesCount,
                            unit = "series"
                        )
                    }
                    if (isJellyfin || isPlex) {
                        ImportProgressRow(
                            icon = Icons.Default.MusicNote,
                            label = "Music",
                            isLoading = isLoadingMusic,
                            isDone = !isLoadingMusic && (musicCount > 0 || isDone),
                            count = musicCount,
                            unit = "tracks"
                        )
                    }
                }
            }

            // Done button — only appears when all background sync completes
            AnimatedVisibility(visible = isDone) {
                var doneFocused by remember { mutableStateOf(false) }
                val doneFR = remember { FocusRequester() }
                LaunchedEffect(isDone) {
                    if (isDone) {
                        kotlinx.coroutines.delay(100)
                        try { doneFR.requestFocus() } catch (_: Exception) {}
                    }
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .focusRequester(doneFR)
                        .onFocusChanged { doneFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)
                            ) { onDone(); true } else false
                        },
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = if (doneFocused) 2.dp else 1.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (doneFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor   = if (doneFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Done", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportProgressRow(
    icon: ImageVector,
    label: String,
    isLoading: Boolean,
    isDone: Boolean,
    count: Int,
    unit: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            val subText = when {
                isDone && count > 0    -> "${formatCount(count)} $unit"
                isDone                 -> "None found"
                isLoading && count > 0 -> "${formatCount(count)} $unit…"
                isLoading              -> "Importing…"
                else                   -> "Waiting…"
            }
            Text(subText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when {
            isDone   -> Icon(Icons.Default.CheckCircle, contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else     -> Icon(Icons.Default.HourglassEmpty, contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000 -> "${n / 1_000},${"%03d".format(n % 1_000)}"
    else       -> n.toString()
}