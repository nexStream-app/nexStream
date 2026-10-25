package app.nexstream.player.ui.screens.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.theme.GlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAddPlaylist(
    onBack: () -> Unit,
    isFirstRun: Boolean = false,
    viewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pollState by viewModel.pollState.collectAsState()
    val deviceId = remember { viewModel.getDeviceId() }
    val isTv = remember { isTvDevice(context) }
    val clipboardManager = LocalClipboardManager.current

    var navigatingAway by remember { mutableStateOf(false) }
    val onCancel: () -> Unit = if (isFirstRun) {
        { (context as? android.app.Activity)?.finish() }
    } else {
        {
            navigatingAway = true
            viewModel.cancelPolling()
            onBack()
        }
    }

    androidx.activity.compose.BackHandler(enabled = !isFirstRun) { onCancel() }

    if (viewModel.importStarted && !navigatingAway) {
        GlassImportProgress(viewModel = viewModel, onDone = onBack)
        return
    }

    val addUrl = "nexstream.uk/add"
    val qrBitmap = remember(deviceId) { generateQrBitmap("https://nexstream.uk/add?device=$deviceId", 512) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Xtream Codes", "M3U URL", "Jellyfin", "Plex", "Device")

    // Tab FocusRequesters
    val tabFRs = remember { tabs.map { FocusRequester() } }
    val firstFieldFR = remember { FocusRequester() }

    // Xtream fields
    var host     by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // M3U fields
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl  by remember { mutableStateOf("") }

    // Jellyfin fields
    var jfHost     by remember { mutableStateOf("") }
    var jfUsername by remember { mutableStateOf("") }
    var jfPassword by remember { mutableStateOf("") }

    // TvKeyboard state
    var keyboardTarget by remember { mutableStateOf<String?>(null) }
    var keyboardValue  by remember { mutableStateOf("") }
    var showKeyboard   by remember { mutableStateOf(false) }

    fun openKeyboard(field: String, current: String) {
        keyboardTarget = field; keyboardValue = current; showKeyboard = true
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.Background),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ── Left info panel ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = GlassTokens.RadiusPanel, bottomEnd = GlassTokens.RadiusPanel))
                    .background(GlassTokens.GlassPanelSolid)
                    .border(
                        width = 1.dp,
                        color = GlassTokens.GlassBorder,
                        shape = RoundedCornerShape(topEnd = GlassTokens.RadiusPanel, bottomEnd = GlassTokens.RadiusPanel),
                    )
                    .padding(GlassTokens.PanelPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Add Playlist",
                    color = GlassTokens.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Poll state badge
                val stateText = when (pollState) {
                    is MacPollState.Polling        -> "Listening…"
                    is MacPollState.Found          -> "Playlist found!"
                    is MacPollState.LicenceActivating -> "Activating…"
                    is MacPollState.LicenceActivated  -> "Activated!"
                    is MacPollState.Error          -> "Error"
                    else                           -> ""
                }
                if (stateText.isNotEmpty()) {
                    val badgeColor = when (pollState) {
                        is MacPollState.Found, is MacPollState.LicenceActivated -> GlassTokens.AccentGreen
                        is MacPollState.Error -> GlassTokens.AccentRed
                        else -> GlassTokens.AccentBlue
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(GlassTokens.RadiusPill))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(text = stateText, color = badgeColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                // QR code
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(8.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Text(
                    text = addUrl,
                    color = GlassTokens.AccentDefault,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Scan to add a playlist from another device",
                    color = GlassTokens.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                HorizontalDivider(color = GlassTokens.GlassBorder)

                // Device ID (copyable)
                var idFocused by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .onFocusChanged { idFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                clipboardManager.setText(AnnotatedString(deviceId)); true
                            } else false
                        }
                        .focusable()
                        .clickable { clipboardManager.setText(AnnotatedString(deviceId)) }
                        .background(if (idFocused) GlassTokens.GlassSmallSolid else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, if (idFocused) GlassTokens.AccentDefault else GlassTokens.GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(text = "Device ID", color = GlassTokens.TextTertiary, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = deviceId,
                        color = GlassTokens.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Right form panel ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Tab strip
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, title ->
                        var tabFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(tabFRs[i])
                                .onFocusChanged { tabFocused = it.isFocused }
                                .onKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (e.key) {
                                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { selectedTab = i; true }
                                        Key.DirectionRight -> { try { tabFRs[(i + 1).coerceAtMost(tabs.lastIndex)].requestFocus() } catch (_: Exception) {}; true }
                                        Key.DirectionLeft  -> { try { tabFRs[(i - 1).coerceAtLeast(0)].requestFocus() } catch (_: Exception) {}; true }
                                        Key.DirectionDown  -> { try { firstFieldFR.requestFocus() } catch (_: Exception) {}; true }
                                        else -> false
                                    }
                                }
                                .focusable()
                                .clickable { selectedTab = i }
                                .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                                .background(
                                    if (i == selectedTab) GlassTokens.AccentDefault
                                    else if (tabFocused) GlassTokens.GlassSmallSolid
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (i == selectedTab) GlassTokens.AccentDefault
                                    else if (tabFocused) GlassTokens.AccentDefault.copy(alpha = 0.5f)
                                    else GlassTokens.GlassBorder,
                                    RoundedCornerShape(GlassTokens.RadiusPill),
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = if (i == selectedTab) GlassTokens.AccentOnDark
                                        else if (tabFocused) GlassTokens.TextPrimary
                                        else GlassTokens.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (i == selectedTab) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Form content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Xtream Codes
                            GlassFormField(
                                label = "Server URL",
                                value = host,
                                placeholder = "http://example.com:8080",
                                focusRequester = firstFieldFR,
                                onValueChange = { host = it },
                                onFocusSelect = { openKeyboard("host", host) },
                                isTv = isTv,
                            )
                            GlassFormField(
                                label = "Username",
                                value = username,
                                onValueChange = { username = it },
                                onFocusSelect = { openKeyboard("username", username) },
                                isTv = isTv,
                            )
                            GlassFormField(
                                label = "Password",
                                value = password,
                                isPassword = true,
                                onValueChange = { password = it },
                                onFocusSelect = { openKeyboard("password", password) },
                                isTv = isTv,
                            )
                            GlassFormActions(
                                isLoading = viewModel.isLoading,
                                errorMessage = viewModel.errorMessage,
                                isValid = host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                                onCancel = onCancel,
                                onSubmit = { viewModel.addXtreamPlaylist(username, host, password) },
                            )
                        }
                        1 -> {
                            // M3U
                            GlassFormField(
                                label = "Playlist Name",
                                value = m3uName,
                                focusRequester = firstFieldFR,
                                onValueChange = { m3uName = it },
                                onFocusSelect = { openKeyboard("m3uName", m3uName) },
                                isTv = isTv,
                            )
                            GlassFormField(
                                label = "M3U URL",
                                value = m3uUrl,
                                placeholder = "http://example.com/playlist.m3u",
                                onValueChange = { m3uUrl = it },
                                onFocusSelect = { openKeyboard("m3uUrl", m3uUrl) },
                                isTv = isTv,
                            )
                            GlassFormActions(
                                isLoading = viewModel.isLoading,
                                errorMessage = viewModel.errorMessage,
                                isValid = m3uName.isNotBlank() && m3uUrl.isNotBlank(),
                                onCancel = onCancel,
                                onSubmit = { viewModel.addM3UPlaylist(m3uName, m3uUrl) },
                            )
                        }
                        2 -> {
                            // Jellyfin
                            GlassFormField(
                                label = "Server URL",
                                value = jfHost,
                                placeholder = "http://jellyfin.local:8096",
                                focusRequester = firstFieldFR,
                                onValueChange = { jfHost = it },
                                onFocusSelect = { openKeyboard("jfHost", jfHost) },
                                isTv = isTv,
                            )
                            GlassFormField(
                                label = "Username",
                                value = jfUsername,
                                onValueChange = { jfUsername = it },
                                onFocusSelect = { openKeyboard("jfUsername", jfUsername) },
                                isTv = isTv,
                            )
                            GlassFormField(
                                label = "Password",
                                value = jfPassword,
                                isPassword = true,
                                onValueChange = { jfPassword = it },
                                onFocusSelect = { openKeyboard("jfPassword", jfPassword) },
                                isTv = isTv,
                            )
                            GlassFormActions(
                                isLoading = viewModel.isLoading,
                                errorMessage = viewModel.errorMessage,
                                isValid = jfHost.isNotBlank() && jfUsername.isNotBlank(),
                                onCancel = onCancel,
                                onSubmit = { viewModel.addJellyfinPlaylist(jfHost, jfUsername, jfPassword) },
                            )
                        }
                        3 -> {
                            // Plex — delegate to existing Plex flow via a thin wrapper
                            GlassPlexTab(
                                viewModel = viewModel,
                                deviceId = deviceId,
                                focusRequester = firstFieldFR,
                                onCancel = onCancel,
                            )
                        }
                        4 -> {
                            // Device tab
                            Box(
                                modifier = Modifier
                                    .focusRequester(firstFieldFR)
                                    .focusable(),
                            )
                            Text(
                                text = "Scan the QR code on the left or visit nexstream.uk on another device to push a playlist here automatically.",
                                color = GlassTokens.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            GlassFormActions(
                                isLoading = false,
                                errorMessage = null,
                                isValid = false,
                                onCancel = onCancel,
                                onSubmit = {},
                                submitLabel = null,
                            )
                        }
                    }
                }
            }
        }
    }

    // TV Keyboard
    if (showKeyboard && isTv) {
        ModalBottomSheet(
            onDismissRequest = { showKeyboard = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            containerColor = GlassTokens.GlassPanelSolid,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassTokens.GlassSmallSolid)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        keyboardTarget?.replaceFirstChar { it.uppercase() } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.TextTertiary,
                    )
                    Text(
                        text = if (keyboardValue.isEmpty()) "…" else keyboardValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (keyboardValue.isEmpty()) GlassTokens.TextTertiary else GlassTokens.TextPrimary,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                app.nexstream.player.ui.components.TvKeyboard(
                    value = keyboardValue,
                    onValueChange = { keyboardValue = it },
                    onDone = { commitKeyboard() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Form helpers ────────────────────────────────────────────────────────────────

@Composable
private fun GlassFormField(
    label: String,
    value: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null,
    onValueChange: (String) -> Unit,
    onFocusSelect: () -> Unit,
    isTv: Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }
    var showPass  by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = GlassTokens.TextSecondary, style = MaterialTheme.typography.labelMedium)
        if (isTv) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GlassTokens.ButtonHeight)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                            onFocusSelect(); true
                        } else false
                    }
                    .focusable()
                    .clickable { onFocusSelect() }
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassTokens.GlassSmallSolid)
                    .border(
                        1.dp,
                        if (isFocused) GlassTokens.AccentDefault else GlassTokens.GlassBorder,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (value.isEmpty()) placeholder else if (isPassword && !showPass) "•".repeat(value.length) else value,
                    color = if (value.isEmpty()) GlassTokens.TextTertiary else GlassTokens.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (isPassword && !showPass && value.isNotEmpty()) FontFamily.Monospace else FontFamily.Default,
                )
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = GlassTokens.TextTertiary) },
                visualTransformation = if (isPassword && !showPass) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = if (isPassword) ({
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = GlassTokens.TextSecondary,
                        )
                    }
                }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlassTokens.AccentDefault,
                    unfocusedBorderColor = GlassTokens.GlassBorder,
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextSecondary,
                    cursorColor = GlassTokens.AccentDefault,
                    unfocusedContainerColor = GlassTokens.GlassSmallSolid,
                    focusedContainerColor = GlassTokens.GlassSmallSolid,
                ),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun GlassFormActions(
    isLoading: Boolean,
    errorMessage: String?,
    isValid: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String? = "Add Playlist",
) {
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            color = GlassTokens.AccentRed,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        var cancelFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .height(GlassTokens.ButtonHeight)
                .defaultMinSize(minWidth = 100.dp)
                .onFocusChanged { cancelFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                        onCancel(); true
                    } else false
                }
                .focusable()
                .clickable { onCancel() }
                .clip(RoundedCornerShape(10.dp))
                .background(if (cancelFocused) GlassTokens.GlassSmallSolid else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Cancel", color = GlassTokens.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (submitLabel != null) {
            var submitFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .height(GlassTokens.ButtonHeight)
                    .defaultMinSize(minWidth = 140.dp)
                    .onFocusChanged { submitFocused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                            if (isValid && !isLoading) onSubmit(); true
                        } else false
                    }
                    .focusable()
                    .clickable { if (isValid && !isLoading) onSubmit() }
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (!isValid || isLoading) GlassTokens.GlassSmallSolid
                        else if (submitFocused) GlassTokens.AccentDefault
                        else GlassTokens.AccentDefault.copy(alpha = 0.85f)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = GlassTokens.AccentOnDark,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = submitLabel,
                        color = if (!isValid) GlassTokens.TextTertiary else GlassTokens.AccentOnDark,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPlexTab(
    viewModel: AddPlaylistViewModel,
    deviceId: String,
    focusRequester: FocusRequester,
    onCancel: () -> Unit,
) {
    val pinState  = viewModel.plexPinState
    val pinCode   = viewModel.plexPinCode
    val servers   = viewModel.plexServers
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = Modifier.focusRequester(focusRequester).focusable())

    when (pinState) {
        AddPlaylistViewModel.PlexPinState.IDLE -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Sign in to Plex on plex.tv and your servers will appear here automatically.",
                    color = GlassTokens.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                GlassFormActions(
                    isLoading = false,
                    errorMessage = null,
                    isValid = true,
                    onCancel = onCancel,
                    onSubmit = { viewModel.requestPlexPin(deviceId) },
                    submitLabel = "Connect to Plex",
                )
            }
        }
        AddPlaylistViewModel.PlexPinState.REQUESTING -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassTokens.AccentDefault)
            }
        }
        AddPlaylistViewModel.PlexPinState.WAITING -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pinCode != null) {
                    Text(
                        text = "Enter this code at plex.tv/link:",
                        color = GlassTokens.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    var codeFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { codeFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                    clipboardManager.setText(AnnotatedString(pinCode)); true
                                } else false
                            }
                            .focusable()
                            .clickable { clipboardManager.setText(AnnotatedString(pinCode)) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassTokens.GlassSmallSolid)
                            .border(1.dp, if (codeFocused) GlassTokens.AccentDefault else GlassTokens.GlassBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = pinCode,
                            color = GlassTokens.AccentDefault,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                GlassFormActions(isLoading = true, errorMessage = null, isValid = false, onCancel = onCancel, onSubmit = {})
            }
        }
        AddPlaylistViewModel.PlexPinState.FETCHING_SERVERS -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassTokens.AccentDefault)
            }
        }
        AddPlaylistViewModel.PlexPinState.READY -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a server:", color = GlassTokens.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                servers.forEachIndexed { i, server ->
                    var serverFocused by remember { mutableStateOf(false) }
                    val isSelected = server.clientIdentifier == viewModel.plexSelectedServer?.clientIdentifier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .onFocusChanged { serverFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                    viewModel.plexSelectedServer = server; true
                                } else false
                            }
                            .focusable()
                            .clickable { viewModel.plexSelectedServer = server }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected || serverFocused) GlassTokens.GlassSmallSolid else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (isSelected) GlassTokens.AccentDefault else if (serverFocused) GlassTokens.AccentDefault.copy(alpha = 0.5f) else GlassTokens.GlassBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = server.name,
                            color = if (isSelected) GlassTokens.AccentDefault else GlassTokens.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                GlassFormActions(
                    isLoading = viewModel.isLoading,
                    errorMessage = viewModel.errorMessage,
                    isValid = viewModel.plexSelectedServer != null,
                    onCancel = onCancel,
                    onSubmit = { viewModel.addPlexServer() },
                    submitLabel = "Add Server",
                )
            }
        }
        AddPlaylistViewModel.PlexPinState.ERROR -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Authentication failed. Please try again.", color = GlassTokens.AccentRed, style = MaterialTheme.typography.bodyMedium)
                GlassFormActions(isLoading = false, errorMessage = null, isValid = true, onCancel = onCancel, onSubmit = { viewModel.requestPlexPin(deviceId) }, submitLabel = "Retry")
            }
        }
        AddPlaylistViewModel.PlexPinState.EXPIRED -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PIN expired. Please try again.", color = GlassTokens.AccentRed, style = MaterialTheme.typography.bodyMedium)
                GlassFormActions(isLoading = false, errorMessage = null, isValid = true, onCancel = onCancel, onSubmit = { viewModel.requestPlexPin(deviceId) }, submitLabel = "Retry")
            }
        }
    }
}

