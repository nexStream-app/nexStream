package app.nexstream.player.ui.screens.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexstream.player.downloads.DownloadItem
import app.nexstream.player.downloads.DownloadStatus
import app.nexstream.player.downloads.NexStreamDownloadManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun DownloadsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    selectedType: String? = null,
    onBack: () -> Unit = {},
    onPlayFile: (filePath: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val allDownloads by NexStreamDownloadManager.observeDownloads(context)
        .collectAsState(initial = emptyList())

    val downloads = remember(allDownloads, selectedType) {
        when (selectedType) {
            "Active"    -> allDownloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
            "Completed" -> allDownloads.filter { it.status == DownloadStatus.COMPLETED }
            "Failed"    -> allDownloads.filter { it.status == DownloadStatus.FAILED }
            else        -> allDownloads
        }
    }

    val active    = downloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val failed    = downloads.filter { it.status == DownloadStatus.FAILED }

    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

    // Confirm delete dialog
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Download") },
            text = { Text("Remove \"${item.title}\" from your downloads? The file will be deleted from your device.") },
            confirmButton = {
                Button(onClick = {
                    NexStreamDownloadManager.deleteDownload(context, item.downloadId, item.filePath)
                    deleteTarget = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = selectedType ?: "All",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = nsTheme.sidebar.divider)

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Download, null,
                        modifier = Modifier.size(64.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f))
                    Text("No downloads yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Text("Long-press a movie to download it",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (active.isNotEmpty()) {
                    item {
                        SectionHeader("Downloading", active.size)
                    }
                    items(active, key = { it.downloadId }) { item ->
                        val isFirst = item == (active + completed + failed).firstOrNull()
                        DownloadCard(
                            item = item,
                            focusRequester = if (isFirst) firstItemFocusRequester else null,
                            onCancel = { NexStreamDownloadManager.cancelDownload(context, item.downloadId) },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); SectionHeader("Completed", completed.size) }
                    items(completed, key = { it.downloadId }) { item ->
                        DownloadCard(
                            item = item,
                            focusRequester = if (active.isEmpty() && item == completed.firstOrNull()) firstItemFocusRequester else null,
                            onCancel = null,
                            onDelete = { deleteTarget = item },
                            onPlay = if (item.filePath.isNotBlank()) {
                                { onPlayFile(item.filePath, item.title) }
                            } else null
                        )
                    }
                }

                if (failed.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)); SectionHeader("Failed", failed.size) }
                    items(failed, key = { it.downloadId }) { item ->
                        DownloadCard(
                            item = item,
                            focusRequester = null,
                            onCancel = { NexStreamDownloadManager.cancelDownload(context, item.downloadId) },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
            Text("$count", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun FocusableButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(6.dp)) else Modifier),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FocusableOutlineButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    label: String,
    isDestructive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val focusTint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, focusTint, RoundedCornerShape(6.dp)) else Modifier),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isFocused) focusTint else tint
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isFocused) focusTint else tint.copy(alpha = 0.5f)
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    focusRequester: FocusRequester?,
    onCancel: (() -> Unit)?,
    onDelete: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sTheme = LocalNexStreamTheme.current.sidebar
    val animProgress by animateFloatAsState(
        targetValue = item.progressPercent / 100f,
        animationSpec = tween(600),
        label = "progress"
    )

    val statusColor = when (item.status) {
        DownloadStatus.RUNNING   -> MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
        DownloadStatus.FAILED    -> MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED    -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.PENDING   -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when (item.status) {
        DownloadStatus.RUNNING   -> "${item.progressPercent}%"
        DownloadStatus.COMPLETED -> "Complete"
        DownloadStatus.FAILED    -> "Failed"
        DownloadStatus.PAUSED    -> "Paused"
        DownloadStatus.PENDING   -> "Waiting…"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Poster thumbnail
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.posterUrl)
                            .size(60, 90).crossfade(true).build(),
                        contentDescription = item.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .width(60.dp).height(90.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = sTheme.categoryText)
                    Text(item.fileName, style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                // Status badge
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusLabel, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // Progress bar (only for active/pending)
            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PENDING || item.status == DownloadStatus.PAUSED) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatBytes(item.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (item.totalBytes > 0) formatBytes(item.totalBytes) else "Unknown size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Completed: show file size
            if (item.status == DownloadStatus.COMPLETED && item.totalBytes > 0) {
                Text(formatBytes(item.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Action row — each button is individually focusable for D-pad
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.status == DownloadStatus.COMPLETED && onPlay != null) {
                    CardActionButton(
                        label = "Play",
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        onClick = onPlay
                    )
                }
                if (onCancel != null && item.status != DownloadStatus.COMPLETED) {
                    CardActionButton(label = "Cancel", onClick = onCancel)
                }
                CardActionButton(
                    label = "Delete",
                    isDestructive = true,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun CardActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val error   = MaterialTheme.colorScheme.error

    val bgColor = when {
        isPrimary && isFocused    -> primary
        isPrimary                 -> primary.copy(alpha = 0.85f)
        isDestructive && isFocused -> error
        isDestructive             -> androidx.compose.ui.graphics.Color.Transparent
        isFocused                 -> MaterialTheme.colorScheme.surfaceVariant
        else                      -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        isPrimary                  -> MaterialTheme.colorScheme.onPrimary
        isDestructive && isFocused -> MaterialTheme.colorScheme.onError
        isDestructive              -> error
        else                       -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        isPrimary    -> androidx.compose.ui.graphics.Color.Transparent
        isDestructive -> error.copy(alpha = if (isFocused) 1f else 0.6f)
        else          -> MaterialTheme.colorScheme.outline.copy(alpha = if (isFocused) 1f else 0.5f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "%.0f KB".format(bytes / 1024.0)
    }
}