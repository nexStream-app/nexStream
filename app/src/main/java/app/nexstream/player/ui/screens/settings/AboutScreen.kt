package app.nexstream.player.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val profileSyncManager: ProfileSyncManager,
    private val profileManager: ProfileManager
) : ViewModel() {
    var isSyncing by mutableStateOf(false)
        private set

    fun syncNow() {
        viewModelScope.launch {
            isSyncing = true
            profileSyncManager.syncFromServer()
            profileManager.refreshAfterSync()
            isSyncing = false
        }
    }
}

@SuppressLint("HardwareIds")
@Composable
fun AboutScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val isSyncing = viewModel.isSyncing

    val androidId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "null"
    }

    val currentDeviceId = remember {
        val bytes = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        bytes.take(8).joinToString("") { "%02x".format(it) }.uppercase()
    }

    val androidIdKnownDefault = androidId == "9774d56d682e549c"

    var copied by remember { mutableStateOf(false) }

    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(firstFR)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scrollState.animateScrollTo(scrollState.value + 200) }; true }
                        Key.DirectionUp   -> { scope.launch { scrollState.animateScrollTo(scrollState.value - 200) }; true }
                        else -> false
                    }
                } else false
            }
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Device ID section
        InfoSection(title = "Device ID", icon = Icons.Default.Fingerprint) {
            InfoRow("ID", currentDeviceId)
            if (androidIdKnownDefault) {
                InfoRow("Warning", "ANDROID_ID is a known default - ID may not be unique!", warning = true)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Device ID", currentDeviceId))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy Device ID")
            }
        }

        // Manual sync section
        InfoSection(title = "Sync", icon = Icons.Default.Sync) {
            Text(
                "Sync your profiles and settings from the server. This happens automatically on startup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.syncNow() },
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isSyncing) "Syncing..." else "Sync Now")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, warning: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (warning) FontWeight.Bold else FontWeight.Normal,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}