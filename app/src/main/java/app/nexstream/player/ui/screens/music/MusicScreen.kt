package app.nexstream.player.ui.screens.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.nexstream.player.data.local.entity.MusicTrackEntity
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_FAVOURITES = "Favourites"
private const val PANEL_QUEUE      = "Queue"

@Composable
fun MusicScreen(
    selectedCategory: String?,
    onBack: () -> Unit = {},
    isContentFocused: Boolean = true,
    onReady: () -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel()
) {
    val albums        by viewModel.albums.collectAsState()
    val favouriteKeys by viewModel.favouriteAlbumKeys.collectAsState()
    val playQueue     by viewModel.playQueue.collectAsState()
    val queueIndex    by viewModel.queueIndex.collectAsState()
    val currentTrack  by viewModel.currentTrack.collectAsState()
    // Collect so AlbumCard recomposes when tokens arrive
    @Suppress("UNUSED_VARIABLE")
    val jellyfinTokens by viewModel.jellyfinTokens.collectAsState()

    val nsTheme      = LocalNexStreamTheme.current
    val sTheme       = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    var openAlbum by remember { mutableStateOf<AlbumUi?>(null) }

    LaunchedEffect(Unit) { onReady() }

    val displayedAlbums = remember(selectedCategory, albums, favouriteKeys) {
        when (selectedCategory) {
            PANEL_FAVOURITES -> albums.filter { it.key in favouriteKeys }
            PANEL_QUEUE      -> emptyList()
            else             -> albums
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text  = when (selectedCategory) {
                    PANEL_FAVOURITES -> "Favourites"
                    PANEL_QUEUE      -> "Queue"
                    else             -> "Music"
                },
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

    if (selectedCategory == PANEL_QUEUE) {
        QueueContent(
            queue            = playQueue,
            currentIndex     = queueIndex,
            onSelectIndex    = { viewModel.skipToIndex(it) },
            isContentFocused = isContentFocused,
            onBack           = onBack
        )
    } else if (displayedAlbums.isEmpty()) {
        EmptyMusicPlaceholder(selectedCategory)
    } else {
        AlbumGrid(
            albums           = displayedAlbums,
            isContentFocused = isContentFocused,
            getArtUrl        = { album -> viewModel.getArtUrl(album.playlistId, album.albumId, album.artUrl) },
            isFavourite      = { album -> album.key in favouriteKeys },
            onAlbumSelected  = { openAlbum = it },
            onBack           = onBack
        )
    }
    } // end Column

    openAlbum?.let { album ->
        AlbumDetailsDialog(
            album        = album,
            isFavourite  = viewModel.isFavourite(album),
            viewModel    = viewModel,
            onDismiss    = { openAlbum = null },
            onToggleFav  = { viewModel.toggleFavouriteAlbum(album) },
        )
    }

    // Queue-panel player: shown when user taps a track in the Queue panel
    if (currentTrack != null && playQueue.isNotEmpty()) {
        MusicPlayerDialog(
            track         = currentTrack!!,
            queue         = playQueue,
            queueIndex    = queueIndex,
            viewModel     = viewModel,
            onDismiss     = { viewModel.clearPlayer() },
            onTrackChange = { _, idx -> viewModel.skipToIndex(idx) }
        )
    }
}

// ── Album Grid ────────────────────────────────────────────────────────────────

private const val GRID_COLS = 6

@Composable
private fun AlbumGrid(
    albums: List<AlbumUi>,
    isContentFocused: Boolean,
    getArtUrl: (AlbumUi) -> String?,
    isFavourite: (AlbumUi) -> Boolean,
    onAlbumSelected: (AlbumUi) -> Unit,
    onBack: () -> Unit
) {
    val gridState  = rememberLazyGridState()
    val frs        = remember(albums.size) { Array(albums.size) { FocusRequester() } }
    var focusedIdx by remember { mutableIntStateOf(0) }
    val scope      = rememberCoroutineScope()

    LaunchedEffect(isContentFocused) {
        if (isContentFocused && albums.isNotEmpty()) {
            delay(120)
            try { frs[focusedIdx.coerceIn(0, albums.lastIndex)].requestFocus() } catch (_: Exception) {}
        }
    }

    LazyVerticalGrid(
        columns               = GridCells.Fixed(GRID_COLS),
        state                 = gridState,
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(albums) { idx, album ->
            AlbumCard(
                album          = album,
                artUrl         = getArtUrl(album),
                isFocused      = focusedIdx == idx,
                isFavourite    = isFavourite(album),
                focusRequester = if (idx < frs.size) frs[idx] else remember { FocusRequester() },
                onFocused      = { focusedIdx = idx },
                onSelected     = { onAlbumSelected(album) },
                onKeyUp        = {
                    val above = idx - GRID_COLS
                    if (above >= 0) {
                        focusedIdx = above
                        scope.launch {
                            gridState.animateScrollToItem(above)
                            delay(40)
                            try { frs[above].requestFocus() } catch (_: Exception) {}
                        }
                    } else if (idx == 0) onBack()
                },
                onKeyDown      = {
                    val below = idx + GRID_COLS
                    if (below <= albums.lastIndex) {
                        focusedIdx = below
                        scope.launch {
                            gridState.animateScrollToItem(below)
                            delay(40)
                            try { frs[below].requestFocus() } catch (_: Exception) {}
                        }
                    }
                },
                onKeyLeft      = {
                    if (idx > 0) {
                        val prev = idx - 1
                        focusedIdx = prev
                        scope.launch { try { frs[prev].requestFocus() } catch (_: Exception) {} }
                    } else onBack()
                }
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumUi,
    artUrl: String?,
    isFocused: Boolean,
    isFavourite: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSelected: () -> Unit,
    onKeyUp: () -> Unit,
    onKeyDown: () -> Unit,
    onKeyLeft: () -> Unit
) {
    val accent = LocalNsAccent.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onSelected(); true }
                    Key.DirectionUp   -> { onKeyUp(); true }
                    Key.DirectionDown -> { onKeyDown(); true }
                    Key.DirectionLeft -> { onKeyLeft(); true }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (isFocused) Modifier.border(2.dp, accent, RoundedCornerShape(8.dp)) else Modifier)
        ) {
            if (!artUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = artUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Album, null,
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            if (isFocused) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp), tint = Color.White)
                }
            }
            if (isFavourite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(14.dp),
                    tint = Color(0xFFE91E63)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            album.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (isFocused) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            album.artist, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Queue view ────────────────────────────────────────────────────────────────

@Composable
private fun QueueContent(
    queue: List<MusicTrackEntity>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    isContentFocused: Boolean,
    onBack: () -> Unit
) {
    if (queue.isEmpty()) { EmptyMusicPlaceholder(PANEL_QUEUE); return }
    val listState  = rememberLazyListState()
    val frs        = remember(queue.size) { Array(queue.size) { FocusRequester() } }
    var focusedIdx by remember { mutableIntStateOf(currentIndex) }
    val scope      = rememberCoroutineScope()

    LaunchedEffect(isContentFocused) {
        if (isContentFocused) {
            delay(120)
            try { frs[currentIndex.coerceIn(0, queue.lastIndex)].requestFocus() } catch (_: Exception) {}
        }
    }

    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                "${queue.size} tracks in queue",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        itemsIndexed(queue) { idx, track ->
            TrackRow(
                track          = track,
                isCurrentTrack = idx == currentIndex,
                isFocused      = focusedIdx == idx,
                focusRequester = if (idx < frs.size) frs[idx] else remember { FocusRequester() },
                onFocused      = { focusedIdx = idx },
                onPlay         = { onSelectIndex(idx) },
                onKeyUp        = {
                    val prev = idx - 1
                    if (prev >= 0) {
                        focusedIdx = prev
                        scope.launch { listState.scrollToItem(maxOf(0, prev - 2)); delay(30); frs[prev].requestFocus() }
                    } else onBack()
                },
                onKeyDown      = {
                    val next = idx + 1
                    if (next < queue.size) {
                        focusedIdx = next
                        scope.launch { listState.scrollToItem(minOf(queue.size - 1, next + 2)); delay(30); frs[next].requestFocus() }
                    }
                }
            )
        }
    }
}

