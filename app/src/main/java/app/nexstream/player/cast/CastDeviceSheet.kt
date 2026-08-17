package app.nexstream.player.cast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceSheet(
    castManager: CastManager,
    currentUrl: String,
    title: String?,
    currentPositionMs: Long,
    onDismiss: () -> Unit
) {
    val devices    by castManager.devices.collectAsState()
    val castState  by castManager.castState.collectAsState()
    val isScanning by castManager.isScanning.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cast, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cast to Device",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            // ── Active connection banner ───────────────────────────────────
            if (castState is CastState.Active) {
                val device = (castState as CastState.Active).device
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(iconForType(device.type), null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(device.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        OutlinedButton(onClick = { castManager.disconnect() }) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            // ── Connecting indicator ──────────────────────────────────────
            if (castState is CastState.Connecting) {
                val device = (castState as CastState.Connecting).device
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Connecting to ${device.name}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Device list ───────────────────────────────────────────────
            val chromecasts  = devices.filter { it.type == CastType.CHROMECAST }
            val dlnaDevices  = devices.filter { it.type == CastType.DLNA }
            val airPlayDevs  = devices.filter { it.type == CastType.AIRPLAY }

            if (devices.isEmpty() && !isScanning && castState is CastState.Idle) {
                Text("No devices found on this network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp))
            }

            if (chromecasts.isNotEmpty()) {
                SectionHeader("Chromecast")
                chromecasts.forEach { device ->
                    DeviceRow(device, castState) {
                        castManager.connect(device, currentUrl, title, currentPositionMs)
                    }
                }
            }
            if (dlnaDevices.isNotEmpty()) {
                SectionHeader("DLNA / Smart TV")
                dlnaDevices.forEach { device ->
                    DeviceRow(device, castState) {
                        castManager.connect(device, currentUrl, title, currentPositionMs)
                    }
                }
            }
            if (airPlayDevs.isNotEmpty()) {
                SectionHeader("AirPlay")
                airPlayDevs.forEach { device ->
                    DeviceRow(device, castState) {
                        castManager.connect(device, currentUrl, title, currentPositionMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRow(
    device: CastDevice,
    castState: CastState,
    onConnect: () -> Unit
) {
    val activeDevice     = (castState as? CastState.Active)?.device
    val connectingDevice = (castState as? CastState.Connecting)?.device
    val isActive         = activeDevice?.id == device.id
    val isConnecting     = connectingDevice?.id == device.id

    Surface(
        onClick = { if (!isActive && !isConnecting) onConnect() },
        modifier = Modifier.fillMaxWidth(),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = iconForType(device.type),
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                       else LocalContentColor.current,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else LocalContentColor.current
            )
            when {
                isConnecting -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                isActive     -> Icon(Icons.Default.Check, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun iconForType(type: CastType) = when (type) {
    CastType.CHROMECAST -> Icons.Default.Cast
    CastType.DLNA       -> Icons.Default.Tv
    CastType.AIRPLAY    -> Icons.Default.Wifi
}
