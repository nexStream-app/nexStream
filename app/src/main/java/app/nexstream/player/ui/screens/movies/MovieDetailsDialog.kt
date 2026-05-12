package app.nexstream.player.ui.screens.movies

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.MovieEntity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ── Storage helpers ───────────────────────────────────────────────────────────

private fun getAvailableStorageBytes(): Long {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return stat.availableBlocksLong * stat.blockSizeLong
}

private suspend fun getMovieSizeBytes(streamUrl: String): Long = withContext(Dispatchers.IO) {
    try {
        // Try HEAD first with VLC user agent
        val headConn = URL(streamUrl).openConnection() as HttpURLConnection
        headConn.requestMethod = "HEAD"
        headConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        headConn.connectTimeout = 8_000
        headConn.readTimeout = 8_000
        headConn.instanceFollowRedirects = true
        headConn.connect()
        val headSize = headConn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        headConn.disconnect()
        if (headSize > 0) return@withContext headSize

        // Fall back to GET Range: bytes=0-0 — server replies with Content-Range: bytes 0-0/TOTAL
        val getConn = URL(streamUrl).openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        getConn.setRequestProperty("Range", "bytes=0-0")
        getConn.connectTimeout = 8_000
        getConn.readTimeout = 8_000
        getConn.instanceFollowRedirects = true
        getConn.connect()
        val contentRange = getConn.getHeaderField("Content-Range")
        getConn.disconnect()
        if (contentRange != null) {
            val total = contentRange.substringAfterLast("/").trim().toLongOrNull()
            if (total != null && total > 0) return@withContext total
        }
        -1L
    } catch (_: Exception) { -1L }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "%.0f KB".format(bytes / 1024.0)
    }
}

// 250 MB buffer threshold
private const val LOW_SPACE_BUFFER = 250L * 1024 * 1024

// ── Dialog ────────────────────────────────────────────────────────────────────

