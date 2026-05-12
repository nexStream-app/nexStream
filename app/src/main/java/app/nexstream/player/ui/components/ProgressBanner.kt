package app.nexstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProgressBanner(
    message: String,
    progress: Float? = null, // null = indeterminate
    current: Int? = null,
    total: Int? = null,
    isComplete: Boolean = false
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isComplete) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isComplete) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )

                    if (current != null && total != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$current / $total channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isComplete) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            }
                        )
                    }

                    if (progress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}