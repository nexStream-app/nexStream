package app.nexstream.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class ReminderOverlayData(
    val reminderId: String,
    val channelName: String,
    val programTitle: String,
    val streamUrl: String,
    val startTime: Long
)

@Composable
fun ReminderOverlay(
    data: ReminderOverlayData,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onWatchNow: (streamUrl: String, channelName: String) -> Unit
) {
    val autoSwitchSeconds = 20
    var secondsLeft by remember { mutableStateOf(autoSwitchSeconds) }
    var minutesUntilStart by remember {
        mutableLongStateOf(((data.startTime - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L))
    }
    val watchNowFocus = remember { FocusRequester() }

    // Countdown timer — updates both auto-switch seconds and live "starting in X min" label
    LaunchedEffect(data.reminderId) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
            minutesUntilStart = ((data.startTime - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        }
        // Auto-switch when countdown reaches zero
        onWatchNow(data.streamUrl, data.channelName)
    }

    // Auto-focus Watch Now on appearance
    LaunchedEffect(Unit) {
        delay(100)
        try { watchNowFocus.requestFocus() } catch (_: Exception) {}
    }

    val progress = secondsLeft.toFloat() / autoSwitchSeconds.toFloat()

    // Semi-transparent full-screen scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .width(420.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "REMINDER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (minutesUntilStart > 0) {
                        Text(
                            text = "Starting in $minutesUntilStart min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Starting now",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Programme info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = data.programTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = data.channelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Countdown progress bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "Auto-switching in ${secondsLeft}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Snooze 1m", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onWatchNow(data.streamUrl, data.channelName) },
                        modifier = Modifier.weight(1f).focusRequester(watchNowFocus),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Watch Now", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}