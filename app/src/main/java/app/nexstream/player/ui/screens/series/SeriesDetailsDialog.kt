package app.nexstream.player.ui.screens.series

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val GRID_COLS = 4
private const val LOW_SPACE_BUFFER_EP = 250L * 1024 * 1024

// ── Storage utilities (mirrors MovieDetailsDialog) ────────────────────────────
private fun getAvailableStorageBytesEp(): Long {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return stat.availableBlocksLong * stat.blockSizeLong
}

private suspend fun getEpisodeSizeBytes(streamUrl: String): Long = withContext(Dispatchers.IO) {
    try {
        val headConn = URL(streamUrl).openConnection() as HttpURLConnection
        headConn.requestMethod = "HEAD"
        headConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        headConn.connectTimeout = 8_000; headConn.readTimeout = 8_000
        headConn.instanceFollowRedirects = true; headConn.connect()
        val headSize = headConn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        headConn.disconnect()
        if (headSize > 0) return@withContext headSize
        val getConn = URL(streamUrl).openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        getConn.setRequestProperty("Range", "bytes=0-0")
        getConn.connectTimeout = 8_000; getConn.readTimeout = 8_000
        getConn.instanceFollowRedirects = true; getConn.connect()
        val contentRange = getConn.getHeaderField("Content-Range")
        getConn.disconnect()
        if (contentRange != null) {
            val total = contentRange.substringAfterLast("/").trim().toLongOrNull()
            if (total != null && total > 0) return@withContext total
        }
        -1L
    } catch (_: Exception) { -1L }
}

private fun formatBytesEp(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "%.0f KB".format(bytes / 1024.0)
    }
}

// ── Overlay states for the info bar ──────────────────────────────────────────
private sealed class InfoBarState {
    object Idle : InfoBarState()                          // show episode title/plot
    object ResumeChoice : InfoBarState()                  // Resume | Start Over | Download
    object PlayChoice : InfoBarState()                    // Play | Download
    object CheckingSize : InfoBarState()                  // "Checking size…"
    data class StorageInfo(
        val episodeSize: Long,
        val available: Long,
        val notEnough: Boolean,
        val lowAfter: Boolean
    ) : InfoBarState()
    object LowSpaceWarning : InfoBarState()
}

