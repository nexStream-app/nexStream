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
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import androidx.compose.foundation.focusable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun DownloadsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    selectedType: String? = null,
    profileId: String = "default",
    onBack: () -> Unit = {},
    onRequestSidebarFocus: () -> Unit = {},
    onPlayFile: (filePath: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val allDownloads by NexStreamDownloadManager.observeDownloads(context, profileId)
        .collectAsState(initial = emptyList())

    val downloads = remember(allDownloads, selectedType) {
        // Exclude items that are tagged as recordings in the old DownloadManager-based system
        val nonRecordings = allDownloads.filter { !NexStreamDownloadManager.isRecording(context, it.downloadId) }
        when (selectedType) {
            "Active"    -> nonRecordings.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
            "Completed" -> nonRecordings.filter { it.status == DownloadStatus.COMPLETED }
            "Failed"    -> nonRecordings.filter { it.status == DownloadStatus.FAILED }
            else        -> nonRecordings
        }
    }

    val active    = downloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.PAUSED }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val failed    = downloads.filter { it.status == DownloadStatus.FAILED }

    var deleteTarget       by remember { mutableStateOf<DownloadItem?>(null) }
    var downloadActionItem by remember { mutableStateOf<DownloadItem?>(null) }

    // Confirm delete dialog — used by both Classic and Modern
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Download") },
            text = { Text("Remove \"${item.title}\" from your downloads? The file will be deleted from your device.") },
            confirmButton = {
                Button(onClick = {
                    NexStreamDownloadManager.deleteDownload(context, item.downloadId, item.filePath)
                    deleteTarget = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Modern card-click action dialog (cinematic style)
    downloadActionItem?.let { item ->
        DownloadDetailsDialog(
            item      = item,
            onDismiss = { downloadActionItem = null },
            onPlay    = { onPlayFile(item.filePath, item.title); downloadActionItem = null },
            onCancel  = { NexStreamDownloadManager.cancelDownload(context, item.downloadId); downloadActionItem = null },
            onDelete  = { deleteTarget = item; downloadActionItem = null },
        )
    }

    val uiStyle = LocalUiStyle.current
    if (uiStyle == UiStyle.MODERN) {
        ModernDownloadsContent(
            downloads               = downloads,
            firstItemFocusRequester = firstItemFocusRequester,
            onCardClick             = { downloadActionItem = it },
            onRequestSidebarFocus   = onRequestSidebarFocus,
        )
    } else {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = selectedType ?: "Downloads",
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
    } // end else (Classic UI)
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
    val tint = MaterialTheme.colorScheme.onSurface
    val focusTint = MaterialTheme.colorScheme.primary
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
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED    -> MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED    -> MaterialTheme.colorScheme.onSurfaceVariant
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
    val primary   = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bgColor = when {
        isPrimary && isFocused    -> primary
        isPrimary                 -> primary.copy(alpha = 0.85f)
        isFocused                 -> MaterialTheme.colorScheme.surfaceVariant
        else                      -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        isPrimary   -> MaterialTheme.colorScheme.onPrimary
        isFocused   -> onSurface
        else        -> onSurface.copy(alpha = 0.7f)
    }
    val borderColor = when {
        isPrimary -> androidx.compose.ui.graphics.Color.Transparent
        else      -> MaterialTheme.colorScheme.outline.copy(alpha = if (isFocused) 1f else 0.5f)
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

@Composable
private fun DownloadDetailsDialog(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val context   = androidx.compose.ui.platform.LocalContext.current
    val isActive  = item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PENDING || item.status == DownloadStatus.PAUSED
    val canPlay   = item.status == DownloadStatus.COMPLETED && item.filePath.isNotBlank()
    val accent    = app.nexstream.player.ui.theme.LocalNsAccent.current

    val statusText = when (item.status) {
        DownloadStatus.RUNNING   -> "Downloading ${item.progressPercent}%"
        DownloadStatus.COMPLETED -> "Downloaded · ${formatBytes(item.totalBytes)}"
        DownloadStatus.FAILED    -> "Download failed"
        DownloadStatus.PAUSED    -> "Paused at ${item.progressPercent}%"
        DownloadStatus.PENDING   -> "Waiting to download…"
    }

    val buttons = buildList {
        add("close")
        if (canPlay)   add("play")
        if (isActive)  add("cancel")
        add("delete")
    }
    var selectedBtn by remember { mutableStateOf(if (canPlay) 1 else 0) }
    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); try { dialogFR.requestFocus() } catch (_: Exception) {} }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.75f),
            shape          = RoundedCornerShape(16.dp),
            color          = Color.Black,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selectedBtn = (selectedBtn - 1 + buttons.size) % buttons.size; true }
                            Key.DirectionRight -> { selectedBtn = (selectedBtn + 1) % buttons.size; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                when (buttons.getOrNull(selectedBtn)) {
                                    "close"  -> onDismiss()
                                    "play"   -> onPlay()
                                    "cancel" -> onCancel()
                                    "delete" -> onDelete()
                                }; true
                            }
                            Key.Back -> { onDismiss(); true }
                            else     -> false
                        }
                    }
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(context).data(item.posterUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                        alignment          = Alignment.Center,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.94f)))
                    )
                )
                // Close pill top-right
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    DlDialogPill(icon = Icons.Default.Close, label = "Close", isSelected = selectedBtn == 0, accent = accent, onClick = onDismiss)
                }
                // Bottom content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        buttons.forEachIndexed { idx, action ->
                            if (action == "close") return@forEachIndexed
                            val (icon, label, color) = when (action) {
                                "play"   -> Triple(Icons.Default.PlayArrow, "Play",   accent)
                                "cancel" -> Triple(Icons.Default.Close,    "Cancel", Color(0xFFFF7043))
                                "delete" -> Triple(Icons.Default.Delete,   "Delete", Color(0xFFD32F2F))
                                else     -> Triple(Icons.Default.Close,    "Close",  accent)
                            }
                            DlDialogPill(icon = icon, label = label, isSelected = selectedBtn == idx, accent = color, onClick = {
                                when (action) { "play" -> onPlay(); "cancel" -> onCancel(); "delete" -> onDelete() }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DlDialogPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) accent else Color.White.copy(alpha = 0.15f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(if (isSelected) 0.dp else 1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color.White)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
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