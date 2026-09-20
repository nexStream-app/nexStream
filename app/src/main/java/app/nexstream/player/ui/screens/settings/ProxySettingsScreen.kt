package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.nexstream.player.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProxySettingsScreen(
    firstItemFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()
    val scope   = rememberCoroutineScope()

    val proxyMode by context.getProxyModeFlow().collectAsState(initial = "OFF")
    val proxyHost by context.getProxyHostFlow().collectAsState(initial = "")
    val proxyPort by context.getProxyPortFlow().collectAsState(initial = 8080)
    val proxyType by context.getProxyTypeFlow().collectAsState(initial = "HTTP")
    val proxyUser by context.getProxyUsernameFlow().collectAsState(initial = "")
    val proxyPass by context.getProxyPasswordFlow().collectAsState(initial = "")

    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Edit dialog state
    var editField  by remember { mutableStateOf<String?>(null) }
    var editValue  by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Network", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Proxy Mode ────────────────────────────────────────────────────
            Box(modifier = Modifier.onFocusChanged { fs ->
                if (fs.hasFocus) scope.launch { scrollState.animateScrollTo(0) }
            }) {
            SettingsSectionContainer(title = "Stream Proxy", icon = Icons.Default.NetworkCheck, uiStyle = uiStyle) {
                Text(
                    "Routes stream traffic through a proxy server so your router sees only the proxy address, not the IPTV server.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ProxyModeRow(
                    label       = "Off",
                    description = "Connect directly to stream servers (default)",
                    selected    = proxyMode == "OFF",
                    focusRequester = firstFR,
                    onClick     = { scope.launch { context.saveProxyMode("OFF") } }
                )

                ProxyModeRow(
                    label       = "Built-in (proxy.nexstream.uk)",
                    description = "Route streams via the nexStream proxy — requires a valid licence. Your licence key is sent as authentication.",
                    selected    = proxyMode == "BUILTIN",
                    onClick     = { scope.launch { context.saveProxyMode("BUILTIN") } }
                )

                ProxyModeRow(
                    label       = "Custom",
                    description = "Use your own HTTP or SOCKS5 proxy server",
                    selected    = proxyMode == "CUSTOM",
                    onClick     = { scope.launch { context.saveProxyMode("CUSTOM") } }
                )
            }
            } // end Box (scroll-to-top on Stream Proxy focus)

            // ── Custom proxy fields (only when CUSTOM selected) ───────────────
            if (proxyMode == "CUSTOM") {
                SettingsSectionContainer(title = "Custom Proxy", icon = Icons.Default.Dns, uiStyle = uiStyle) {

                    // Protocol type
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Protocol", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProxyTypeChip(label = "HTTP",   selected = proxyType == "HTTP",   onClick = { scope.launch { context.saveProxyType("HTTP") } })
                            ProxyTypeChip(label = "SOCKS5", selected = proxyType == "SOCKS5", onClick = { scope.launch { context.saveProxyType("SOCKS5") } })
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionItem(
                        label       = "Host",
                        description = "Proxy server hostname or IP address",
                        value       = proxyHost.ifEmpty { "Not set" },
                        uiStyle     = uiStyle,
                        onClick     = { editField = "host"; editValue = proxyHost }
                    )
                    SettingsActionItem(
                        label       = "Port",
                        description = "Proxy server port number",
                        value       = proxyPort.toString(),
                        uiStyle     = uiStyle,
                        onClick     = { editField = "port"; editValue = proxyPort.toString() }
                    )
                    SettingsActionItem(
                        label       = "Username",
                        description = "Leave blank if no authentication required",
                        value       = proxyUser.ifEmpty { "None" },
                        uiStyle     = uiStyle,
                        onClick     = { editField = "username"; editValue = proxyUser }
                    )
                    SettingsActionItem(
                        label       = "Password",
                        description = "Leave blank if no authentication required",
                        value       = if (proxyPass.isEmpty()) "None" else "••••••••",
                        uiStyle     = uiStyle,
                        showDivider = false,
                        onClick     = { editField = "password"; editValue = proxyPass; showPass = false }
                    )
                }
            }

            // ── Info box ──────────────────────────────────────────────────────
            if (proxyMode == "BUILTIN") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Changes take effect when you start the next stream. Your licence key is used to authenticate with the proxy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (editField != null) {
        val title = when (editField) {
            "host"     -> "Proxy Host"
            "port"     -> "Proxy Port"
            "username" -> "Username"
            "password" -> "Password"
            else       -> ""
        }
        AlertDialog(
            onDismissRequest = { editField = null },
            title = { Text(title) },
            text = {
                if (editField == "password") {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (editField == "port") KeyboardType.Number else KeyboardType.Uri
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (editField) {
                            "host"     -> context.saveProxyHost(editValue.trim())
                            "port"     -> editValue.trim().toIntOrNull()?.coerceIn(1, 65535)?.let { context.saveProxyPort(it) }
                            "username" -> context.saveProxyUsername(editValue.trim())
                            "password" -> context.saveProxyPassword(editValue)
                        }
                    }
                    editField = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editField = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProxyModeRow(
    label: String,
    description: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProxyTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = if (selected) MaterialTheme.colorScheme.primary
                 else if (isFocused) MaterialTheme.colorScheme.primaryContainer
                 else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style    = MaterialTheme.typography.labelLarge,
            color    = if (selected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