// ── Album Details Dialog (inline player) ─────────────────────────────────────

@Composable
fun AlbumDetailsDialog(
    album: AlbumUi,
    isFavourite: Boolean,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onToggleFav: () -> Unit,
) {
    val context = LocalContext.current
    val accent  = LocalNsAccent.current

    // Subscribe so the dialog recomposes when Jellyfin tokens load (for artwork URLs)
    @Suppress("UNUSED_VARIABLE")
    val jellyfinTokens by viewModel.jellyfinTokens.collectAsState()

    // ── Local playback state ───────────────────────────────────────────────────
    var localQueue by remember { mutableStateOf<List<MusicTrackEntity>>(emptyList()) }
    var localIdx   by remember { mutableIntStateOf(0) }
    val currentLocalTrack = localQueue.getOrNull(localIdx)

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { exoPlayer.release(); viewModel.clearLyrics() } }

    val scope = rememberCoroutineScope()
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    scope.launch { if (localIdx < localQueue.size - 1) localIdx++ }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentLocalTrack?.id) {
        val track = currentLocalTrack ?: return@LaunchedEffect
        val url = viewModel.getFreshStreamUrl(track)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        if (track.hasLyrics) viewModel.loadLyrics(track.playlistId, track.jellyfinItemId.ifBlank { track.id })
        else viewModel.clearLyrics()
    }

    var isExoPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { isExoPlaying = exoPlayer.isPlaying; delay(500) } }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { positionMs = exoPlayer.currentPosition; delay(250) } }

    // ── Lyrics ─────────────────────────────────────────────────────────────────
    val lyrics        by viewModel.lyrics.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()
    var showLyrics    by remember { mutableStateOf(false) }
    val lyricsListState = rememberLazyListState()
    val currentLyricIdx = remember(lyrics, positionMs) {
        val lines = lyrics ?: return@remember -1
        var idx = -1
        for (i in lines.indices) { if (lines[i].startMs <= positionMs) idx = i else break }
        idx
    }
    LaunchedEffect(currentLyricIdx) {
        if (currentLyricIdx > 0) {
            try { lyricsListState.animateScrollToItem(maxOf(0, currentLyricIdx - 2)) } catch (_: Exception) {}
        }
    }

    // ── Focus ──────────────────────────────────────────────────────────────────
    val dialogFR         = remember { FocusRequester() }
    val trackListFR      = remember { FocusRequester() }
    var trackListFocused by remember { mutableStateOf(false) }
    var inTrackList      by remember { mutableStateOf(false) }
    var focusedTrackIdx  by remember { mutableIntStateOf(0) }
    var selectedControl  by remember { mutableStateOf<Int?>(null) }
    val listState        = rememberLazyListState()
    val playAllFR        = remember { FocusRequester() }
    val favFR            = remember { FocusRequester() }
    val lyricsFR         = remember { FocusRequester() }
    val downloadFR       = remember { FocusRequester() }
    var downloadQueued   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        try { playAllFR.requestFocus() } catch (_: Exception) {}
    }

    val albumDescription   by viewModel.albumDescription.collectAsState()
    val favouriteTrackIds  by viewModel.favouriteTrackIds.collectAsState()
    LaunchedEffect(album.albumId) { viewModel.loadAlbumDescription(album.playlistId, album.albumId) }

    LaunchedEffect(localIdx) {
        if (localQueue.isNotEmpty()) {
            focusedTrackIdx = localIdx
            selectedControl = null
            listState.animateScrollToItem(localIdx.coerceIn(0, album.tracks.lastIndex))
        }
    }

    val primaryUrl  = viewModel.getArtUrl(album.playlistId, album.albumId, album.artUrl)
    val backdropUrl = viewModel.getBackdropUrl(album.playlistId, album.albumId)
    var backdropFailed by remember(backdropUrl) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .focusRequester(dialogFR)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                }
        ) {
            // Layer 1: Backdrop (album-own only) or blurred Primary — never parent/artist fallback
            if (!backdropFailed && backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { backdropFailed = true }
                )
            } else {
                AsyncImage(
                    model = primaryUrl ?: album.artUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(24.dp),
                    contentScale = ContentScale.Crop
                )
            }
            // Layer 2: Gradient dark overlay — lighter at top, darker at bottom for track list readability
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f   to Color.Black.copy(alpha = 0.25f),
                        0.38f to Color.Black.copy(alpha = 0.55f),
                        1f   to Color.Black.copy(alpha = 0.82f)
                    )
                )
            )

            // Layer 3: Content
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header: album info ─────────────────────────────────────
                Box(Modifier.fillMaxWidth().height(200.dp)) {

                    // Lyrics overlay
                    if (showLyrics) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.82f))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            when {
                                lyricsLoading -> CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(28.dp), color = accent
                                )
                                lyrics != null && lyrics!!.isNotEmpty() -> {
                                    val lyricLines = lyrics!!
                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        itemsIndexed(lyricLines) { idx, line ->
                                            val isCurrent = idx == currentLyricIdx
                                            Text(
                                                line.text,
                                                fontSize = if (isCurrent) 15.sp else 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isCurrent -> accent
                                                    idx < currentLyricIdx -> Color.White.copy(alpha = 0.3f)
                                                    else -> Color.White.copy(alpha = 0.65f)
                                                },
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                else -> Text(
                                    "No lyrics available", fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }

                    // Top-right: close
                    Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        TextButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.width(4.dp))
                            Text("Close", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }

                    // Album info — bottom-left (hidden when lyrics are showing)
                    if (!showLyrics) Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, end = 120.dp, bottom = 12.dp)
                    ) {
                        Text(
                            album.title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            album.artist, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (album.year != null || album.trackCount > 0) {
                            Text(
                                listOfNotNull(album.year?.toString(), "${album.trackCount} tracks").joinToString(" · "),
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                if (!albumDescription.isNullOrBlank()) {
                    Text(
                        text = albumDescription!!,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isQueueActive = localQueue.isNotEmpty()
                    DialogActionPill(
                        icon = if (isQueueActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        label = if (isQueueActive) "Stop Playing" else "Play All",
                        focusRequester = playAllFR, isPrimary = true,
                        onKeyDown = {
                            scope.launch {
                                inTrackList = true
                                focusedTrackIdx = if (localQueue.isNotEmpty()) localIdx else 0
                                listState.animateScrollToItem(focusedTrackIdx.coerceAtMost(album.tracks.lastIndex))
                                delay(50)
                                try { trackListFR.requestFocus() } catch (_: Exception) {}
                            }
                            true
                        },
                        onClick = {
                            if (isQueueActive) {
                                exoPlayer.stop()
                                localQueue = emptyList()
                                localIdx = 0
                            } else {
                                localQueue = album.tracks
                                localIdx = 0
                            }
                        }
                    )
                    DialogActionPill(
                        icon  = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (isFavourite) "In My List" else "My List",
                        focusRequester = favFR, isPrimary = isFavourite,
                        onKeyDown = {
                            scope.launch {
                                inTrackList = true
                                focusedTrackIdx = 0
                                listState.animateScrollToItem(0)
                                delay(50)
                                try { trackListFR.requestFocus() } catch (_: Exception) {}
                            }
                            true
                        },
                        onClick = onToggleFav
                    )
                    DialogActionPill(
                        icon = Icons.Default.Lyrics,
                        label = "Lyrics",
                        focusRequester = lyricsFR, isPrimary = showLyrics,
                        onKeyDown = {
                            scope.launch {
                                inTrackList = true
                                focusedTrackIdx = 0
                                listState.animateScrollToItem(0)
                                delay(50)
                                try { trackListFR.requestFocus() } catch (_: Exception) {}
                            }
                            true
                        },
                        onClick = { showLyrics = !showLyrics }
                    )
                    if (app.nexstream.player.downloads.NexStreamDownloadManager.hasEnoughSpace(context)) {
                        DialogActionPill(
                            icon = if (downloadQueued) Icons.Default.Check else Icons.Default.Download,
                            label = if (downloadQueued) "Queued" else "Download",
                            focusRequester = downloadFR, isPrimary = downloadQueued,
                            onKeyDown = {
                                scope.launch {
                                    inTrackList = true
                                    focusedTrackIdx = 0
                                    listState.animateScrollToItem(0)
                                    delay(50)
                                    try { trackListFR.requestFocus() } catch (_: Exception) {}
                                }
                                true
                            },
                            onClick = {
                                if (!downloadQueued) {
                                    scope.launch {
                                        album.tracks.forEach { track ->
                                            try {
                                                val url = viewModel.getFreshStreamUrl(track)
                                                app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
                                                    context, url,
                                                    "${album.title} - ${track.title}",
                                                    track.albumArtUrl
                                                )
                                            } catch (_: Exception) {}
                                        }
                                        downloadQueued = true
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // ── Track list ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(trackListFR)
                        .onFocusChanged { trackListFocused = it.isFocused || it.hasFocus }
                        .focusable()
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val focusedTrack    = album.tracks.getOrNull(focusedTrackIdx)
                            val playingTrack    = localQueue.getOrNull(localIdx)
                            val isPlayingFocused = focusedTrack?.id == playingTrack?.id && localQueue.isNotEmpty()
                            when (e.key) {
                                Key.DirectionUp -> {
                                    val prev = focusedTrackIdx - 1
                                    if (prev >= 0) {
                                        focusedTrackIdx = prev
                                        selectedControl = null
                                        scope.launch { listState.animateScrollToItem(prev) }
                                    } else {
                                        inTrackList = false
                                        selectedControl = null
                                        scope.launch { delay(40); try { playAllFR.requestFocus() } catch (_: Exception) {} }
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    val next = focusedTrackIdx + 1
                                    if (next <= album.tracks.lastIndex) {
                                        focusedTrackIdx = next
                                        selectedControl = null
                                        scope.launch { listState.animateScrollToItem(next) }
                                    }
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    if (focusedTrack != null) {
                                        when (selectedControl) {
                                            5 -> viewModel.toggleFavouriteTrack(focusedTrack)
                                            6 -> viewModel.addToQueue(focusedTrack)
                                            else -> if (isPlayingFocused) {
                                                when (selectedControl) {
                                                    0    -> { if (localIdx > 0) { localIdx--; selectedControl = null } }
                                                    1    -> exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                                    3    -> exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                                                    4    -> { if (localIdx < localQueue.size - 1) { localIdx++; selectedControl = null } }
                                                    else -> if (isExoPlaying) exoPlayer.pause() else exoPlayer.play()
                                                }
                                            } else {
                                                localQueue = album.tracks
                                                localIdx = focusedTrackIdx
                                            }
                                        }
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    when (selectedControl) {
                                        6    -> { selectedControl = 5; true }
                                        5    -> { selectedControl = 4; true }
                                        4    -> { selectedControl = 3; true }
                                        3    -> { selectedControl = null; true }
                                        null -> { selectedControl = 1; true }
                                        1    -> { selectedControl = 0; true }
                                        0    -> true
                                        else -> true
                                    }
                                }
                                Key.DirectionRight -> {
                                    when (selectedControl) {
                                        0    -> { selectedControl = 1; true }
                                        1    -> { selectedControl = null; true }
                                        null -> { selectedControl = 3; true }
                                        3    -> { selectedControl = 4; true }
                                        4    -> { selectedControl = 5; true }
                                        5    -> { selectedControl = 6; true }
                                        6    -> true
                                        else -> true
                                    }
                                }
                                else -> false
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(album.tracks) { idx, track ->
                            val isCurrentPlaying = track.id == currentLocalTrack?.id
                            val isRowFocused = idx == focusedTrackIdx && (trackListFocused || inTrackList)
                            AlbumTrackRow(
                                track              = track,
                                isFocused          = isRowFocused,
                                isCurrentlyPlaying = isCurrentPlaying,
                                isExoPlaying       = isExoPlaying,
                                canPrev            = localIdx > 0,
                                canNext            = localIdx < localQueue.size - 1,
                                trackArtUrl        = viewModel.getArtUrl(track.playlistId, track.albumId, track.albumArtUrl),
                                positionMs         = if (isCurrentPlaying) positionMs else 0L,
                                selectedControl    = if (isRowFocused) selectedControl else null,
                                isFavouriteTrack   = track.id in favouriteTrackIds,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Pill button ───────────────────────────────────────────────────────────────

@Composable
private fun DialogActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focusRequester: FocusRequester,
    isPrimary: Boolean = false,
    onKeyDown: () -> Boolean = { false },
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val accent = LocalNsAccent.current
    val bg = when {
        isFocused -> accent
        isPrimary -> accent.copy(alpha = 0.25f)
        else      -> Color.White.copy(alpha = 0.08f)
    }
    val fg = if (isFocused) Color.Black else Color.White

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionDown -> onKeyDown()
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = fg)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

// ── Track row (album dialog) ──────────────────────────────────────────────────

@Composable
private fun AlbumTrackRow(
    track: MusicTrackEntity,
    isFocused: Boolean,
    isCurrentlyPlaying: Boolean,
    isExoPlaying: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    trackArtUrl: String?,
    positionMs: Long,
    selectedControl: Int?,
    isFavouriteTrack: Boolean = false,
) {
    val accent = LocalNsAccent.current
    val progress = if (isCurrentlyPlaying && track.durationMs > 0L)
        (positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f

    val bg = when {
        isCurrentlyPlaying -> accent.copy(alpha = 0.10f)
        isFocused          -> Color.White.copy(alpha = 0.08f)
        else               -> Color.Transparent
    }

    Column(Modifier.fillMaxWidth().background(bg)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Art box — equalizer when playing, album art when not
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentlyPlaying) {
                        Box(
                            Modifier.fillMaxSize().background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EqualizerBars(color = accent, modifier = Modifier.width(22.dp))
                        }
                    } else if (!trackArtUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = trackArtUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title, fontSize = 13.sp,
                        fontWeight = if (isFocused || isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isFocused || isCurrentlyPlaying) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!track.artist.isNullOrBlank() && track.artist != track.albumArtist) {
                        Text(track.artist, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isFocused || isCurrentlyPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selPrev = selectedControl == 0
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selPrev) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(14.dp),
                                tint = when { selPrev -> Color.Black; isCurrentlyPlaying && canPrev -> Color.White.copy(0.6f); else -> Color.White.copy(0.15f) })
                        }
                        val selBack = selectedControl == 1
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selBack) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Replay10, null, modifier = Modifier.size(14.dp),
                                tint = when { selBack -> Color.Black; isCurrentlyPlaying -> accent.copy(0.8f); else -> Color.White.copy(0.15f) })
                        }
                        val selPP = selectedControl == null || selectedControl == 2
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (selPP) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(if (isCurrentlyPlaying && isExoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                                modifier = Modifier.size(17.dp),
                                tint = if (selPP) Color.Black else accent.copy(0.8f))
                        }
                        val selFwd = selectedControl == 3
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selFwd) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Forward10, null, modifier = Modifier.size(14.dp),
                                tint = when { selFwd -> Color.Black; isCurrentlyPlaying -> accent.copy(0.8f); else -> Color.White.copy(0.15f) })
                        }
                        val selNext = selectedControl == 4
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selNext) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(14.dp),
                                tint = when { selNext -> Color.Black; isCurrentlyPlaying && canNext -> Color.White.copy(0.6f); else -> Color.White.copy(0.15f) })
                        }
                        Spacer(Modifier.width(2.dp))
                        val selHeart = selectedControl == 5
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selHeart) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(if (isFavouriteTrack) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                                modifier = Modifier.size(13.dp),
                                tint = when { selHeart -> Color.Black; isFavouriteTrack -> accent; else -> Color.White.copy(0.6f) })
                        }
                        val selQueue = selectedControl == 6
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selQueue) accent else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlaylistAdd, null, modifier = Modifier.size(13.dp),
                                tint = if (selQueue) Color.Black else Color.White.copy(0.6f))
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val durationText = formatDuration(track.durationMs)
                        if (durationText.isNotEmpty()) {
                            Text(durationText, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        if (track.hasLyrics) {
                            Icon(Icons.Default.Lyrics, null, modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                        }
                    }
                }
            }

            if (isCurrentlyPlaying && track.durationMs > 0L) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                        .background(accent.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .background(accent)
                    )
                }
            }
    }
}

