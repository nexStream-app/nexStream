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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
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

    var isLoading    by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set

    private val _pollState = MutableStateFlow<MacPollState>(MacPollState.Idle)
    val pollState: StateFlow<MacPollState> = _pollState

    private var pollJob: Job? = null

    fun getDeviceId(): String = licenceManager.getDeviceId()

    fun startPolling(deviceId: String, onSuccess: () -> Unit) {
        pollJob?.cancel()
        _pollState.update { MacPollState.Polling }
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val json = JSONObject(
                        URL("https://nexstream.uk/api/playlist.php?device_id=$deviceId").readText()
                    )

                    // Check for assigned licence and auto-activate
                    val licenceObj = json.optJSONObject("licence")
                    if (licenceObj != null && !licenceManager.isActivated()) {
                        val licenceKey = licenceObj.optString("key")
                        if (licenceKey.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                _pollState.update { MacPollState.LicenceActivating }
                            }
                            val result = licenceManager.activate(licenceKey)
                            withContext(Dispatchers.Main) {
                                if (result is app.nexstream.player.license.LicenceResult.Success) {
                                    _pollState.update { MacPollState.LicenceActivated }
                                }
                            }
                        }
                    }

                    // Check for playlist
                    if (json.optBoolean("found", false)) {
                        withContext(Dispatchers.Main) { _pollState.update { MacPollState.Found } }
                        val result = when (json.optString("type")) {
                            "xtream" -> repository.addXtreamPlaylist(
                                username = json.getString("username"),
                                host     = json.getString("server_url"),
                                password = json.getString("password")
                            )
                            "m3u" -> repository.addM3UPlaylist(
                                name = "My Playlist",
                                url  = json.getString("m3u_url")
                            )
                            else -> Result.failure(Exception("Unknown type"))
                        }
                        result
                            .onSuccess { withContext(Dispatchers.Main) { onSuccess() } }
                            .onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    _pollState.update { MacPollState.Error(e.message ?: "Import failed") }
                                }
                            }
                        break
                    }
                } catch (_: Exception) {}
                delay(5_000L)
            }
        }
    }

    fun stopPolling() { pollJob?.cancel(); _pollState.update { MacPollState.Idle } }

    suspend fun hasPlaylists(): Boolean = repository.getAllPlaylists().first().isNotEmpty()

    fun addM3UPlaylist(name: String, url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.addM3UPlaylist(name, url)
                .onSuccess { isLoading = false; onSuccess() }
                .onFailure { isLoading = false; errorMessage = it.message ?: "Failed" }
        }
    }

    fun addXtreamPlaylist(username: String, host: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            repository.addXtreamPlaylist(username = username, host = host, password = password)
                .onSuccess { isLoading = false; onSuccess() }
                .onFailure { isLoading = false; errorMessage = it.message ?: "Failed" }
        }
    }

    override fun onCleared() { super.onCleared(); pollJob?.cancel() }
}

// -- Screen --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    onBack: () -> Unit,
    viewModel: AddPlaylistViewModel = hiltViewModel()
) {
    val context   = LocalContext.current
    val pollState by viewModel.pollState.collectAsState()
    val isTv      = remember { isTvDevice(context) }
    val deviceId  = remember { viewModel.getDeviceId() }

    var keyboardTarget by remember { mutableStateOf<String?>(null) }
    var keyboardValue  by remember { mutableStateOf("") }
    var showKeyboard   by remember { mutableStateOf(false) }

    var showQrDialog by remember { mutableStateOf(false) }

    var host       by remember { mutableStateOf("") }
    var username   by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var m3uName    by remember { mutableStateOf("") }
    var m3uUrl     by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    // Auto-start polling on launch
    LaunchedEffect(deviceId) {
        if (!viewModel.hasPlaylists()) {
            viewModel.startPolling(deviceId) { onBack() }
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    fun openKeyboard(fieldName: String, current: String) {
        keyboardTarget = fieldName; keyboardValue = current; showKeyboard = true
    }

    fun commitKeyboard() {
        when (keyboardTarget) {
            "host"     -> host     = keyboardValue
            "username" -> username = keyboardValue
            "password" -> password = keyboardValue
            "m3uName"  -> m3uName  = keyboardValue
            "m3uUrl"   -> m3uUrl   = keyboardValue
        }
        showKeyboard = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // -- Device ID / status bar --------------------------------------------
        DeviceBar(deviceId = deviceId, pollState = pollState)

        // -- Tabs + forms ------------------------------------------------------
        TabRow(selectedTabIndex = selectedTab) {
            listOf("Xtream Codes", "M3U URL").forEachIndexed { i, title ->
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
                    onBack = onBack,
                    onSubmit = { viewModel.addXtreamPlaylist(username, host, password, onSuccess = onBack) }
                )
            } else {
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
                    onBack = onBack,
                    onSubmit = { viewModel.addM3UPlaylist(m3uName, m3uUrl, onSuccess = onBack) }
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
    onBack: () -> Unit, onSubmit: () -> Unit
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
        ) { Text("Cancel", fontSize = 13.sp) }

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