package app.nexstream.player.ui.screens.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import app.nexstream.player.ui.components.PosterItem
import app.nexstream.player.ui.components.PosterGridView
import app.nexstream.player.ui.components.PosterGridCallbacks
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun SeriesScreen(
    selectedCategory: String?,
    onPlayEpisode: (streamUrl: String, episodeId: String, startPosition: Long, seriesId: String, seriesName: String, seasonNum: Int, episodeNum: Int, episodeName: String) -> Unit,
    onNavigateToAddPlaylist: () -> Unit,
    onBack: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    restoreIndex: Int = 0,
    restoreTick: Int = 0,
    onItemFocused: (Int) -> Unit = {},
    onRequestSidebarFocus: () -> Unit = {},
    onGridViewReady: (app.nexstream.player.ui.components.PosterGridView?) -> Unit = {},
    onDialogOpen: (Boolean) -> Unit = {},
    onDownloadEpisode: ((streamUrl: String, title: String) -> Unit)? = null,
    onContentFocused: () -> Unit = {},
    viewModel: SeriesViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val _allSeriesList by viewModel.getSeriesByCategory(
        if (selectedCategory == "__favourites__" || selectedCategory == "__search__") null
        else selectedCategory
    ).collectAsState(initial = emptyList())
    val playlists by viewModel.playlists.collectAsState()
    // Avoid flashing "No playlists" before Room emits first value
    var isInitialising by remember { mutableStateOf(true) }
    LaunchedEffect(playlists) { if (isInitialising && playlists.isNotEmpty()) isInitialising = false
    else if (!isInitialising) return@LaunchedEffect
        kotlinx.coroutines.delay(500); isInitialising = false }
    val watchedCounts by viewModel.watchedEpisodeCounts.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    val activeProfile by watchlistViewModel.profileManager.activeProfile.collectAsState()
    val profileId = activeProfile?.id ?: "default"
    val progressItemIds by viewModel.getProgressItemIds(profileId).collectAsState(emptySet())
    val watchedCountsForProfile by viewModel.getWatchedCountsForProfile(profileId).collectAsState(emptyMap())
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(selectedCategory) { if (selectedCategory != "__search__") { searchQuery = ""; debouncedQuery = "" } }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) { debouncedQuery = ""; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        debouncedQuery = searchQuery
    }
    var allSeriesList by remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
    LaunchedEffect(_allSeriesList, watchlistIds, selectedCategory, debouncedQuery) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val base = if (selectedCategory == "__favourites__") _allSeriesList.filter { it.id in watchlistIds } else _allSeriesList
            if (selectedCategory == "__search__" && debouncedQuery.isNotBlank()) base.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
            else base
        }
        allSeriesList = result
    }
    val context = LocalContext.current

    val seriesList = allSeriesList

    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }
    var lastSeriesForDialog by remember { mutableStateOf<SeriesEntity?>(null) }
    var lastPlayedEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastEpisodesForDialog by remember { mutableStateOf<List<app.nexstream.player.data.local.entity.EpisodeEntity>>(emptyList()) }
    var lastSeasonsForDialog by remember { mutableStateOf<List<Int>>(emptyList()) }
    // Live episode list — updates automatically when DB changes (e.g. after playback saves position)
    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    val loadedEpisodes by remember(selectedSeriesId) {
        if (selectedSeriesId != null)
            viewModel.repository.getEpisodesForSeries(selectedSeriesId!!)
        else
            kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    // Per-profile episode progress for the open series
    val episodeProgressMap by remember(selectedSeriesId, profileId) {
        if (selectedSeriesId != null)
            viewModel.progressRepository.getEpisodeProgressForSeries(profileId, selectedSeriesId!!)
        else
            kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())
    val loadedSeasons = remember(loadedEpisodes) {
        loadedEpisodes.map { it.seasonNum }.distinct().sorted()
    }
    var isLoadingDetails by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isInitialising -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            playlists.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("No playlists added yet")
                        Button(onClick = onNavigateToAddPlaylist) { Text("Add Playlist") }
                    }
                }
            }
            else -> {
                var gridViewRef by remember { mutableStateOf<PosterGridView?>(null) }
                val currentSeriesList = remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
                LaunchedEffect(allSeriesList) { currentSeriesList.value = allSeriesList }
                val nsTheme = LocalNexStreamTheme.current
                val sTheme = nsTheme.sidebar
                val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

                Column(modifier = Modifier.fillMaxSize()
                ) {
                    if (selectedCategory == "__search__") {
                        val searchFR = remember { FocusRequester() }
                        var showKeyboard by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); showKeyboard = true }
                        Box(modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFR).focusable().clickable { showKeyboard = true },
                                shape = RoundedCornerShape(12.dp), color = sTheme.categorySelectedBg, tonalElevation = 2.dp
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Search, null, tint = sTheme.railIconActive, modifier = Modifier.size(20.dp))
                                    Text(searchQuery.ifEmpty { "Search series..." }, style = MaterialTheme.typography.bodyMedium,
                                        color = if (searchQuery.isEmpty()) sTheme.categoryText.copy(alpha = 0.5f) else sTheme.categoryText,
                                        modifier = Modifier.weight(1f))
                                    if (searchQuery.isNotEmpty()) Icon(Icons.Default.Close, "Clear",
                                        tint = sTheme.categoryText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp).clickable { searchQuery = "" })
                                }
                            }
                        }
                        app.nexstream.player.ui.components.TvKeyboardSheet(
                            visible       = showKeyboard,
                            value         = searchQuery,
                            onValueChange = { searchQuery = it },
                            onDone        = { showKeyboard = false },
                            onDismiss     = { showKeyboard = false },
                            hint          = "Search series…"
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterStart) {
                            Text(
                                text = when (selectedCategory) {
                                    "__favourites__" -> "Favourites"
                                    null -> "All"
                                    else -> selectedCategory
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = sTheme.categoryText
                            )
                        }
                    }
                    HorizontalDivider(color = sTheme.divider)

                    when {
                        // Only show spinner when actually loading — not when favourites/search is genuinely empty
                        allSeriesList.isEmpty() && selectedCategory != "__favourites__" && selectedCategory != "__search__" -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        seriesList.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(when (selectedCategory) {
                                    "__favourites__" -> "No series in your favourites yet"
                                    "__search__" -> "No results found"
                                    else -> "No series in this category"
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
                            val tertiaryColor = MaterialTheme.colorScheme.tertiary.toArgb()
                            val onTertiaryColor = MaterialTheme.colorScheme.onTertiary.toArgb()
                            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
                            val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                            val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                            val posterItems = remember(seriesList, watchlistIds, watchedCountsForProfile) {
                                seriesList.map { s ->
                                    PosterItem(
                                        id = s.id,
                                        name = s.name,
                                        posterUrl = s.posterUrl,
                                        badge = if (s.seasonCount > 1) "${s.seasonCount} Seasons" else null,
                                        showProgressBadge = (watchedCountsForProfile[s.id] ?: 0) > 0,
                                        isBookmarked = s.id in watchlistIds
                                    )
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    PosterGridView(ctx).also { gridViewRef = it; onGridViewReady(it) }.apply {
                                        setColumnCount(6)
                                        blockFocus()
                                        callbacks = object : PosterGridCallbacks {
                                            override fun onItemClick(item: PosterItem, index: Int) {
                                                val series = currentSeriesList.value.firstOrNull { it.id == item.id } ?: return
                                                onItemFocused(index)
                                                isLoadingDetails = true
                                                selectedSeriesId = null
                                                scope.launch {
                                                    val fullSeries = viewModel.repository.getSeriesById(series.id)
                                                    selectedSeries = fullSeries
                                                    val rawId = series.id.removePrefix("${series.playlistId}-")
                                                    // Fetch from server to ensure latest episode data
                                                    viewModel.loadSeriesDetails(
                                                        playlistId = series.playlistId, seriesId = rawId
                                                    )
                                                    // Now point the live Flow at this series — DB already has latest
                                                    selectedSeriesId = series.id
                                                    isLoadingDetails = false
                                                }
                                            }
                                            override fun onItemLongClick(item: PosterItem, index: Int) {
                                                val series = currentSeriesList.value.firstOrNull { it.id == item.id } ?: return
                                                val isBookmarked = series.id in watchlistIds
                                                watchlistViewModel.toggleWatchlist(WatchlistEntity(
                                                    id = series.id,
                                                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                                    type = WatchlistType.SERIES, name = series.name,
                                                    posterUrl = series.posterUrl, streamUrl = null
                                                ), isBookmarked)
                                                scope.launch { snackbarHostState.showSnackbar(
                                                    if (!isBookmarked) "${series.name} added to My List"
                                                    else "${series.name} removed from My List"
                                                )}
                                            }
                                            override fun onItemFocused(index: Int) { onItemFocused(index) }
                                            override fun onLeftEdge() { /* left key does nothing — use back button */ }
                                            override fun onTopEdge() { }
                                        }
                                    }
                                },
                                update = { view ->
                                    view.primaryColor = primaryColor
                                    view.onPrimaryColor = onPrimaryColor
                                    view.tertiaryColor = tertiaryColor
                                    view.onTertiaryColor = onTertiaryColor
                                    view.surfaceVariantColor = surfaceVariantColor
                                    view.surfaceColor = surfaceColor
                                    view.onSurfaceColor = onSurfaceColor
                                    val prevSize = view.itemCount
                                    view.setItems(posterItems)
                                    // Scroll to top and restore focus when category changes
                                    if (prevSize != posterItems.size) {
                                        view.scrollToIndex(0)
                                        view.requestItemFocus(0)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    // canFocus=false on the Compose wrapper — focus goes
                                    // directly to PosterItemView children via requestFocus override
                                    .focusProperties { canFocus = false }
                                    .focusable(false)
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    LaunchedEffect(selectedSeries) {
        onDialogOpen(selectedSeries != null)
        if (selectedSeries != null) lastSeriesForDialog = selectedSeries
    }

    // Reopen dialog when player closes (restoreTick increments)
    LaunchedEffect(restoreTick) {
        if (restoreTick > 0 && lastSeriesForDialog != null && selectedSeries == null) {
            selectedSeries = lastSeriesForDialog
            selectedSeriesId = lastSeriesForDialog?.id
        }
    }

    selectedSeries?.let { series ->
        val isDialogBookmarked = series.id in watchlistIds
        SeriesDetailsDialog(series = series, episodes = loadedEpisodes, seasons = loadedSeasons,
            episodeProgressMap = episodeProgressMap,
            isLoading = isLoadingDetails, isBookmarked = isDialogBookmarked,
            initialFocusEpisodeId = lastPlayedEpisodeId,
            onDownloadEpisode = onDownloadEpisode,
            onToggleWatchlist = {
                watchlistViewModel.toggleWatchlist(WatchlistEntity(id = series.id,
                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                    type = WatchlistType.SERIES, name = series.name, posterUrl = series.posterUrl, streamUrl = null), isDialogBookmarked)
            },
            onDismiss = { selectedSeries = null; selectedSeriesId = null },
            onPlayEpisode = { streamUrl, episodeId, startPosition, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                lastPlayedEpisodeId = episodeId
                selectedSeries = null // Close dialog while player is open
                onPlayEpisode(streamUrl, episodeId, startPosition, seriesId, seriesName, seasonNum, episodeNum, episodeName)
            }
        )
    }
}

// ── Series card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesCard(
    series: SeriesGridItem,
    hasProgress: Boolean,
    isBookmarked: Boolean = false,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(100), label = "scale")
    Box(
        modifier = Modifier
            .fillMaxWidth().aspectRatio(1f / 1.4f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { fs -> isFocused = fs.isFocused; if (fs.isFocused) onFocused() }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // Border drawn outside card clip — always visible over image
            .then(if (isFocused) Modifier.border(3.dp, primary, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 12.dp else 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(series.posterUrl)
                        .size(180, 252)
                        .crossfade(false)
                        .memoryCacheKey(series.posterUrl)
                        .diskCacheKey(series.posterUrl)
                        .build(),
                    contentDescription = series.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
                // Continue badge — top left
                if (hasProgress) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(10.dp))
                            Text("Continue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                // Season count — top right
                if (series.seasonCount > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Text(
                            if (series.seasonCount == 1) "1 Season" else "${series.seasonCount} Seasons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                }
                // Title gradient + My List icon
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,
                        if (isFocused) Color.Black.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 8.dp, vertical = if (isFocused) 10.dp else 8.dp)) {
                    Text(series.name, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(end = 28.dp))
                    // My List icon — bottom right in title bar
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "In My List" else "Add to My List",
                        tint = if (isBookmarked) primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)
                    )
                }
            }
        } // Card
    } // outer Box
}