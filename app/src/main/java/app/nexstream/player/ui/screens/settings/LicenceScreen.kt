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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.license.AppAccessState
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle

@Composable
fun LicenceScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: LicenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val uiStyle = rememberUiStyle()
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Licence", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LicenceContent(
                uiState          = uiState,
                firstItemFocusRequester = firstItemFocusRequester,
                onActivate       = { viewModel.activate(it) },
                onDeactivate     = { viewModel.deactivate() },
                onCheckAssigned  = { viewModel.checkAssignedLicence() }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun LicenceContent(
    uiState: LicenceUiState,
    firstItemFocusRequester: FocusRequester? = null,
    onActivate: (String) -> Unit,
    onDeactivate: () -> Unit,
    onCheckAssigned: () -> Unit = {}
) {
    val uiStyle   = rememberUiStyle()
    var keyInput by remember { mutableStateOf("") }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    val keyFieldFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (!uiState.isActivated) {
            // ── Licence info ──────────────────────────────────────────────────
            val isExpired = uiState.accessState == AppAccessState.TRIAL_EXPIRED
            val expiryText = if (!uiState.trialExpiresAt.isNullOrBlank())
                formatExpiry(uiState.trialExpiresAt)
            else if (uiState.trialDaysLeft > 0)
                "${uiState.trialDaysLeft} day${if (uiState.trialDaysLeft == 1) "" else "s"} remaining"
            else "—"

            SettingsSectionContainer(title = "Licence", icon = Icons.Default.Lock, uiStyle = uiStyle) {
                SettingsInfoRow("Status", if (isExpired) "Trial Expired" else "Free Trial")
                SettingsInfoRow("Expires", expiryText)
                if (uiState.deviceModel.isNotEmpty()) SettingsInfoRow("Device", uiState.deviceModel)
                SettingsInfoRow("Device ID", uiState.currentDeviceId)
            }

            // ── Activate ──────────────────────────────────────────────────────
            SettingsSectionContainer(title = "Activate", icon = Icons.Default.VpnKey, uiStyle = uiStyle) {
                uiState.successMessage?.let {
                    SettingsInfoRow("", it, valueColor = MaterialTheme.colorScheme.primary)
                }
                uiState.errorMessage?.let {
                    SettingsInfoRow("", it, valueColor = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick  = onCheckAssigned,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    enabled  = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Check for Assigned Licence")
                }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { raw ->
                        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
                        keyInput = clean.chunked(6).take(4).joinToString("-")
                    },
                    label       = { Text("Licence Key") },
                    placeholder = { Text("XXXXXX-XXXXXX-XXXXXX-XXXXXX") },
                    modifier    = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(keyFieldFocus),
                    singleLine  = true,
                    isError     = uiState.errorMessage != null
                )
                Button(
                    onClick  = { onActivate(keyInput) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    enabled  = keyInput.length == 27 && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Activate")
                }
            }

        } else {
            // ── Active licence ────────────────────────────────────────────────
            val isLifetimeLicence = uiState.licenceType.isNullOrBlank() ||
                uiState.licenceType.equals("lifetime", ignoreCase = true)
            val typeDisplay = when {
                isLifetimeLicence -> "Lifetime"
                uiState.licenceType.equals("annual", ignoreCase = true) -> "Annual"
                uiState.licenceType.equals("trial", ignoreCase = true) -> "Free Trial"
                else -> uiState.licenceType?.replaceFirstChar { c -> c.uppercase() } ?: "Lifetime"
            }
            val expiryDisplay = when {
                !uiState.expiresAt.isNullOrBlank() -> formatExpiry(uiState.expiresAt)
                isLifetimeLicence -> "Never"
                else -> "—"
            }

            SettingsSectionContainer(title = "Licence", icon = Icons.Default.VerifiedUser, uiStyle = uiStyle) {
                SettingsInfoRow("Status", "Licensed", valueColor = MaterialTheme.colorScheme.primary)
                val sourceDisplay = if (uiState.resellerId != null) "Reseller assigned" else "Direct"
                SettingsInfoRow("Source", sourceDisplay)
                if (!uiState.isResellerAssigned) {
                    uiState.email?.takeIf { it.isNotEmpty() && !it.endsWith("@nexstream.app") }
                        ?.let { SettingsInfoRow("Account", it) }
                }
                SettingsInfoRow("Type", typeDisplay)
                SettingsInfoRow("Expires", expiryDisplay)
                uiState.licenceKey?.takeIf { it.isNotEmpty() }
                    ?.let { SettingsInfoRow("Key", it) }
                if (uiState.deviceModel.isNotEmpty()) SettingsInfoRow("Device", uiState.deviceModel)
                SettingsInfoRow("Device ID", uiState.currentDeviceId)
            }

            SettingsSectionContainer(title = "Actions", icon = Icons.Default.Settings, uiStyle = uiStyle) {
                var deactivateFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick  = { showDeactivateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .onFocusChanged { deactivateFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown &&
                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                            ) { showDeactivateDialog = true; true } else false
                        },
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (deactivateFocused) 2.dp else 1.dp,
                        color = if (deactivateFocused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (deactivateFocused) MaterialTheme.colorScheme.errorContainer
                                         else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor   = if (deactivateFocused) MaterialTheme.colorScheme.onErrorContainer
                                         else MaterialTheme.colorScheme.error
                    )
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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