@Composable
fun SeriesDetailsDialog(
    series: SeriesEntity,
    episodes: List<EpisodeEntity>,
    seasons: List<Int>,
    episodeProgressMap: Map<String, Long> = emptyMap(),  // episodeId → positionMs per profile
    isLoading: Boolean,
    isBookmarked: Boolean = false,
    initialFocusEpisodeId: String? = null,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onDownloadEpisode: ((streamUrl: String, title: String) -> Unit)? = null,
    onPlayEpisode: (streamUrl: String, episodeId: String, startPosition: Long, seriesId: String, seriesName: String, seasonNum: Int, episodeNum: Int, episodeName: String) -> Unit
) {
    var selectedSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    var seasonDropdownExpanded by remember { mutableStateOf(false) }

    val episodesForSeason = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNum == selectedSeason }.sortedBy { it.episodeNum }
    }

    // Default: last played > first unwatched > first
    val defaultIndex = remember(episodesForSeason, initialFocusEpisodeId, episodeProgressMap) {
        if (initialFocusEpisodeId != null) {
            val idx = episodesForSeason.indexOfFirst { it.id == initialFocusEpisodeId }
            if (idx >= 0) return@remember idx
        }
        val first = episodesForSeason.indexOfFirst { (episodeProgressMap[it.id] ?: 0L) <= 0L }
        if (first >= 0) first else 0
    }

    val configuration = LocalConfiguration.current
    val screenHeight  = configuration.screenHeightDp.dp
    val headerHeight  = (screenHeight * 0.25f).coerceAtMost(190.dp)
    val posterHeight  = (headerHeight * 0.80f).coerceAtMost(95.dp)
    val posterWidth   = (posterHeight * 0.67f).coerceAtMost(65.dp)

    // Bar buttons: 0=Close  1=MyList  [2=Season — rightmost, default]
    val hasMultiSeason  = seasons.size > 1
    val barButtonCount  = if (hasMultiSeason) 3 else 2
    val seasonsLoaded   = seasons.isNotEmpty()
    // selectedButton is set by LaunchedEffect(seasons) once loaded — start hidden
    var selectedButton  by remember { mutableStateOf(if (hasMultiSeason) 2 else 1) }

    // Focus zone
    var inGrid           by remember { mutableStateOf(false) }
    var focusedGridIndex by remember { mutableStateOf(defaultIndex) }

    // Info bar state — drives inline overlay
    var infoBarState     by remember { mutableStateOf<InfoBarState>(InfoBarState.Idle) }
    // Which overlay button is selected (varies by state)
    var overlayButton    by remember { mutableStateOf(0) }

    val focusedEpisode = remember(focusedGridIndex, episodesForSeason) {
        episodesForSeason.getOrNull(focusedGridIndex)
    }

    // Reset overlay when focused episode changes
    LaunchedEffect(focusedGridIndex) { infoBarState = InfoBarState.Idle }

    val dialogFocus         = remember { FocusRequester() }
    val seasonButtonFR      = remember { FocusRequester() }
    val closeButtonFR       = remember { FocusRequester() }
    val gridFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val gridState           = rememberLazyGridState()
    val scope               = rememberCoroutineScope()

    // Focus dialog and set selectedButton when seasons load
    LaunchedEffect(seasons) {
        if (seasons.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        selectedButton = if (hasMultiSeason) 2 else 1
        // Focus first episode in grid like CatchUpDetailsDialog
        if (episodesForSeason.isNotEmpty()) {
            inGrid = true
            val target = defaultIndex.coerceIn(0, episodesForSeason.lastIndex)
            focusedGridIndex = target
            kotlinx.coroutines.delay(80)
            try { gridFocusRequesters[target]?.requestFocus() } catch (_: Exception) {
                try { dialogFocus.requestFocus() } catch (_: Exception) {}
            }
        } else {
            try { dialogFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedSeason) {
        focusedGridIndex = 0; inGrid = false; infoBarState = InfoBarState.Idle
        gridState.scrollToItem(0)
    }

    LaunchedEffect(episodesForSeason, defaultIndex) {
        if (episodesForSeason.isNotEmpty() && defaultIndex > 0) {
            gridState.scrollToItem(defaultIndex)
            focusedGridIndex = defaultIndex
        }
    }

    // Helper: trigger download flow for focused episode
    fun startDownloadFlow(ep: EpisodeEntity) {
        infoBarState = InfoBarState.CheckingSize
        overlayButton = 1 // default Download
        scope.launch {
            val available = getAvailableStorageBytesEp()
            val size = getEpisodeSizeBytes(ep.streamUrl)
            val notEnough = size > 0 && available < size
            val lowAfter  = size > 0 && available >= size && (available - size) < LOW_SPACE_BUFFER_EP
            infoBarState = InfoBarState.StorageInfo(size, available, notEnough, lowAfter)
            overlayButton = if (notEnough) 0 else 1
        }
    }

    // Helper: play episode
    fun playEp(ep: EpisodeEntity, fromStart: Boolean) {
        val resumePos = episodeProgressMap[ep.id] ?: 0L
        onPlayEpisode(ep.streamUrl, ep.id,
            if (!fromStart && resumePos > 0L) resumePos else 0L,
            series.id, series.name, ep.seasonNum, ep.episodeNum, ep.name)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.90f).fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false

                        // ── Overlay active: intercept left/right/enter ────────
                        val overlay = infoBarState
                        if (overlay !is InfoBarState.Idle && overlay !is InfoBarState.CheckingSize) {
                            val overlayCount = when (overlay) {
                                is InfoBarState.ResumeChoice  -> if (onDownloadEpisode != null) 3 else 2
                                is InfoBarState.PlayChoice    -> if (onDownloadEpisode != null) 2 else 1
                                is InfoBarState.StorageInfo   -> 2
                                is InfoBarState.LowSpaceWarning -> 2
                                else -> 0
                            }
                            when (e.key) {
                                Key.DirectionLeft  -> { overlayButton = (overlayButton - 1 + overlayCount) % overlayCount; true }
                                Key.DirectionRight -> { overlayButton = (overlayButton + 1) % overlayCount; true }
                                Key.DirectionUp    -> { infoBarState = InfoBarState.Idle; true }
                                Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                    val ep = focusedEpisode
                                    when (overlay) {
                                        is InfoBarState.ResumeChoice -> when (overlayButton) {
                                            0 -> { if (ep != null) playEp(ep, fromStart = true) }
                                            1 -> { if (ep != null) playEp(ep, fromStart = false) }
                                            2 -> { if (ep != null) startDownloadFlow(ep) }
                                        }
                                        is InfoBarState.PlayChoice -> when (overlayButton) {
                                            0 -> { if (ep != null) playEp(ep, fromStart = true) }
                                            1 -> { if (ep != null) startDownloadFlow(ep) }
                                        }
                                        is InfoBarState.StorageInfo -> when (overlayButton) {
                                            0 -> { infoBarState = InfoBarState.Idle }
                                            1 -> {
                                                if (overlay.lowAfter) infoBarState = InfoBarState.LowSpaceWarning
                                                else if (!overlay.notEnough && ep != null) {
                                                    onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum}E${ep.episodeNum} - ${ep.name}")
                                                    infoBarState = InfoBarState.Idle
                                                }
                                            }
                                        }
                                        is InfoBarState.LowSpaceWarning -> when (overlayButton) {
                                            0 -> { infoBarState = InfoBarState.Idle }
                                            1 -> {
                                                if (ep != null) onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum}E${ep.episodeNum} - ${ep.name}")
                                                infoBarState = InfoBarState.Idle
                                            }
                                        }
                                        else -> {}
                                    }
                                    true
                                }
                                Key.Back -> { infoBarState = InfoBarState.Idle; true }
                                else -> false
                            }
                            return@onKeyEvent true
                        }

                        when (e.key) {
                            Key.DirectionLeft -> {
                                if (!inGrid) { selectedButton = (selectedButton - 1 + barButtonCount) % barButtonCount; true }
                                else {
                                    val col = focusedGridIndex % GRID_COLS
                                    if (col > 0) {
                                        val prev = focusedGridIndex - 1
                                        focusedGridIndex = prev
                                        scope.launch { gridFocusRequesters[prev]?.requestFocus() }
                                        true
                                    } else false
                                }
                            }
                            Key.DirectionRight -> {
                                if (!inGrid) { selectedButton = (selectedButton + 1) % barButtonCount; true }
                                else {
                                    val col = focusedGridIndex % GRID_COLS
                                    if (col < GRID_COLS - 1 && focusedGridIndex < episodesForSeason.lastIndex) {
                                        val next = focusedGridIndex + 1
                                        focusedGridIndex = next
                                        scope.launch { gridFocusRequesters[next]?.requestFocus() }
                                        true
                                    } else false
                                }
                            }
                            Key.DirectionDown -> {
                                if (!inGrid && episodesForSeason.isNotEmpty()) {
                                    inGrid = true
                                    val target = focusedGridIndex.coerceIn(0, episodesForSeason.lastIndex)
                                    scope.launch {
                                        gridState.animateScrollToItem(target)
                                        kotlinx.coroutines.delay(60)
                                        gridFocusRequesters[target]?.requestFocus()
                                    }
                                    true
                                } else if (inGrid) {
                                    val next = focusedGridIndex + GRID_COLS
                                    if (next < episodesForSeason.size) {
                                        focusedGridIndex = next
                                        scope.launch {
                                            gridState.animateScrollToItem(next)
                                            kotlinx.coroutines.delay(40)
                                            gridFocusRequesters[next]?.requestFocus()
                                        }
                                        true
                                    } else {
                                        // Bottom of grid → action bar
                                        inGrid = false
                                        selectedButton = barButtonCount - 1
                                        try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (inGrid) {
                                    val prev = focusedGridIndex - GRID_COLS
                                    if (prev >= 0) {
                                        focusedGridIndex = prev
                                        scope.launch {
                                            gridState.animateScrollToItem(prev)
                                            kotlinx.coroutines.delay(40)
                                            gridFocusRequesters[prev]?.requestFocus()
                                        }
                                    } else {
                                        inGrid = false
                                        try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                    }
                                    true
                                } else if (!inGrid && episodesForSeason.isNotEmpty()) {
                                    // Bar → last row of grid
                                    inGrid = true
                                    val lastRow = ((episodesForSeason.size - 1) / GRID_COLS) * GRID_COLS
                                    val target = (lastRow + (focusedGridIndex % GRID_COLS))
                                        .coerceAtMost(episodesForSeason.lastIndex)
                                    focusedGridIndex = target
                                    scope.launch {
                                        gridState.animateScrollToItem(target)
                                        kotlinx.coroutines.delay(60)
                                        gridFocusRequesters[target]?.requestFocus()
                                    }
                                    true
                                } else false
                            }
                            Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                if (!inGrid) {
                                    when {
                                        selectedButton == 0 -> onDismiss()
                                        selectedButton == 1 -> onToggleWatchlist()
                                        selectedButton == 2 && hasMultiSeason -> seasonDropdownExpanded = true
                                    }
                                    true
                                } else {
                                    // Enter on grid cell — show overlay
                                    val ep = episodesForSeason.getOrNull(focusedGridIndex)
                                    if (ep != null) {
                                        if ((episodeProgressMap[ep.id] ?: 0L) > 0L) {
                                            infoBarState = InfoBarState.ResumeChoice
                                            overlayButton = 1 // default Resume
                                        } else {
                                            if (onDownloadEpisode != null) {
                                                infoBarState = InfoBarState.PlayChoice
                                                overlayButton = 0 // default Play
                                            } else {
                                                playEp(ep, fromStart = true)
                                            }
                                        }
                                        true
                                    } else false
                                }
                            }
                            else -> false
                        }
                    }
            ) {
                // ── Header ────────────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
                    AnimatedContent(
                        targetState = series.backdropUrl ?: series.posterUrl,
                        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                        label = "backdrop"
                    ) { imageUrl ->
                        AsyncImage(model = imageUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)))
                    ))
                    Row(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = series.posterUrl, contentDescription = series.name,
                            modifier = Modifier.width(posterWidth).height(posterHeight).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(series.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!series.rating.isNullOrEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                        Text(series.rating!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                if (seasons.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) {
                                        Text("${seasons.size} Season${if (seasons.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (!series.genre.isNullOrEmpty())
                                    Text(series.genre!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ── Info bar / inline overlay ─────────────────────────────────
                val infoBarHeight = when (infoBarState) {
                    is InfoBarState.StorageInfo, is InfoBarState.LowSpaceWarning -> 72.dp
                    else -> 52.dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(infoBarHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (val state = infoBarState) {
                        is InfoBarState.Idle -> {
                            if (focusedEpisode != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text("E${focusedEpisode.episodeNum} · ${focusedEpisode.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!focusedEpisode.plot.isNullOrEmpty())
                                        Text(focusedEpisode.plot!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                Text("Select an episode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.align(Alignment.CenterStart))
                            }
                        }
                        is InfoBarState.ResumeChoice -> {
                            // Start Over | Resume | Download
                            Row(modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OverlayButton("↺  Start Over", overlayButton == 0) {}
                                OverlayButton("▶  Resume", overlayButton == 1) {}
                                if (onDownloadEpisode != null)
                                    OverlayButton("⬇  Download", overlayButton == 2) {}
                            }
                        }
                        is InfoBarState.PlayChoice -> {
                            Row(modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OverlayButton("▶  Play", overlayButton == 0) {}
                                if (onDownloadEpisode != null)
                                    OverlayButton("⬇  Download", overlayButton == 1) {}
                            }
                        }
                        is InfoBarState.CheckingSize -> {
                            Row(modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Checking size…", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is InfoBarState.StorageInfo -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Episode: ${formatBytesEp(state.episodeSize)}  ·  Available: ${formatBytesEp(state.available)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        state.notEnough  -> MaterialTheme.colorScheme.error
                                        state.lowAfter   -> MaterialTheme.colorScheme.tertiary
                                        else             -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayButton("Cancel", overlayButton == 0) {}
                                    OverlayButton("Download", overlayButton == 1, enabled = !state.notEnough) {}
                                }
                            }
                        }
                        is InfoBarState.LowSpaceWarning -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Low storage — less than ${formatBytesEp(LOW_SPACE_BUFFER_EP)} will remain after download.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayButton("Cancel", overlayButton == 0) {}
                                    OverlayButton("Continue Anyway", overlayButton == 1) {}
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Thumbnail grid ─────────────────────────────────────────────
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text("Loading episodes…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    episodesForSeason.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No episodes available", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(GRID_COLS),
                        state = gridState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(episodesForSeason, key = { _, ep -> ep.id }) { index, episode ->
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(fr) { gridFocusRequesters[index] = fr }

                            val isFocused = inGrid && focusedGridIndex == index
                            val episodePos = episodeProgressMap[episode.id] ?: 0L
                            val hasProgress = episodePos > 0L
                            val progressFraction = remember(episodePos, episode.duration) {
                                if (hasProgress && !episode.duration.isNullOrEmpty()) {
                                    val ms = parseDurationToMs(episode.duration!!)
                                    if (ms > 0L) (episodePos.toFloat() / ms.toFloat()).coerceIn(0f, 1f)
                                    else 0f
                                } else 0f
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .focusRequester(fr)
                                    .focusable()
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused && inGrid) {
                                            focusedGridIndex = index
                                            infoBarState = InfoBarState.Idle
                                        }
                                    }
                                    .then(
                                        if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        focusedGridIndex = index; inGrid = true
                                        // Direct click always plays (resume if progress)
                                        onPlayEpisode(episode.streamUrl, episode.id,
                                            if (hasProgress) episodePos else 0L,
                                            series.id, series.name,
                                            episode.seasonNum, episode.episodeNum, episode.name)
                                    }
                            ) {
                                if (!episode.posterUrl.isNullOrEmpty()) {
                                    AsyncImage(model = episode.posterUrl, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                                Box(Modifier.fillMaxSize().background(
                                    Color.Black.copy(alpha = if (isFocused) 0.15f else 0.4f)
                                ))
                                Surface(
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        "Episode ${episode.episodeNum}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                    )
                                }
                                if (isFocused) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                                        modifier = Modifier.size(28.dp).align(Alignment.Center))
                                }
                                if (progressFraction >= 0.9f) {
                                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text("✓", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                // Progress bar — surface background, primary fill
                                if (progressFraction > 0f) {
                                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).align(Alignment.BottomCenter)) {
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)))
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(progressFraction)
                                            .background(MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Action bar — only render once seasons are known to prevent flicker ──
                if (seasonsLoaded) Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.weight(1f))

                    // 0 = Close — always OutlinedButton, border highlights when selected
                    val closeSelected = !inGrid && selectedButton == 0
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (closeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (closeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (closeSelected) 2.dp else 1.dp,
                            color = if (closeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.focusRequester(closeButtonFR)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Close")
                    }

                    // 1 = My List
                    val myListSelected = !inGrid && selectedButton == 1
                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (myListSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (myListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (myListSelected) 2.dp else 1.dp,
                            color = if (myListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isBookmarked) "Remove from My List" else "Add to My List")
                    }

                    // 2 = Season dropdown (rightmost, default)
                    if (hasMultiSeason) {
                        Box {
                            val seasonSelected = !inGrid && selectedButton == 2
                            OutlinedButton(
                                onClick = { seasonDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (seasonSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor = if (seasonSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (seasonSelected) 2.dp else 1.dp,
                                    color = if (seasonSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.focusRequester(seasonButtonFR)
                            ) {
                                Icon(Icons.Default.Tv, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Season $selectedSeason")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = seasonDropdownExpanded,
                                onDismissRequest = {
                                    seasonDropdownExpanded = false
                                    selectedButton = 2
                                    try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                }
                            ) {
                                seasons.forEach { season ->
                                    DropdownMenuItem(
                                        text = { Text("Season $season") },
                                        onClick = { selectedSeason = season; seasonDropdownExpanded = false },
                                        leadingIcon = {
                                            if (season == selectedSeason) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            else Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Small overlay button ──────────────────────────────────────────────────────
@Composable
private fun OverlayButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseDurationToMs(duration: String): Long {
    return try {
        val trimmed = duration.trim()
        if (!trimmed.contains(":")) return (trimmed.toDouble() * 1000).toLong()
        val parts = trimmed.split(":").map { it.toDouble().toLong() }
        when (parts.size) {
            3    -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            2    -> (parts[0] * 60 + parts[1]) * 1000
            1    -> parts[0] * 1000
            else -> 0L
        }
    } catch (_: Exception) { 0L }
}