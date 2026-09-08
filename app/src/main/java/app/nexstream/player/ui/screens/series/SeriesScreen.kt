package app.nexstream.player.ui.screens.series

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
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
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getSeriesSortOrderFlow
import app.nexstream.player.ui.theme.saveSeriesSortOrder

private enum class SeriesSortOrder(val label: String) {
    A_Z("A → Z"), Z_A("Z → A"), RATING("Top Rated"), RECENTLY_ADDED("Newest Added"), AGE_RATING("Age Rating")
}

private val SERIES_AGE_CERT_ORDER = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
private fun seriesCertOrder(cert: String?): Int =
    if (cert == null || cert == "NR") Int.MAX_VALUE else SERIES_AGE_CERT_ORDER[cert] ?: Int.MAX_VALUE

private fun parseSeriesReleaseDateSortKey(date: String?): Long {
    if (date.isNullOrBlank()) return 0L
    val parts = date.trim().split("-")
    val year  = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val day   = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return year.toLong() * 10000L + month * 100L + day
}

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
    showSearch: Boolean = false,
    autoSearchQuery: String? = null,
    onAutoSearchConsumed: () -> Unit = {},
    silentFilterQuery: String? = null,
    onSilentFilterConsumed: () -> Unit = {},
    onKeyboardDismissed: (() -> Unit)? = null,
    onKeyboardDismissedEmpty: (() -> Unit)? = null,
    onCategorySelect: (String?) -> Unit = {},
    viewModel: SeriesViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val categoryForFlow = if (selectedCategory == "__favourites__") null else selectedCategory
    val _allSeriesList by remember(categoryForFlow) { viewModel.getSeriesByCategory(categoryForFlow) }
        .collectAsState(initial = emptyList())
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
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) { debouncedQuery = ""; return@LaunchedEffect }
        // Don't search while keyboard is visible — apply when it closes
    }
    val maxAgeRating = activeProfile?.maxAgeRating
    val allowNr      = activeProfile?.allowNr ?: true
    var allSeriesList by remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
    var posterItems   by remember { mutableStateOf<List<PosterItem>>(emptyList()) }
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }
    val openDialogSeriesId = selectedSeries?.id
    val needsFavoritesFilter = selectedCategory == "__favourites__"
    val sortOrderName by androidx.compose.ui.platform.LocalContext.current.applicationContext.getSeriesSortOrderFlow().collectAsState(initial = SeriesSortOrder.A_Z.name)
    val sortOrder = SeriesSortOrder.entries.firstOrNull { it.name == sortOrderName } ?: SeriesSortOrder.A_Z
    var showSortDialog by remember { mutableStateOf(false) }
    val sortButtonFR = remember { FocusRequester() }
    var isSorting by remember { mutableStateOf(false) }
    LaunchedEffect(System.identityHashCode(_allSeriesList), System.identityHashCode(watchlistIds), needsFavoritesFilter, debouncedQuery, maxAgeRating, allowNr, openDialogSeriesId, silentFilterQuery, sortOrder) {
        isSorting = true
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val base = if (needsFavoritesFilter) _allSeriesList.filter { it.id in watchlistIds } else _allSeriesList
            val ageFiltered = if (maxAgeRating != null || !allowNr) base.filter { isAllowedByAgeRating(it.certification, maxAgeRating, allowNr) || it.id == openDialogSeriesId } else base
            val filtered = when {
                silentFilterQuery != null -> ageFiltered.filter { it.name.contains(silentFilterQuery, ignoreCase = true) }
                debouncedQuery.isNotBlank() -> ageFiltered.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
                else -> ageFiltered
            }
            when (sortOrder) {
                SeriesSortOrder.A_Z            -> filtered.sortedBy { it.name.lowercase() }
                SeriesSortOrder.Z_A            -> filtered.sortedByDescending { it.name.lowercase() }
                SeriesSortOrder.RATING         -> filtered.sortedByDescending { it.rating?.toDoubleOrNull() ?: -1.0 }
                SeriesSortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { parseSeriesReleaseDateSortKey(it.releaseDate) }
                SeriesSortOrder.AGE_RATING     -> filtered.sortedBy { seriesCertOrder(it.certification) }
            }
        }
        if (result.isEmpty() && allSeriesList.isNotEmpty() && _allSeriesList.isEmpty()) {
            kotlinx.coroutines.delay(150)
        }
        allSeriesList = result
        isSorting = false
    }
    LaunchedEffect(System.identityHashCode(allSeriesList), System.identityHashCode(watchlistIds), System.identityHashCode(watchedCountsForProfile)) {
        posterItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            allSeriesList.map { s ->
                PosterItem(
                    id                = s.id,
                    name              = s.name,
                    posterUrl         = s.posterUrl,
                    badge             = if (s.hasNewEpisodes) "NEW" else null,
                    showProgressBadge = (watchedCountsForProfile[s.id] ?: 0) > 0,
                    isBookmarked      = s.id in watchlistIds,
                    certification     = s.certification,
                    rating            = s.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: s.rating
                )
            }
        }
    }
    val context = LocalContext.current

    val seriesList = allSeriesList

    var gridViewRef by remember { mutableStateOf<PosterGridView?>(null) }
    var handledSilentQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(silentFilterQuery, allSeriesList.size, posterItems.size, gridViewRef) {
        val query = silentFilterQuery ?: run { handledSilentQuery = null; return@LaunchedEffect }
        if (query == handledSilentQuery) return@LaunchedEffect
        if (allSeriesList.isEmpty()) return@LaunchedEffect
        if (posterItems.isEmpty()) return@LaunchedEffect   // wait for posterItems to be computed
        val view = gridViewRef ?: return@LaunchedEffect
        // Filter is active — item is at index 0; scroll and focus it
        onItemFocused(0)
        view.scrollToIndexTop(0)
        // Wait for submitList diff to complete and ViewHolder to be bound
        kotlinx.coroutines.delay(100)
        var attempt = 0
        while (attempt < 20) {
            view.scrollToIndexTop(0)
            if (view.requestItemFocusNow(0)) break
            kotlinx.coroutines.delay(50)
            attempt++
        }
        handledSilentQuery = query   // mark handled only after focus attempt finishes
        onContentFocused()
    }
    var isReloadingAll by remember { mutableStateOf(false) }
    val prevSilentFilter = remember { mutableStateOf<String?>(null) }
    // Single effect with both keys — set and clear run in the same coroutine, no race condition
    LaunchedEffect(silentFilterQuery, allSeriesList.size) {
        val wasFiltered = prevSilentFilter.value != null
        prevSilentFilter.value = silentFilterQuery
        if (wasFiltered && silentFilterQuery == null) isReloadingAll = true
        if (isReloadingAll && allSeriesList.size > 1) isReloadingAll = false
    }

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
    var showKeyboard by remember { mutableStateOf(false) }
    var searchConfirmed by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showSearch) {
        if (showSearch) { searchQuery = ""; debouncedQuery = ""; searchConfirmed = false; kotlinx.coroutines.delay(100); showKeyboard = true }
        else { showKeyboard = false; if (!searchConfirmed) { searchQuery = ""; debouncedQuery = "" }; searchConfirmed = false }
    }
    // Pre-fill query and open keyboard when navigated from Picks
    LaunchedEffect(autoSearchQuery) {
        if (autoSearchQuery != null) {
            searchQuery = autoSearchQuery
            showKeyboard = true
            onAutoSearchConsumed()
        }
    }
    var wasShowingKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(showKeyboard) {
        if (!showKeyboard && wasShowingKeyboard) {
            if (searchQuery.isNotBlank()) {
                debouncedQuery = searchQuery
                searchConfirmed = true
                kotlinx.coroutines.delay(200)
                onKeyboardDismissed?.invoke()
            } else {
                kotlinx.coroutines.delay(200)
                onKeyboardDismissedEmpty?.invoke()
            }
        }
        wasShowingKeyboard = showKeyboard
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeriesSortOrder.entries.forEach { option ->
                        val selected = option == sortOrder
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { scope.launch { context.applicationContext.saveSeriesSortOrder(option.name) }; showSortDialog = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(option.label, style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface)
                                if (selected) Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSortDialog = false }) { Text("Cancel") } }
        )
    }

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
                val currentSeriesList = remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
                LaunchedEffect(System.identityHashCode(allSeriesList)) { currentSeriesList.value = allSeriesList }
                val nsTheme = LocalNexStreamTheme.current
                val sTheme = nsTheme.sidebar
                val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
                val uiStyleOuter = LocalUiStyle.current

                Column(modifier = Modifier.fillMaxSize()
                ) {
                    if (uiStyleOuter != UiStyle.MODERN) {
                    Box(modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart) {
                        if (silentFilterQuery != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "\"$silentFilterQuery\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = sTheme.categoryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSilentFilterConsumed() }) {
                                    Text("Back to All", color = sTheme.categoryText.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (selectedCategory) {
                                            "__favourites__" -> "Favourites"
                                            null -> "All"
                                            else -> selectedCategory
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = sTheme.categoryText
                                    )
                                    if (isSorting) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text  = "(Sorting...)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sTheme.categoryText.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { showSortDialog = true },
                                    modifier = Modifier
                                        .focusRequester(sortButtonFR)
                                        .onKeyEvent { e ->
                                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                            when (e.key) {
                                                Key.DirectionDown -> { gridViewRef?.requestItemFocus(0); true }
                                                else -> false
                                            }
                                        }
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = "Sort",
                                        modifier = Modifier.size(16.dp),
                                        tint = sTheme.categoryText.copy(alpha = 0.7f))
                                    Spacer(Modifier.width(4.dp))
                                    Text(sortOrder.label, style = MaterialTheme.typography.bodySmall,
                                        color = sTheme.categoryText.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = sTheme.divider)
                    } // end if uiStyleOuter != MODERN

                    when {
                        isReloadingAll -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                    Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // Only show spinner when actually loading — not when favourites/search is genuinely empty,
                        // and not during a silentGoTo (grid must be created so gridViewRef can be set)
                        allSeriesList.isEmpty() && selectedCategory != "__favourites__" && !showSearch && silentFilterQuery == null && debouncedQuery.isBlank() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        seriesList.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(when {
                                    selectedCategory == "__favourites__" -> "No series in your favourites yet"
                                    silentFilterQuery != null -> "No series found"
                                    showSearch -> "No results found"
                                    else -> "No series in this category"
                                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            if (uiStyleOuter == UiStyle.MODERN) {
                                val recentlyWatched by viewModel.repository.getRecentlyWatched().collectAsState(initial = emptyList())
                                ModernSeriesContent(
                                    seriesList       = seriesList,
                                    continueWatching = recentlyWatched,
                                    progressItemIds  = progressItemIds,
                                    watchedCounts    = watchedCountsForProfile,
                                    selectedCategory = selectedCategory,
                                    onSeriesClick    = { series ->
                                        isLoadingDetails = true
                                        selectedSeriesId = null
                                        scope.launch {
                                            try {
                                                val fullSeries = viewModel.repository.getSeriesById(series.id)
                                                selectedSeries = fullSeries
                                                val rawId = series.id.removePrefix("${series.playlistId}-")
                                                viewModel.loadSeriesDetails(playlistId = series.playlistId, seriesId = rawId)
                                                selectedSeriesId = series.id
                                            } finally {
                                                isLoadingDetails = false
                                            }
                                        }
                                    },
                                    onContinueWatchingClick = { item ->
                                        val seriesId = item.seriesId ?: return@ModernSeriesContent
                                        scope.launch {
                                            val fullSeries = viewModel.repository.getSeriesById(seriesId)
                                            selectedSeries = fullSeries
                                            selectedSeriesId = seriesId
                                        }
                                    },
                                )
                            } else {
                                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                                val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
                                val tertiaryColor = MaterialTheme.colorScheme.tertiary.toArgb()
                                val onTertiaryColor = MaterialTheme.colorScheme.onTertiary.toArgb()
                                val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
                                val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
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
                                                        try {
                                                            val fullSeries = viewModel.repository.getSeriesById(series.id)
                                                            selectedSeries = fullSeries
                                                            val rawId = series.id.removePrefix("${series.playlistId}-")
                                                            // Fetch from server to ensure latest episode data
                                                            viewModel.loadSeriesDetails(
                                                                playlistId = series.playlistId, seriesId = rawId
                                                            )
                                                            // Now point the live Flow at this series — DB already has latest
                                                            selectedSeriesId = series.id
                                                        } finally {
                                                            isLoadingDetails = false
                                                        }
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
                                                override fun onLeftEdge() { onRequestSidebarFocus() }
                                                override fun onTopEdge() { runCatching { sortButtonFR.requestFocus() } }
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
                                        if (prevSize > 0 && prevSize != posterItems.size && silentFilterQuery == null) {
                                            view.scrollToIndex(0)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // canFocus=false on the Compose wrapper — focus goes
                                        // directly to PosterItemView children via requestFocus override
                                        .focusProperties { canFocus = false }
                                        .focusable(false)
                                )
                            } // end else (Classic UI)
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        app.nexstream.player.ui.components.TvKeyboardSheet(
            visible       = showKeyboard,
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            onDone        = { showKeyboard = false },
            onDismiss     = { showKeyboard = false },
            hint          = "Search series…",
            modifier      = Modifier.fillMaxSize()
        )
    }

    androidx.activity.compose.BackHandler(enabled = showKeyboard) { showKeyboard = false }
    androidx.activity.compose.BackHandler(enabled = debouncedQuery.isNotBlank() && !showKeyboard) { showClearSearchDialog = true }

    if (showClearSearchDialog) {
        val clearFR = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            try { clearFR.requestFocus() } catch (_: Exception) {}
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showClearSearchDialog = false }) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Clear search results?",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                searchQuery = ""; debouncedQuery = ""; searchConfirmed = false
                                showClearSearchDialog = false
                            },
                            modifier = Modifier.focusRequester(clearFR)
                        ) { Text("Clear") }
                        OutlinedButton(onClick = { showClearSearchDialog = false }) { Text("Keep") }
                    }
                }
            }
        }
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
            maxAgeRating = activeProfile?.maxAgeRating,
            allowNr = activeProfile?.allowNr ?: true,
            playlistName = playlists.find { it.id == series.playlistId }?.let { p ->
                val label = when (p.type) { "XTREAM" -> "Xtream Codes"; "JELLYFIN" -> "Jellyfin"; else -> p.type }
                "$label · ${p.name}"
            },
            onDownloadEpisode = onDownloadEpisode,
            onFetchCertification = { viewModel.fetchCertificationIfMissing(series.id, series.name) },
            onFetchOriginalLanguage = { viewModel.fetchOriginalLanguageIfMissing(series.id, series.name) },
            whisperManager = viewModel.whisperSubtitleManager,
            onFetchTrailerUrl = { viewModel.fetchTrailerUrl(series.name) },
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
                // Watched indicator — top right (eye icon)
                if (hasProgress) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Icon(Icons.Default.Visibility, null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(10.dp))
                    }
                }
                // Certification badge — top right
                if (series.certification != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Text(
                            series.certification,
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

private fun isAllowedByAgeRating(certification: String?, maxAgeRating: String?, allowNr: Boolean = true): Boolean {
    if (certification == "NR") return allowNr
    if (maxAgeRating == null) return true
    if (certification == null) return true
    val order = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
    val certOrder = order[certification] ?: return true
    val maxOrder  = order[maxAgeRating]  ?: return true
    return certOrder <= maxOrder
}