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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LicenceScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: LicenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Licence", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        LicenceContent(
            uiState          = uiState,
            firstItemFocusRequester = firstItemFocusRequester,
            onActivate       = { viewModel.activate(it) },
            onDeactivate     = { viewModel.deactivate() },
            onRefreshDevices = { viewModel.loadDevices() },
            onCheckAssigned  = { viewModel.checkAssignedLicence() }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LicenceContent(
    uiState: LicenceUiState,
    firstItemFocusRequester: FocusRequester? = null,
    onActivate: (String) -> Unit,
    onDeactivate: () -> Unit,
    onRefreshDevices: () -> Unit,
    onCheckAssigned: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    val keyFieldFocus   = remember { FocusRequester() }
    val copyButtonFocus = remember { FocusRequester() }

    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null) {
            kotlinx.coroutines.delay(100)
            try {
                if (uiState.isActivated) copyButtonFocus.requestFocus()
                else keyFieldFocus.requestFocus()
            } catch (e: Exception) { }
        }
    }

    // Show success message if present
    uiState.successMessage?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (!uiState.isActivated) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text("Not activated", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Device ID + assigned licence check
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Your Device ID", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(uiState.currentDeviceId,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(uiState.currentDeviceId)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy device ID",
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider()
                        Text(
                            "You can assign this device to one of your licence keys at nexstream.uk/my-keys, " +
                                    "or tap below to check if one has already been assigned.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://nexstream.uk/my-keys")
                                )
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("nexstream.uk/my-keys",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick  = onCheckAssigned,
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.CloudDownload, null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Check for Assigned Licence")
                        }
                        uiState.errorMessage?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                HorizontalDivider()

                Text("Or enter your licence key manually:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Fixed key input -- strip dashes before processing to avoid offset bug
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { raw ->
                        // Strip all non-alphanumeric, uppercase, rechunk into groups of 6
                        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
                        keyInput = clean.chunked(6).take(4).joinToString("-")
                    },
                    label       = { Text("Licence Key") },
                    placeholder = { Text("XXXXXX-XXXXXX-XXXXXX-XXXXXX") },
                    modifier    = Modifier.fillMaxWidth().focusRequester(keyFieldFocus),
                    singleLine  = true,
                    isError     = uiState.errorMessage != null,
                    supportingText = uiState.errorMessage?.let { { Text(it) } }
                )

                Button(
                    onClick  = { onActivate(keyInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = keyInput.length == 27 && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Activate")
                }

            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Active", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider()

                uiState.email?.let { LicenceInfoRow(label = "Account", value = it) }
                uiState.licenceType?.let {
                    LicenceInfoRow(label = "Type", value = it.replaceFirstChar { c -> c.uppercase() })
                }
                uiState.expiresAt?.let { LicenceInfoRow(label = "Expires", value = formatExpiry(it)) }
                    ?: LicenceInfoRow(label = "Expires", value = "Never (Lifetime)")

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("This Device", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uiState.currentDeviceId,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace))
                    }
                    IconButton(
                        onClick  = { clipboard.setText(AnnotatedString(uiState.currentDeviceId)) },
                        modifier = Modifier.focusRequester(copyButtonFocus)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy device ID",
                            modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Registered Devices (${uiState.devices.size}/${uiState.deviceLimit})",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onRefreshDevices, enabled = !uiState.isLoadingDevices) {
                        if (uiState.isLoadingDevices)
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                                modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.devices.isEmpty() && !uiState.isLoadingDevices) {
                    Text("No devices found", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    uiState.devices.forEach { device ->
                        DeviceRow(device = device,
                            isCurrentDevice = device.device_id == uiState.currentDeviceId)
                    }
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick  = { showDeactivateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deactivate on This Device")
                }
            }
        }
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title   = { Text("Deactivate Licence") },
            text    = { Text("This will remove the licence from this device. You can reactivate at any time with your licence key.") },
            confirmButton = {
                TextButton(onClick = { onDeactivate(); showDeactivateDialog = false }) {
                    Text("Deactivate", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LicenceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeviceRow(device: app.nexstream.player.license.DeviceInfo, isCurrentDevice: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = if (isCurrentDevice) Icons.Default.PhoneAndroid else Icons.Default.Devices,
            contentDescription = null,
            tint = if (isCurrentDevice) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.device_name + if (isCurrentDevice) " (this device)" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrentDevice) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(text = "Last seen: ${formatLastSeen(device.last_seen)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatExpiry(expiresAt: String?): String {
    if (expiresAt.isNullOrEmpty()) return "Never (Lifetime)"
    return try {
        val sdf  = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
        val date = sdf.parse(expiresAt)
        val out  = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.UK)
        date?.let { out.format(it) } ?: expiresAt
    } catch (_: Exception) { expiresAt }
}

private fun formatLastSeen(lastSeen: String): String {
    return try {
        val sdf      = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
        val date     = sdf.parse(lastSeen) ?: return lastSeen
        val diffMins = (java.util.Date().time - date.time) / 60000
        when {
            diffMins < 2    -> "Just now"
            diffMins < 60   -> "$diffMins mins ago"
            diffMins < 1440 -> "${diffMins / 60}h ago"
            else            -> "${diffMins / 1440}d ago"
        }
    } catch (_: Exception) { lastSeen }
}