// ── Import progress ─────────────────────────────────────────────────────────────

@Composable
private fun GlassImportProgress(
    viewModel: AddPlaylistViewModel,
    onDone: () -> Unit,
) {
    val channelCount by viewModel.channelImportedCount.collectAsState()
    val isLoadingEPG by viewModel.isLoadingEPG.collectAsState()
    val epgCount     by viewModel.epgProgramCount.collectAsState()
    val isLoadingVOD by viewModel.isLoadingVOD.collectAsState()
    val vodCount     by viewModel.vodLoadedCount.collectAsState()
    val isComplete   by viewModel.isBackgroundSyncComplete.collectAsState()

    LaunchedEffect(isComplete) {
        if (isComplete) {
            kotlinx.coroutines.delay(1200)
            onDone()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(GlassTokens.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(GlassTokens.RadiusPanel))
                .background(GlassTokens.GlassPanelSolid)
                .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusPanel))
                .padding(GlassTokens.PanelPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = GlassTokens.AccentDefault, modifier = Modifier.size(40.dp))
            Text(text = "Importing Playlist", color = GlassTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (channelCount > 0) GlassProgressRow("Channels", "$channelCount loaded")
                if (isLoadingEPG)     GlassProgressRow("EPG", "$epgCount programmes")
                if (isLoadingVOD)     GlassProgressRow("Movies", "$vodCount loaded")
            }
            if (isComplete) {
                Text(text = "Done!", color = GlassTokens.AccentGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GlassProgressRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = GlassTokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = GlassTokens.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
