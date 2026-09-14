package app.nexstream.player.ui.screens.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.ui.screens.movies.ModernMovieDetailsDialog
import app.nexstream.player.ui.screens.series.SeriesDetailsDialog
import app.nexstream.player.downloads.NexStreamDownloadManager
import app.nexstream.player.recording.NexStreamRecordingManager
import app.nexstream.player.recording.RecordingItem
import app.nexstream.player.recording.RecordingService
import app.nexstream.player.ui.screens.recentlywatched.ClearHistoryConfirmDialog
import app.nexstream.player.ui.components.ContentActionDialog
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary

@Composable
fun WatchlistScreen(
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (item: WatchlistEntity) -> Unit,
    onSeriesClick: (item: WatchlistEntity) -> Unit,
    onGoToMovie: (name: String) -> Unit = {},
    onGoToSeries: (name: String) -> Unit = {},
    onGoToEpgForChannel: (channelName: String) -> Unit = {},
    selectedType: String? = null,
    showSearch: Boolean = false,
    showClearConfirm: Boolean = false,
    onClearDismissed: () -> Unit = {},
    profileId: String = "default",
    onPlayFile: (filePath: String, title: String) -> Unit = { _, _ -> },
    firstItemFocusRequester: FocusRequester? = null,
    onRequestSidebarFocus: () -> Unit = {},
    onLaunchPlayer: (url: String, movieId: String?, episodeId: String?, seriesId: String?, startPos: Long, title: String?, subtitle: String?) -> Unit = { _, _, _, _, _, _, _ -> },
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val allItems by viewModel.allItems.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val watchlistIds by viewModel.watchlistIds.collectAsState()
    val seriesWithNewEpisodes by viewModel.seriesIdsWithNewEpisodes.collectAsState()
    var dialogItem by remember { mutableStateOf<WatchlistEntity?>(null) }

    var movieDialogEntity      by remember { mutableStateOf<MovieEntity?>(null) }
    var movieDialogUpdated     by remember { mutableStateOf<MovieEntity?>(null) }
    var movieResumePosition    by remember { mutableStateOf(0L) }
    var seriesDialogEntity     by remember { mutableStateOf<SeriesEntity?>(null) }
    var seriesDialogUpdated    by remember { mutableStateOf<SeriesEntity?>(null) }
    var seriesDialogEpisodes   by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var seriesDialogSeasons    by remember { mutableStateOf<List<Int>>(emptyList()) }
    var seriesDialogLoading    by remember { mutableStateOf(false) }
    var seriesDialogProgressMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var dialogLoadedForItem    by remember { mutableStateOf<String?>(null) }
    var channelDialogEntity      by remember { mutableStateOf<app.nexstream.player.data.local.entity.ChannelEntity?>(null) }
    var channelCurrentProgram    by remember { mutableStateOf<app.nexstream.player.data.local.entity.ProgramEntity?>(null) }
    var channelNextProgram       by remember { mutableStateOf<app.nexstream.player.data.local.entity.ProgramEntity?>(null) }

    LaunchedEffect(dialogItem) {
        val item = dialogItem
        dialogLoadedForItem     = null
        movieDialogEntity       = null
        movieDialogUpdated      = null
        movieResumePosition     = 0L
        seriesDialogEntity      = null
        seriesDialogUpdated     = null
        seriesDialogEpisodes    = emptyList()
        seriesDialogSeasons     = emptyList()
        seriesDialogProgressMap = emptyMap()
        channelDialogEntity     = null
        channelCurrentProgram   = null
        channelNextProgram      = null
        if (item == null) return@LaunchedEffect
        when (item.type) {
            WatchlistType.MOVIE -> {
                var base = viewModel.getMovieById(item.id)
                // Fallback 1: exact stream URL match
                if (base == null && !item.streamUrl.isNullOrEmpty()) base = viewModel.getMovieByStreamUrl(item.streamUrl)
                // Fallback 2: Xtream VOD ID from stream URL path (handles credential changes between devices).
                // Extract from the URL rather than the item ID, because the item ID suffix is a
                // sequential counter for M3U movies and can falsely match an unrelated Xtream movie.
                if (base == null && !item.streamUrl.isNullOrEmpty()) {
                    val vodId = item.streamUrl
                        .substringAfterLast('/')   // "12345.mp4" or "movie-title.mp4"
                        .substringBefore('.')       // "12345" or "movie-title"
                        .takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
                    if (vodId != null) base = viewModel.getMovieByXtreamStreamId(vodId)
                }
                // Fallback 3: title match
                if (base == null) base = viewModel.findMovieByName(item.name)
                movieDialogEntity = base
                if (base != null) {
                    movieResumePosition = viewModel.getMoviePosition(base.id)
                    val detailed = viewModel.loadMovieDetails(base)
                    if (detailed != null) movieDialogUpdated = detailed
                }
            }
            WatchlistType.SERIES -> {
                var s = viewModel.getSeriesById(item.id)
                // Fallback: look up by name when playlist IDs differ across devices
                if (s == null) s = viewModel.getSeriesByName(item.name)
                seriesDialogEntity = s
                if (s != null) {
                    seriesDialogProgressMap = viewModel.getEpisodeProgressMap(s.id)
                    seriesDialogLoading = true
                    val localEps = viewModel.getLocalEpisodes(s.id)
                    if (localEps.isNotEmpty()) {
                        seriesDialogEpisodes = localEps
                        seriesDialogSeasons  = viewModel.getLocalSeasons(s.id)
                    } else {
                        val (updated, fetchedEps) = viewModel.loadSeriesDetails(s)
                        seriesDialogEpisodes = fetchedEps
                        seriesDialogSeasons  = fetchedEps.map { it.seasonNum }.distinct().sorted()
                        if (updated != null) seriesDialogUpdated = updated
                    }
                    seriesDialogLoading = false
                }
            }
            WatchlistType.CHANNEL -> {
                var ch = viewModel.getChannelById(item.id)
                // Fallback 1: exact stream URL match
                if (ch == null && !item.streamUrl.isNullOrEmpty()) ch = viewModel.getChannelByStreamUrl(item.streamUrl)
                // Fallback 2: Xtream stream ID from URL path
                if (ch == null) {
                    val streamId = item.id.substringAfterLast('-')
                    if (streamId.isNotEmpty() && streamId.all { it.isDigit() })
                        ch = viewModel.getChannelByXtreamStreamId(streamId)
                }
                // Fallback 3: name match
                if (ch == null) ch = viewModel.getChannelByName(item.name)
                channelDialogEntity = ch
                if (ch != null) {
                    val epgId = ch.epgChannelId ?: ch.id
                    channelCurrentProgram = viewModel.getCurrentProgram(epgId)
                    channelNextProgram    = viewModel.getNextProgram(epgId)
                }
            }
            else -> {}
        }
        dialogLoadedForItem = item.id
    }

    var searchQuery    by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var showKeyboard   by remember { mutableStateOf(false) }

    LaunchedEffect(showSearch) {
        if (showSearch) { kotlinx.coroutines.delay(100); showKeyboard = true }
        else { searchQuery = ""; debouncedQuery = ""; showKeyboard = false }
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) { debouncedQuery = ""; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        debouncedQuery = searchQuery
    }
    var wasShowingKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(showKeyboard) {
        if (!showKeyboard && wasShowingKeyboard) {
            kotlinx.coroutines.delay(200)
            try { firstItemFocusRequester?.requestFocus() } catch (_: Exception) {}
        }
        wasShowingKeyboard = showKeyboard
    }

    BackHandler(enabled = showKeyboard) { showKeyboard = false }

    val filteredItems = remember(allItems, selectedType, debouncedQuery) {
        if (selectedType == "Recordings") return@remember emptyList()
        val byType = when (selectedType) {
            "Live TV" -> allItems.filter { it.type == WatchlistType.CHANNEL }
            "Movies"  -> allItems.filter { it.type == WatchlistType.MOVIE }
            "Series"  -> allItems.filter { it.type == WatchlistType.SERIES }
            "Music"   -> allItems.filter { it.type == WatchlistType.MUSIC }
            else      -> allItems.filter { it.type != WatchlistType.MUSIC }
        }
        if (showSearch && debouncedQuery.isNotBlank())
            byType.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
        else byType
    }

    val firstItemFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        val uiStyle = LocalUiStyle.current
        if (selectedType == "Recordings") {
            val context = LocalContext.current
            RecordingsContent(
                profileId               = profileId,
                firstItemFocusRequester = firstItemFR,
                onPlayFile              = onPlayFile,
                onDelete                = { item ->
                    NexStreamRecordingManager.deleteRecording(context, item.id)
                },
            )
        } else if (uiStyle == UiStyle.MODERN) {
            ModernMyListContent(
                allItems                = allItems,
                filteredItems           = filteredItems,
                selectedType            = selectedType,
                firstItemFocusRequester = firstItemFR,
                onItemClick             = { dialogItem = it },
                onRequestSidebarFocus   = onRequestSidebarFocus,
                progressMap             = progressMap,
            )
        } else {
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
            HorizontalDivider(color = sTheme.divider)

            if (allItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = sTheme.categoryText.copy(alpha = 0.3f))
                        Text("Your list is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = sTheme.categoryText.copy(alpha = 0.6f))
                        Text("Bookmark channels, movies and series to add them here",
                            style = MaterialTheme.typography.bodySmall,
                            color = sTheme.categoryText.copy(alpha = 0.4f))
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${selectedType?.lowercase() ?: "items"} in your list",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (selectedType == null) {
                // ── All: horizontal card rows by type ─────────────────────────
                val channels = remember(filteredItems) { filteredItems.filter { it.type == WatchlistType.CHANNEL } }
                val movies   = remember(filteredItems) { filteredItems.filter { it.type == WatchlistType.MOVIE } }
                val series   = remember(filteredItems) { filteredItems.filter { it.type == WatchlistType.SERIES } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (channels.isNotEmpty()) {
                        item(key = "section_channels") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WatchlistSectionHeader("Live TV")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(channels, key = { _, it -> "ch_${it.id}" }) { idx, ch ->
                                        ContentCard(
                                            name            = ch.name,
                                            posterUrl       = ch.posterUrl,
                                            defaultIcon     = Icons.Default.Tv,
                                            focusRequester  = if (idx == 0) firstItemFR else null,
                                            onFocused       = {},
                                            onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                                            onClick         = { dialogItem = ch }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (movies.isNotEmpty()) {
                        item(key = "section_movies") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WatchlistSectionHeader("Movies")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(movies, key = { _, it -> "mov_${it.id}" }) { idx, movie ->
                                        ContentCard(
                                            name            = movie.name,
                                            posterUrl       = movie.posterUrl,
                                            defaultIcon     = Icons.Default.Movie,
                                            focusRequester  = if (idx == 0 && channels.isEmpty()) firstItemFR else null,
                                            onFocused       = {},
                                            onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                                            onClick         = { dialogItem = movie }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (series.isNotEmpty()) {
                        item(key = "section_series") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WatchlistSectionHeader("Series")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(series, key = { _, it -> "ser_${it.id}" }) { idx, ser ->
                                        ContentCard(
                                            name            = ser.name,
                                            posterUrl       = ser.posterUrl,
                                            defaultIcon     = Icons.Default.VideoLibrary,
                                            badge           = if (ser.id in seriesWithNewEpisodes) "NEW" else null,
                                            focusRequester  = if (idx == 0 && channels.isEmpty() && movies.isEmpty()) firstItemFR else null,
                                            onFocused       = {},
                                            onDirectionLeft = if (idx == 0) onRequestSidebarFocus else null,
                                            onClick         = { dialogItem = ser }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Specific type: card row (same layout as All, single section) ─
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    item(key = "row_type") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                                val icon = when (item.type) {
                                    WatchlistType.CHANNEL -> Icons.Default.Tv
                                    WatchlistType.MOVIE   -> Icons.Default.Movie
                                    WatchlistType.SERIES  -> Icons.Default.VideoLibrary
                                    WatchlistType.MUSIC   -> Icons.Default.Album
                                }
                                ContentCard(
                                    name = item.name,
                                    posterUrl = item.posterUrl,
                                    defaultIcon = icon,
                                    badge = if (item.type == WatchlistType.SERIES && item.id in seriesWithNewEpisodes) "NEW" else null,
                                    focusRequester = if (index == 0) firstItemFR else null,
                                    onFocused = {},
                                    onClick = { dialogItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end else (Classic UI)
        TvKeyboardSheet(
            visible       = showKeyboard,
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            onDone        = { showKeyboard = false },
            onDismiss     = { showKeyboard = false },
            hint          = "Search my list…",
            modifier      = Modifier.fillMaxSize()
        )
    }

    val selectedItem = dialogItem
    if (selectedItem != null && dialogLoadedForItem == selectedItem.id) {
        val movie = movieDialogEntity
        val series = seriesDialogEntity

        when {
            selectedItem.type == WatchlistType.MOVIE && movie != null -> {
                val displayMovie = movieDialogUpdated ?: movie
                val isMovieBookmarked = displayMovie.id in watchlistIds || selectedItem.id in watchlistIds
                ModernMovieDetailsDialog(
                    movie          = displayMovie,
                    resumePosition = movieResumePosition,
                    isBookmarked   = isMovieBookmarked,
                    onDismiss      = { movieDialogEntity = null; movieDialogUpdated = null; dialogItem = null },
                    onPlay         = { startPos ->
                        onLaunchPlayer(displayMovie.streamUrl, displayMovie.id, null, null, startPos, displayMovie.name, null)
                        movieDialogEntity = null; movieDialogUpdated = null; dialogItem = null
                    },
                    onToggleWatchlist = {
                        viewModel.toggleWatchlist(
                            WatchlistEntity(
                                id        = displayMovie.id,
                                profileId = viewModel.profileManager.activeProfile.value?.id ?: "default",
                                type      = WatchlistType.MOVIE,
                                name      = displayMovie.name,
                                posterUrl = displayMovie.posterUrl,
                                streamUrl = displayMovie.streamUrl
                            ),
                            isMovieBookmarked
                        )
                        if (isMovieBookmarked) { movieDialogEntity = null; movieDialogUpdated = null; dialogItem = null }
                    },
                    onFetchCertification    = { viewModel.fetchMovieCertification(displayMovie.id, displayMovie.name) },
                    onFetchOriginalLanguage = { viewModel.fetchMovieOriginalLanguage(displayMovie.id, displayMovie.name) },
                    onFetchRtData           = { viewModel.fetchMovieRtData(displayMovie.id, displayMovie.name) },
                    onFetchTrailerUrl       = { viewModel.fetchMovieTrailerUrl(displayMovie) },
                )
            }
            selectedItem.type == WatchlistType.SERIES && series != null -> {
                val displaySeries = seriesDialogUpdated ?: series
                val isSeriesBookmarked = displaySeries.id in watchlistIds || selectedItem.id in watchlistIds
                SeriesDetailsDialog(
                    series              = displaySeries,
                    episodes            = seriesDialogEpisodes,
                    seasons             = seriesDialogSeasons,
                    episodeProgressMap  = seriesDialogProgressMap,
                    isLoading           = seriesDialogLoading,
                    isBookmarked        = isSeriesBookmarked,
                    onDownloadEpisode   = { url, title ->
                        app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(context, url, title, profileId)
                    },
                    onDismiss     = { seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null },
                    onToggleWatchlist = {
                        viewModel.toggleWatchlist(
                            WatchlistEntity(
                                id        = displaySeries.id,
                                profileId = viewModel.profileManager.activeProfile.value?.id ?: "default",
                                type      = WatchlistType.SERIES,
                                name      = displaySeries.name,
                                posterUrl = displaySeries.posterUrl,
                                streamUrl = null
                            ),
                            isSeriesBookmarked
                        )
                        if (isSeriesBookmarked) { seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null }
                    },
                    onFetchCertification    = { viewModel.fetchSeriesCertification(displaySeries.id, displaySeries.name) },
                    onFetchOriginalLanguage = { viewModel.fetchSeriesOriginalLanguage(displaySeries.id, displaySeries.name) },
                    onFetchRtData           = { viewModel.fetchSeriesRtData(displaySeries.id, displaySeries.name) },
                    onFetchTrailerUrl       = { viewModel.fetchSeriesTrailerUrl(displaySeries.name) },
                    onPlayEpisode = { streamUrl, episodeId, startPos, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                        onLaunchPlayer(streamUrl, null, episodeId, seriesId, startPos, seriesName, "S${seasonNum}E${episodeNum} - $episodeName")
                        seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null
                    }
                )
            }
            selectedItem.type == WatchlistType.CHANNEL && channelDialogEntity != null -> {
                val ch = channelDialogEntity!!
                ChannelDetailsDialog(
                    channel        = ch,
                    currentProgram = channelCurrentProgram,
                    nextProgram    = channelNextProgram,
                    isBookmarked   = true,
                    onDismiss    = { channelCurrentProgram = null; channelNextProgram = null; dialogItem = null },
                    onWatch      = {
                        onChannelClick(ch.streamUrl, ch.name)
                        channelCurrentProgram = null; channelNextProgram = null; dialogItem = null
                    },
                    onToggleWatchlist = {
                        viewModel.removeFromWatchlist(selectedItem.id, selectedItem.type)
                        channelCurrentProgram = null; channelNextProgram = null; dialogItem = null
                    },
                    onGoToEpg = {
                        onGoToEpgForChannel(ch.name)
                        channelCurrentProgram = null; channelNextProgram = null; dialogItem = null
                    }
                )
            }
            else -> {
                val goToLabel = when (selectedItem.type) {
                    WatchlistType.CHANNEL -> "Go to EPG"
                    WatchlistType.MOVIE   -> "Go to Movies"
                    WatchlistType.SERIES  -> "Go to Series"
                    WatchlistType.MUSIC   -> "Go to Music"
                }
                // Play is available for movies/channels/music that have a stream URL
                val playAction: (() -> Unit)? = when {
                    selectedItem.type == WatchlistType.MOVIE && !selectedItem.streamUrl.isNullOrEmpty() -> {
                        { onLaunchPlayer(selectedItem.streamUrl, null, null, null, 0L, selectedItem.name, null); dialogItem = null }
                    }
                    selectedItem.type == WatchlistType.CHANNEL && !selectedItem.streamUrl.isNullOrEmpty() -> {
                        { onChannelClick(selectedItem.streamUrl, selectedItem.name); dialogItem = null }
                    }
                    selectedItem.type == WatchlistType.MUSIC && !selectedItem.streamUrl.isNullOrEmpty() -> {
                        { onChannelClick(selectedItem.streamUrl, selectedItem.name); dialogItem = null }
                    }
                    else -> null
                }
                ContentActionDialog(
                    name        = selectedItem.name,
                    posterUrl   = selectedItem.posterUrl,
                    goToLabel   = goToLabel,
                    removeLabel = "Remove from My List",
                    onDismiss   = { dialogItem = null },
                    onRemove    = {
                        dialogItem = null
                        viewModel.removeFromWatchlist(selectedItem.id, selectedItem.type)
                    },
                    onGoTo = {
                        dialogItem = null
                        when (selectedItem.type) {
                            WatchlistType.CHANNEL -> onGoToEpgForChannel(selectedItem.name)
                            WatchlistType.MOVIE   -> onGoToMovie(selectedItem.name)
                            WatchlistType.SERIES  -> onGoToSeries(selectedItem.name)
                            WatchlistType.MUSIC   -> selectedItem.streamUrl?.let { url ->
                                onChannelClick(url, selectedItem.name)
                            }
                        }
                    },
                    onPlay = playAction,
                )
            }
        }
    }

    if (showClearConfirm) {
        val context = LocalContext.current
        val isDestructive = selectedType == "Recordings" || selectedType == "Downloads"
        val typeLabel = when (selectedType) {
            "Live TV"    -> "Live TV channels"
            "Movies"     -> "movies"
            "Series"     -> "series"
            "Music"      -> "music"
            "Recordings" -> "all recordings"
            "Downloads"  -> "all downloads"
            else         -> "everything"
        }
        val warning = when (selectedType) {
            "Recordings" -> "This will permanently delete all recording files from storage. This cannot be undone."
            "Downloads"  -> "This will permanently delete all downloaded files from storage. This cannot be undone."
            else         -> null
        }
        ClearHistoryConfirmDialog(
            title       = if (isDestructive) "Delete ${if (selectedType == "Recordings") "Recordings" else "Downloads"}" else "Clear My List",
            message     = "Remove $typeLabel from My List?",
            warningText = warning,
            onDismiss   = { onClearDismissed() },
            onConfirm   = {
                when (selectedType) {
                    "Recordings" -> NexStreamRecordingManager.deleteAllRecordings(context, profileId)
                    "Downloads"  -> NexStreamDownloadManager.deleteAllDownloads(context, profileId)
                    else         -> viewModel.clearAllForType(selectedType)
                }
                onClearDismissed()
            }
        )
    }
}

@Composable
private fun RecordingsContent(
    profileId: String,
    firstItemFocusRequester: FocusRequester,
    onPlayFile: (filePath: String, title: String) -> Unit,
    onDelete: (RecordingItem) -> Unit,
) {
    val context     = LocalContext.current
    val nsTheme     = LocalNexStreamTheme.current
    val sTheme      = nsTheme.sidebar
    val accent      = LocalNsAccent.current
    val surface     = LocalNsSurface.current
    val divider     = LocalNsDivider.current
    val textPrimary = LocalNsTextPrimary.current
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val recordings by NexStreamRecordingManager.observeRecordings(context, profileId)
        .collectAsState(initial = emptyList())

    var dialogItem by remember { mutableStateOf<RecordingItem?>(null) }
    var stopTarget by remember { mutableStateOf<RecordingItem?>(null) }

    stopTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { stopTarget = null },
            title = { Text("Stop Recording") },
            text  = { Text("Stop recording \"${item.title}\"? What has been recorded so far will be saved.") },
            confirmButton = {
                Button(onClick = { RecordingService.stop(context, item.id); stopTarget = null }) { Text("Stop") }
            },
            dismissButton = {
                OutlinedButton(onClick = { stopTarget = null }) { Text("Cancel") }
            }
        )
    }

    dialogItem?.let { rec ->
        RecordingDetailsDialog(
            recording  = rec,
            onDismiss  = { dialogItem = null },
            onPlay     = { onPlayFile(rec.filePath, rec.title); dialogItem = null },
            onStop     = { stopTarget = rec; dialogItem = null },
            onDelete   = { onDelete(rec); dialogItem = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Recordings",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord, null,
                        modifier = Modifier.size(48.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f)
                    )
                    Text(
                        "No recordings yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = sTheme.categoryText.copy(alpha = 0.6f)
                    )
                    Text(
                        "Record live or upcoming programmes from the EPG",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            // Group by calendar date (newest first); recordings within each group already sorted newest first
            val dateGroups = remember(recordings) {
                recordings
                    .groupBy { rec ->
                        val c = java.util.Calendar.getInstance().apply { timeInMillis = rec.startedAt }
                        Triple(
                            c.get(java.util.Calendar.YEAR),
                            c.get(java.util.Calendar.MONTH),
                            c.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }
                    .entries
                    .sortedByDescending { (k, _) -> k.first * 10000 + k.second * 100 + k.third }
            }
            val firstItem = recordings.firstOrNull()

            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 100.dp),
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((dateKey, items) in dateGroups) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RecordingSectionHeader(
                            formatRecordingDateHeader(dateKey.first, dateKey.second, dateKey.third),
                            accent, divider
                        )
                    }
                    itemsIndexed(items, key = { _, r -> r.id }) { idx, rec ->
                        RecordingPosterCard(
                            recording      = rec,
                            accent         = accent,
                            surface        = surface,
                            textPrimary    = textPrimary,
                            focusRequester = if (idx == 0 && rec == firstItem) firstItemFocusRequester else null,
                            onClick        = { dialogItem = rec },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingDetailsDialog(
    recording: RecordingItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val canPlay    = !recording.isActive && recording.bytesWritten > 0L
    val imageUrl   = recording.posterUrl?.takeIf { it.isNotBlank() }
        ?: recording.channelLogoUrl?.takeIf { it.isNotBlank() }
    val dateStr    = remember(recording.startedAt) {
        java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.UK)
            .format(java.util.Date(recording.startedAt))
    }
    val statusText = when {
        recording.isActive    -> "Recording in progress…"
        canPlay               -> "$dateStr · ${formatRecordingBytes(recording.bytesWritten)}"
        else                  -> "Recording failed"
    }
    val accent = LocalNsAccent.current

    // Button layout: 0=Close, [1=Play if canPlay], [next=Stop if active], last=Delete
    val buttons = buildList {
        add("close")
        if (canPlay) add("play")
        if (recording.isActive) add("stop")
        add("delete")
    }
    var selectedBtn by remember { mutableStateOf(if (canPlay) 1 else 0) }
    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); try { dialogFR.requestFocus() } catch (_: Exception) {} }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Surface(
            modifier       = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f),
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
                                    "stop"   -> onStop()
                                    "delete" -> onDelete()
                                }; true
                            }
                            Key.Back -> { onDismiss(); true }
                            else     -> false
                        }
                    }
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model              = imageUrl,
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
                    RecDialogPill(
                        icon       = Icons.Default.Close,
                        label      = "Close",
                        isSelected = selectedBtn == 0,
                        accent     = accent,
                        onClick    = onDismiss,
                    )
                }
                // Bottom content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(recording.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!recording.description.isNullOrBlank()) {
                        Text(recording.description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Text(statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        buttons.forEachIndexed { idx, action ->
                            if (action == "close") return@forEachIndexed
                            val (icon, label, color) = when (action) {
                                "play"   -> Triple(Icons.Default.PlayArrow, "Play",   accent)
                                "stop"   -> Triple(Icons.Default.Stop,      "Stop",   Color(0xFFFF7043))
                                "delete" -> Triple(Icons.Default.Delete,    "Delete", Color(0xFFD32F2F))
                                else     -> Triple(Icons.Default.Close,     "Close",  accent)
                            }
                            RecDialogPill(
                                icon       = icon,
                                label      = label,
                                isSelected = selectedBtn == idx,
                                accent     = color,
                                onClick    = {
                                    when (action) { "play" -> onPlay(); "stop" -> onStop(); "delete" -> onDelete() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecDialogPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg      = if (isSelected) accent else Color.White.copy(alpha = 0.15f)
    val content = Color.White
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
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = content)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = content)
    }
}

@Composable
private fun RecordingPosterCard(
    recording: RecordingItem,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val context   = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }
    val scale     by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "recScale")

    val canPlay     = !recording.isActive && recording.bytesWritten > 0L
    val statusLabel = when { recording.isActive -> "REC"; canPlay -> "Done"; else -> "Failed" }
    val statusColor = when {
        recording.isActive -> Color(0xFFD32F2F)
        canPlay            -> Color(0xFF4CAF50)
        else               -> Color(0xFF9E9E9E)
    }
    val imageUrl = recording.posterUrl?.takeIf { it.isNotBlank() }
        ?: recording.channelLogoUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier            = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused.value = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                        else -> false
                    }
                }
                .clickable { onClick() }
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model            = imageUrl,
                    contentDescription = null,
                    contentScale     = ContentScale.Crop,
                    modifier         = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Movie,
                    contentDescription = null,
                    tint               = textPrimary.copy(alpha = 0.25f),
                    modifier           = Modifier.align(Alignment.Center).size(36.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.9f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(text = statusLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if ((canPlay || recording.isActive) && isFocused.value) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = if (canPlay) Icons.Default.PlayArrow else Icons.Default.Stop,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            text     = recording.title,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordingSectionHeader(title: String, accent: Color, divider: Color) {
    Column(
        modifier            = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.4f)),
        )
    }
}

private fun formatRecordingDateHeader(year: Int, month: Int, day: Int): String {
    val months = arrayOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else           -> "th"
    }
    return "${day}${suffix} ${months[month.coerceIn(0, 11)]} $year"
}

private fun formatRecordingBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "${(bytes / 1024).coerceAtLeast(0)} KB"
    }
}

@Composable
private fun WatchlistSectionHeader(title: String) {
    val nsTheme = LocalNexStreamTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = nsTheme.sidebar.categoryText
    )
}

@Composable
private fun WatchlistRow(
    item: WatchlistEntity,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (
                    e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                )) { onClick(); true } else false
            }
            .focusable(),
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrEmpty()) {
                    AsyncImage(model = item.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(imageVector = defaultIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
