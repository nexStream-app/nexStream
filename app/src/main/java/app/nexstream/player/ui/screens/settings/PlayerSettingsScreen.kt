package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.getAutoFrameRateFlow
import app.nexstream.player.ui.theme.getSmartBufferFlow
import app.nexstream.player.ui.theme.saveAutoFrameRate
import app.nexstream.player.ui.theme.saveSmartBuffer
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val scope = rememberCoroutineScope()

    val autoFrameRate by context.getAutoFrameRateFlow().collectAsState(initial = true)
    val smartBuffer   by context.getSmartBufferFlow().collectAsState(initial = true)

    val frameRateFR = remember { FocusRequester() }

    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null) {
            kotlinx.coroutines.delay(100)
            try { frameRateFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart) {
            Text("Player Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sTheme.categoryText)
        }

        HorizontalDivider(color = nsTheme.sidebar.divider)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Frame Rate ────────────────────────────────────────────────────
            SettingsSection(title = "Frame Rate", icon = Icons.Default.Speed) {
                SettingsToggleRow(
                    label = "Auto Frame Rate Matching",
                    description = "Switches the display refresh rate to match content (24fps, 30fps, 60fps). Reduces judder on films. Disable if your screen flickers when playback starts.",
                    checked = autoFrameRate,
                    focusRequester = frameRateFR,
                    onToggle = { scope.launch { context.saveAutoFrameRate(!autoFrameRate) } }
                )
            }

            // ── Buffering ─────────────────────────────────────────────────────
            SettingsSection(title = "Buffering", icon = Icons.Default.Storage) {
                SettingsToggleRow(
                    label = "Smart Buffer",
                    description = "Increases buffer size on slow or unstable connections. Improves stability at the cost of a slightly longer start time.",
                    checked = smartBuffer,
                    onToggle = { scope.launch { context.saveSmartBuffer(!smartBuffer) } }
                )
            }

            // ── Info ──────────────────────────────────────────────────────────
            SettingsSection(title = "Formats", icon = Icons.Default.Info) {
                InfoRow("Supported",  "HLS, DASH, MP4, MKV, AVI, TS")
                InfoRow("Audio",      "AC3, EAC3, AAC, MP3, FLAC")
                InfoRow("Subtitles",  "SRT, VTT, ASS, embedded")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        Surface(shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onToggle(); true
                } else false
            }
            .clickable { onToggle() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = sTheme.categoryText)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = sTheme.categoryText.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = sTheme.categoryText.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = sTheme.categoryText)
    }
}