// ── Equaliser animation ───────────────────────────────────────────────────────

@Composable
private fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { scale ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(scale)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ── Duration formatter ────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSecs = ms / 1000L
    return "%d:%02d".format(totalSecs / 60L, totalSecs % 60L)
}

// ── Track row (queue view) ────────────────────────────────────────────────────

@Composable
private fun TrackRow(
    track: MusicTrackEntity,
    isCurrentTrack: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onKeyUp: () -> Unit,
    onKeyDown: () -> Unit
) {
    val accent = LocalNsAccent.current
    val bg = when {
        isCurrentTrack -> accent.copy(alpha = 0.12f)
        isFocused      -> Color.White.copy(alpha = 0.06f)
        else           -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bg)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPlay(); true }
                    Key.DirectionUp   -> { onKeyUp(); true }
                    Key.DirectionDown -> { onKeyDown(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (track.albumArtUrl != null) {
                AsyncImage(track.albumArtUrl, null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, null,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
            if (isCurrentTrack) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, fontSize = 13.sp,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentTrack) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(track.artist, track.album).joinToString(" · "),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isFocused) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = accent)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyMusicPlaceholder(selectedCategory: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (selectedCategory) {
                PANEL_QUEUE -> {
                    Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text("Queue is empty", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("Open an album and add tracks to your queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center)
                }
                PANEL_FAVOURITES -> {
                    Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text("No favourites yet", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("Open an album and tap ♥ to add it to your list",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center)
                }
                else -> {
                    Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text("No music found", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("Add a Jellyfin playlist to import your music library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Queue-panel player dialog ─────────────────────────────────────────────────

@Composable
private fun MusicPlayerDialog(
    track: MusicTrackEntity,
    queue: List<MusicTrackEntity>,
    queueIndex: Int,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onTrackChange: (MusicTrackEntity, Int) -> Unit
) {
    val context    = LocalContext.current
    val accent     = LocalNsAccent.current
    val background = LocalNsBackground.current
    val lyrics     by viewModel.lyrics.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()
    val scope      = rememberCoroutineScope()

    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    LaunchedEffect(track.id) {
        val freshUrl = viewModel.getFreshStreamUrl(track)
        exoPlayer.setMediaItem(MediaItem.fromUri(freshUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(track.id) {
        if (track.hasLyrics) viewModel.loadLyrics(track.playlistId, track.jellyfinItemId.ifBlank { track.id })
        else viewModel.clearLyrics()
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(track.id) { while (true) { positionMs = exoPlayer.currentPosition; delay(250) } }

    var isPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { isPlaying = exoPlayer.isPlaying; delay(500) } }

    val lyricsListState   = rememberLazyListState()
    val currentLyricIndex = remember(lyrics, positionMs) {
        val lines = lyrics ?: return@remember -1
        var idx = -1
        for (i in lines.indices) { if (lines[i].startMs <= positionMs) idx = i else break }
        idx
    }
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex > 0) {
            try { lyricsListState.animateScrollToItem(maxOf(0, currentLyricIndex - 2)) } catch (_: Exception) {}
        }
    }

    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); try { dialogFR.requestFocus() } catch (_: Exception) {} }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .focusRequester(dialogFR)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); true
                        }
                        Key.DirectionLeft -> {
                            val prev = queueIndex - 1
                            if (prev >= 0) { onTrackChange(queue[prev], prev); true } else false
                        }
                        Key.DirectionRight -> {
                            val next = queueIndex + 1
                            if (next < queue.size) { onTrackChange(queue[next], next); true } else false
                        }
                        Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {

                Column(modifier = Modifier.width(260.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val artUrl = viewModel.getArtUrl(track.playlistId, track.albumId, track.albumArtUrl)
                        if (!artUrl.isNullOrEmpty()) {
                            AsyncImage(artUrl, null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Album, null,
                                modifier = Modifier.align(Alignment.Center).size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                    Text(track.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
                    Text(track.artist ?: track.albumArtist ?: "", fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (!track.album.isNullOrEmpty()) {
                        Text(track.album, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val hasPrev = queueIndex > 0
                        val hasNext = queueIndex < queue.size - 1
                        IconButton(onClick = { if (hasPrev) onTrackChange(queue[queueIndex - 1], queueIndex - 1) },
                            enabled = hasPrev) {
                            Icon(Icons.Default.SkipPrevious, null,
                                tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp))
                        }
                        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(accent),
                            contentAlignment = Alignment.Center) {
                            IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null, tint = Color.Black, modifier = Modifier.size(28.dp))
                            }
                        }
                        IconButton(onClick = { if (hasNext) onTrackChange(queue[queueIndex + 1], queueIndex + 1) },
                            enabled = hasNext) {
                            Icon(Icons.Default.SkipNext, null,
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val trackProgress = if (track.durationMs > 0L) (positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(trackProgress).fillMaxHeight().background(accent))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(positionMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(formatDuration(track.durationMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${queueIndex + 1} / ${queue.size}", fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.3f))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.5f))
                        Spacer(Modifier.width(4.dp))
                        Text("Close", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(modifier = Modifier.padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lyrics, null, modifier = Modifier.size(16.dp), tint = accent)
                        Text("Lyrics", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    when {
                        lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accent)
                        }
                        lyrics != null && lyrics!!.isNotEmpty() -> {
                            val lyricLines = lyrics!!
                            LazyColumn(
                                state = lyricsListState, modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(lyricLines) { idx, line ->
                                    val isCurrent = idx == currentLyricIndex
                                    Text(line.text,
                                        fontSize = if (isCurrent) 15.sp else 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isCurrent -> accent
                                            idx < currentLyricIndex -> Color.White.copy(alpha = 0.35f)
                                            else -> Color.White.copy(alpha = 0.65f)
                                        },
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                                }
                            }
                        }
                        !track.hasLyrics -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(36.dp),
                                    tint = Color.White.copy(alpha = 0.2f))
                                Text("No lyrics available", fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.3f))
                            }
                        }
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Lyrics not found", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