@Composable
fun MovieDetailsDialog(
    movie: MovieEntity,
    resumePosition: Long = 0L,
    isBookmarked: Boolean = false,
    onDismiss: () -> Unit,
    onPlay: (startPosition: Long) -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onDownload: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val topSectionHeight = (screenHeight * 0.30f).coerceAtMost(280.dp)
    val posterHeight = (topSectionHeight * 0.9f).coerceAtMost(150.dp)
    val posterWidth = (posterHeight * 0.67f).coerceAtMost(100.dp)

    val hasProgress = resumePosition > 0L
    val buttonCount = if (hasProgress) 5 else 4
    var selectedButton by remember { mutableStateOf(if (hasProgress) 4 else 3) }
    val dialogFocus = remember { FocusRequester() }

    var showStorageInfo by remember { mutableStateOf(false) }
    var showLowSpaceWarning by remember { mutableStateOf(false) }
    var movieSizeBytes by remember { mutableStateOf<Long?>(null) }
    var availableBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    // Compute storage state once sizes are known
    val notEnoughSpace = remember(movieSizeBytes, availableBytes) {
        val size = movieSizeBytes
        size != null && size > 0 && availableBytes > 0 && availableBytes < size
    }
    val lowSpaceAfterDownload = remember(movieSizeBytes, availableBytes) {
        val size = movieSizeBytes
        size != null && size > 0 && availableBytes > 0 &&
                availableBytes >= size && (availableBytes - size) < LOW_SPACE_BUFFER
    }

    // ── Low space warning dialog ───────────────────────────────────────────────
    if (showLowSpaceWarning) {
        var lowSelected by remember { mutableStateOf(0) } // 0=Cancel, 1=Continue
        val lowFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(80)
            try { lowFocus.requestFocus() } catch (_: Exception) {}
        }
        Dialog(
            onDismissRequest = { showLowSpaceWarning = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(0.6f), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(24.dp).focusRequester(lowFocus).focusable()
                    .onKeyEvent { e ->
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) when (e.key) {
                            androidx.compose.ui.input.key.Key.DirectionLeft  -> { lowSelected = (lowSelected - 1 + 2) % 2; true }
                            androidx.compose.ui.input.key.Key.DirectionRight -> { lowSelected = (lowSelected + 1) % 2; true }
                            androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.DirectionCenter -> {
                                if (lowSelected == 0) showLowSpaceWarning = false
                                else { showLowSpaceWarning = false; onDownload() }
                                true
                            }
                            else -> false
                        } else false
                    },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Low Storage Warning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "After this download you'll have less than ${formatBytes(LOW_SPACE_BUFFER)} free. " +
                                "Running low on storage can cause app instability. Are you sure you want to continue?",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (lowSelected == 0) Button(onClick = { showLowSpaceWarning = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        } else OutlinedButton(onClick = { showLowSpaceWarning = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        if (lowSelected == 1) Button(onClick = { showLowSpaceWarning = false; onDownload() }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Continue Anyway")
                        } else OutlinedButton(onClick = { showLowSpaceWarning = false; onDownload() }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Continue Anyway")
                        }
                    }
                }
            }
        }
    }

    // ── Storage info dialog ───────────────────────────────────────────────────
    if (showStorageInfo) {
        val movieSize = movieSizeBytes
        val cancelFR = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            availableBytes = getAvailableStorageBytes()
            movieSizeBytes = getMovieSizeBytes(movie.streamUrl)
        }

        // If not enough space, auto-focus cancel
        LaunchedEffect(movieSizeBytes, availableBytes) {
            if (notEnoughSpace) {
                kotlinx.coroutines.delay(80)
                try { cancelFR.requestFocus() } catch (_: Exception) {}
            }
        }

        var storageSelected by remember { mutableStateOf(if (notEnoughSpace) 0 else 1) } // 0=Cancel, 1=Download
        LaunchedEffect(notEnoughSpace) { if (notEnoughSpace) storageSelected = 0 }
        Dialog(
            onDismissRequest = { showStorageInfo = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(0.6f), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(24.dp).focusRequester(cancelFR).focusable()
                    .onKeyEvent { e ->
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) when (e.key) {
                            androidx.compose.ui.input.key.Key.DirectionLeft  -> { if (!notEnoughSpace) storageSelected = (storageSelected - 1 + 2) % 2; true }
                            androidx.compose.ui.input.key.Key.DirectionRight -> { if (!notEnoughSpace) storageSelected = (storageSelected + 1) % 2; true }
                            androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.DirectionCenter -> {
                                if (storageSelected == 0) showStorageInfo = false
                                else { showStorageInfo = false; if (lowSpaceAfterDownload) showLowSpaceWarning = true else if (!notEnoughSpace) onDownload() }
                                true
                            }
                            else -> false
                        } else false
                    },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Storage Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Movie size:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (movieSize == null) "Checking…" else formatBytes(movieSize),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available space:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(formatBytes(availableBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (movieSize != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            when {
                                movieSize <= 0 -> Text("Movie size could not be determined. Available space is shown above.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                notEnoughSpace -> Text("Not enough space. Free up at least ${formatBytes(movieSize - availableBytes + LOW_SPACE_BUFFER)} before downloading.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                lowSpaceAfterDownload -> Text("Only ${formatBytes(availableBytes - movieSize)} will remain after download.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                                else -> Text("Sufficient space available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (storageSelected == 0) Button(onClick = { showStorageInfo = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        } else OutlinedButton(onClick = { showStorageInfo = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        if (storageSelected == 1) Button(
                            onClick = { showStorageInfo = false; if (lowSpaceAfterDownload) showLowSpaceWarning = true else if (!notEnoughSpace) onDownload() },
                            enabled = !notEnoughSpace, shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                        } else OutlinedButton(
                            onClick = { showStorageInfo = false; if (lowSpaceAfterDownload) showLowSpaceWarning = true else if (!notEnoughSpace) onDownload() },
                            enabled = !notEnoughSpace, shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                        }
                    }
                }
            }
        }
    }

    // ── Main details dialog ───────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + buttonCount) % buttonCount; true }
                                Key.DirectionRight -> { selectedButton = (selectedButton + 1) % buttonCount; true }
                                Key.Enter, Key.DirectionCenter -> {
                                    when (selectedButton) {
                                        0 -> onDismiss()
                                        1 -> onToggleWatchlist()
                                        2 -> { movieSizeBytes = null; availableBytes = 0L; showStorageInfo = true }
                                        3 -> if (hasProgress) onPlay(0) else onPlay(0) // StartOver or Play
                                        4 -> onPlay(resumePosition) // Resume
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                // ── Top section ───────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().height(topSectionHeight)) {
                    AnimatedContent(
                        targetState = movie.backdropUrl ?: movie.posterUrl,
                        transitionSpec = { fadeIn(tween(2500)) togetherWith fadeOut(tween(2500)) },
                        label = "backdrop"
                    ) { url ->
                        AsyncImage(model = url, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)))))
                    Row(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = movie.posterUrl, contentDescription = movie.name,
                            modifier = Modifier.width(posterWidth).height(posterHeight).clip(RoundedCornerShape(8.dp)))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(movie.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!movie.rating.isNullOrEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                        Text(movie.rating!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                if (!movie.releaseDate.isNullOrEmpty()) Text(movie.releaseDate!!, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                                if (!movie.duration.isNullOrEmpty()) Text(movie.duration!!, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                            }
                            if (!movie.genre.isNullOrEmpty()) Text(movie.genre!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // ── Scrollable body ───────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!movie.plot.isNullOrEmpty()) Text(movie.plot!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!movie.cast.isNullOrEmpty()) InfoSection("Cast", movie.cast!!)
                    if (!movie.director.isNullOrEmpty()) InfoSection("Director", movie.director!!)
                }

                // ── Action bar: all right-aligned, Close leftmost ─────────
                // No progress: Spacer | Close | MyList | Download | Play
                // Has progress: Spacer | Close | MyList | Download | StartOver | Resume(default)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    // 0 = Close
                    if (selectedButton == 0) Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Close")
                    } else OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Close")
                    }
                    // 1 = My List
                    if (selectedButton == 1) Button(onClick = onToggleWatchlist, shape = RoundedCornerShape(8.dp)) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isBookmarked) "Remove from My List" else "Add to My List")
                    } else OutlinedButton(onClick = onToggleWatchlist, shape = RoundedCornerShape(8.dp)) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isBookmarked) "Remove from My List" else "Add to My List")
                    }
                    // 2 = Download
                    if (selectedButton == 2) Button(onClick = { movieSizeBytes = null; availableBytes = 0L; showStorageInfo = true }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                    } else OutlinedButton(onClick = { movieSizeBytes = null; availableBytes = 0L; showStorageInfo = true }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                    }
                    if (hasProgress) {
                        // 3 = Start Over
                        if (selectedButton == 3) Button(onClick = { onPlay(0) }, shape = RoundedCornerShape(8.dp)) {
                            Text("Start Over")
                        } else OutlinedButton(onClick = { onPlay(0) }, shape = RoundedCornerShape(8.dp)) {
                            Text("Start Over")
                        }
                        // 4 = Resume (rightmost, default)
                        if (selectedButton == 4) Button(onClick = { onPlay(resumePosition) }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Resume")
                        } else OutlinedButton(onClick = { onPlay(resumePosition) }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Resume")
                        }
                    } else {
                        // 3 = Play (rightmost, default)
                        if (selectedButton == 3) Button(onClick = { onPlay(0) }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Play Movie")
                        } else OutlinedButton(onClick = { onPlay(0) }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Play Movie")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}