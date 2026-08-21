package app.nexstream.player.ui.screens.movies

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import app.nexstream.player.ui.components.PosterItem
import app.nexstream.player.ui.components.PosterGridView
import app.nexstream.player.ui.components.PosterGridCallbacks
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.data.repository.WatchProgressRepository
import app.nexstream.player.subtitle.WhisperSubtitleManager
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getMovieSortOrderFlow
import app.nexstream.player.ui.theme.saveMovieSortOrder
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

private enum class MovieSortOrder(val label: String) {
    A_Z("A → Z"), Z_A("Z → A"), RATING("Top Rated"), RECENTLY_ADDED("Newest Added"), AGE_RATING("Age Rating")
}

private val AGE_CERT_ORDER = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
private fun movieCertOrder(cert: String?): Int =
    if (cert == null || cert == "NR") Int.MAX_VALUE else AGE_CERT_ORDER[cert] ?: Int.MAX_VALUE

private fun parseReleaseDateSortKey(date: String?): Long {
    if (date.isNullOrBlank()) return 0L
    val parts = date.trim().split("-")
    val year  = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val day   = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return year.toLong() * 10000L + month * 100L + day
}

@Composable
fun MoviesScreen(
    selectedCategory: String?,
    onMovieClick: (streamUrl: String, movieId: String, startPosition: Long, movieName: String) -> Unit,
    onNavigateToAddPlaylist: () -> Unit,
    onBack: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    restoreIndex: Int = 0,
    restoreTick: Int = 0,
    onItemFocused: (Int) -> Unit = {},
    onRequestSidebarFocus: () -> Unit = {},
    onGridViewReady: (app.nexstream.player.ui.components.PosterGridView?) -> Unit = {},
    onContentFocused: () -> Unit = {},
    onDialogOpen: (Boolean) -> Unit = {},
    showSearch: Boolean = false,
    autoSearchQuery: String? = null,
    onAutoSearchConsumed: () -> Unit = {},
    silentFilterQuery: String? = null,
    onSilentFilterConsumed: () -> Unit = {},
    onKeyboardDismissed: (() -> Unit)? = null,
    onKeyboardDismissedEmpty: (() -> Unit)? = null,
    onCategorySelect: (String?) -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val categoryForFlow = if (selectedCategory == "__favourites__") null else selectedCategory
    val _allMovies by remember(categoryForFlow) { viewModel.getMoviesByCategory(categoryForFlow) }
        .collectAsState(initial = emptyList())
    val playlists by viewModel.playlists.collectAsState()
    // Avoid flashing "No playlists" before Room emits first value
    var isInitialising by remember { mutableStateOf(true) }
    LaunchedEffect(playlists) { if (isInitialising && playlists.isNotEmpty()) isInitialising = false
    else if (!isInitialising) return@LaunchedEffect
        kotlinx.coroutines.delay(500); isInitialising = false }
    val isLoadingVod by viewModel.repository.isLoadingVOD.collectAsState()
    val vodLoaded by viewModel.vodLoadedCount.collectAsState()
    val vodTotal by viewModel.vodTotalCount.collectAsState()
    var movieWithDetails by remember { mutableStateOf<MovieEntity?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    val activeProfile by watchlistViewModel.profileManager.activeProfile.collectAsState()
    val profileId = activeProfile?.id ?: "default"
    val progressItemIds by viewModel.getProgressItemIds(profileId).collectAsState(emptySet())
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(showSearch) {
        if (showSearch) { searchQuery = ""; debouncedQuery = ""; kotlinx.coroutines.delay(100); showKeyboard = true }
        else { showKeyboard = false; searchQuery = ""; debouncedQuery = "" }
    }
    // Pre-fill query and open keyboard when navigated from Picks
    LaunchedEffect(autoSearchQuery) {
        if (autoSearchQuery != null) {
            searchQuery = autoSearchQuery
            showKeyboard = true
            onAutoSearchConsumed()
        }
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) { debouncedQuery = ""; return@LaunchedEffect }
        // Don't search while keyboard is visible — apply when it closes
    }
    var wasShowingKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(showKeyboard) {
        if (!showKeyboard && wasShowingKeyboard) {
            if (searchQuery.isNotBlank()) {
                debouncedQuery = searchQuery
                kotlinx.coroutines.delay(200)
                onKeyboardDismissed?.invoke()
            } else {
                kotlinx.coroutines.delay(200)
                onKeyboardDismissedEmpty?.invoke()
            }
        }
        wasShowingKeyboard = showKeyboard
    }
    val sortOrderName by LocalContext.current.applicationContext.getMovieSortOrderFlow().collectAsState(initial = MovieSortOrder.A_Z.name)
    val sortOrder = MovieSortOrder.entries.firstOrNull { it.name == sortOrderName } ?: MovieSortOrder.A_Z
    var showSortDialog by remember { mutableStateOf(false) }
    val sortButtonFR = remember { FocusRequester() }
    val maxAgeRating = activeProfile?.maxAgeRating
    val allowNr      = activeProfile?.allowNr ?: true
    var allMovies  by remember { mutableStateOf<List<MovieGridItem>>(emptyList()) }
    var posterItems by remember { mutableStateOf<List<PosterItem>>(emptyList()) }
    val openDialogMovieId = movieWithDetails?.id
    val needsFavoritesFilter = selectedCategory == "__favourites__"
    var isSorting by remember { mutableStateOf(false) }
    LaunchedEffect(System.identityHashCode(_allMovies), System.identityHashCode(watchlistIds), needsFavoritesFilter, debouncedQuery, maxAgeRating, allowNr, openDialogMovieId, silentFilterQuery, sortOrder) {
        isSorting = true
        val result = withContext(Dispatchers.Default) {
            val base = if (needsFavoritesFilter) _allMovies.filter { it.id in watchlistIds } else _allMovies
            val ageFiltered = if (maxAgeRating != null || !allowNr) base.filter { isAllowedByAgeRating(it.certification, maxAgeRating, allowNr) || it.id == openDialogMovieId } else base
            val filtered = when {
                silentFilterQuery != null -> ageFiltered.filter { it.name.contains(silentFilterQuery, ignoreCase = true) }
                showSearch && debouncedQuery.isNotBlank() -> ageFiltered.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
                else -> ageFiltered
            }
            when (sortOrder) {
                MovieSortOrder.A_Z             -> filtered.sortedBy { it.name.lowercase() }
                MovieSortOrder.Z_A             -> filtered.sortedByDescending { it.name.lowercase() }
                MovieSortOrder.RATING          -> filtered.sortedByDescending { it.rating?.toDoubleOrNull() ?: -1.0 }
                MovieSortOrder.RECENTLY_ADDED  -> filtered.sortedByDescending { parseReleaseDateSortKey(it.releaseDate) }
                MovieSortOrder.AGE_RATING      -> filtered.sortedBy { movieCertOrder(it.certification) }
            }
        }
        if (result.isEmpty() && allMovies.isNotEmpty() && _allMovies.isEmpty()) {
            kotlinx.coroutines.delay(150)
        }
        allMovies = result
        isSorting = false
    }
    LaunchedEffect(System.identityHashCode(allMovies), System.identityHashCode(watchlistIds), System.identityHashCode(progressItemIds)) {
        posterItems = withContext(Dispatchers.Default) {
            allMovies.map { m ->
                PosterItem(
                    id                = m.id,
                    name              = m.name,
                    posterUrl         = m.posterUrl,
                    isBookmarked      = m.id in watchlistIds,
                    showProgressBadge = m.id in progressItemIds,
                    certification     = m.certification,
                    rating            = m.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: m.rating
                )
            }
        }
    }
    val context = LocalContext.current

    val movies = allMovies

    var gridViewRef by remember { mutableStateOf<PosterGridView?>(null) }
    // Always-current ref for use inside factory lambdas (avoids stale closure capture)
    val currentMovies = remember { mutableStateOf<List<MovieGridItem>>(emptyList()) }
    LaunchedEffect(System.identityHashCode(allMovies)) { currentMovies.value = allMovies }
    // When navigated from My List / Recent / Search "Go to": locate item in filtered grid and focus it.
    // posterItems.size is included as a key because posterItems is computed asynchronously after
    // allMovies changes — the first fire (when allMovies.size changes) may see posterItems still
    // empty, so we guard and let the effect re-fire once posterItems is populated.
    // handledSilentQuery is set AFTER the focus attempt so that a cancellation/re-fire due to a
    // key change still retries rather than returning early.
    var handledSilentQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(silentFilterQuery, allMovies.size, posterItems.size, gridViewRef) {
        val query = silentFilterQuery ?: run { handledSilentQuery = null; return@LaunchedEffect }
        if (query == handledSilentQuery) return@LaunchedEffect
        if (allMovies.isEmpty()) return@LaunchedEffect
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
    LaunchedEffect(silentFilterQuery, allMovies.size) {
        val wasFiltered = prevSilentFilter.value != null
        prevSilentFilter.value = silentFilterQuery
        if (wasFiltered && silentFilterQuery == null) isReloadingAll = true
        if (isReloadingAll && allMovies.size > 1) isReloadingAll = false
    }



    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MovieSortOrder.entries.forEach { option ->
                        val selected = option == sortOrder
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { scope.launch { context.applicationContext.saveMovieSortOrder(option.name) }; showSortDialog = false },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
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
                val uiStyle = LocalUiStyle.current
                // When navigated from Search/My List with a silent filter but raw data hasn't
                // arrived from the DB yet, show a spinner rather than "No movies found".
                if (silentFilterQuery != null && movies.isEmpty() && _allMovies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiStyle == UiStyle.MODERN) {
                    ModernMoviesContent(
                        movies           = movies,
                        progressItemIds  = progressItemIds,
                        selectedCategory = selectedCategory,
                        onMovieClick     = { streamUrl, movieId, name ->
                            isLoadingDetails = true
                            scope.launch {
                                val base = viewModel.repository.getMovieById(movieId)
                                movieWithDetails = base
                                val detailed = base?.let {
                                    viewModel.repository.getMovieDetails(
                                        playlistId = it.playlistId,
                                        vodId      = it.id.removePrefix("${it.playlistId}-")
                                    )
                                }
                                if (detailed != null) movieWithDetails = detailed
                                isLoadingDetails = false
                            }
                        },
                        onMovieLongPress = { movie ->
                            watchlistViewModel.toggleWatchlist(
                                WatchlistEntity(
                                    id        = movie.id,
                                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                    type      = WatchlistType.MOVIE,
                                    name      = movie.name,
                                    posterUrl = movie.posterUrl,
                                    streamUrl = movie.streamUrl
                                ),
                                movie.id in watchlistIds
                            )
                        }
                    )
                } else {

                val nsTheme = LocalNexStreamTheme.current
                val sTheme = nsTheme.sidebar
                val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

                Column(modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
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
                                            null             -> "All"
                                            else             -> selectedCategory
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

                    when {
                        isReloadingAll -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                    Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        (isLoadingVod || (vodTotal > 0 && vodLoaded < vodTotal) ||
                                (playlists.isNotEmpty() && movies.isEmpty())) &&
                                selectedCategory != "__favourites__" && !showSearch &&
                                silentFilterQuery == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        movies.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(when {
                                    selectedCategory == "__favourites__" -> "No movies in your favourites yet"
                                    silentFilterQuery != null -> "No movies found"
                                    showSearch -> "No results found"
                                    else -> "No movies in this category"
                                },
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            AndroidView(
                                factory = { ctx ->
                                    PosterGridView(ctx).also { gridViewRef = it; onGridViewReady(it) }.apply {
                                        setColumnCount(6)
                                        blockFocus() // Block until user explicitly navigates here
                                        callbacks = object : PosterGridCallbacks {
                                            override fun onItemClick(item: PosterItem, index: Int) {
                                                val movie = currentMovies.value.firstOrNull { it.id == item.id } ?: return
                                                val resolvedIndex = currentMovies.value.indexOf(movie)
                                                onItemFocused(if (resolvedIndex >= 0) resolvedIndex else index)
                                                isLoadingDetails = true
                                                scope.launch {
                                                    val base = viewModel.repository.getMovieById(movie.id)
                                                    movieWithDetails = base
                                                    val detailed = viewModel.repository.getMovieDetails(
                                                        playlistId = movie.playlistId,
                                                        vodId = movie.id.removePrefix("${movie.playlistId}-")
                                                    )
                                                    if (detailed != null) movieWithDetails = detailed
                                                    isLoadingDetails = false
                                                }
                                            }
                                            override fun onItemLongClick(item: PosterItem, index: Int) {
                                                val movie = currentMovies.value.firstOrNull { it.id == item.id } ?: return
                                                val isBookmarked = movie.id in watchlistIds
                                                watchlistViewModel.toggleWatchlist(WatchlistEntity(
                                                    id = movie.id,
                                                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                                    type = WatchlistType.MOVIE, name = movie.name,
                                                    posterUrl = movie.posterUrl, streamUrl = movie.streamUrl
                                                ), isBookmarked)
                                                scope.launch { snackbarHostState.showSnackbar(
                                                    if (!isBookmarked) "${movie.name} added to My List"
                                                    else "${movie.name} removed from My List"
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
                        }
                    }
                }
                } // end else (Classic UI)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        app.nexstream.player.ui.components.TvKeyboardSheet(
            visible       = showKeyboard,
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            onDone        = { showKeyboard = false },
            onDismiss     = { showKeyboard = false },
            hint          = "Search movies…",
            modifier      = Modifier.fillMaxSize()
        )
    }

    BackHandler(enabled = showKeyboard) { showKeyboard = false }

    LaunchedEffect(movieWithDetails) { onDialogOpen(movieWithDetails != null) }

    movieWithDetails?.takeIf { !isLoadingDetails }?.let { movie ->
        val isBookmarked = movie.id in watchlistIds
        val resumePositionState by produceState<Long?>(null, movie.id, profileId) {
            value = viewModel.progressRepository.getMoviePosition(profileId, movie.id)
        }
        // Wait until position is known before showing dialog - prevents button flicker
        if (resumePositionState == null) return@let
        val resumePosition = resumePositionState!!
        val isContentRestricted = run {
            val cert       = movie.certification
            val maxAge     = activeProfile?.maxAgeRating
            val allowNrVal = activeProfile?.allowNr ?: true
            when {
                cert == "NR" || cert == null -> !allowNrVal
                maxAge == null               -> false
                else                         -> !isAllowedByAgeRating(cert, maxAge, allowNrVal)
            }
        }
        ModernMovieDetailsDialog(
            movie               = movie,
            isBookmarked        = isBookmarked,
            resumePosition      = resumePosition,
            isContentRestricted = isContentRestricted,
            maxAgeRating        = activeProfile?.maxAgeRating,
            allowNr             = activeProfile?.allowNr ?: true,
            playlistName        = playlists.find { it.id == movie.playlistId }?.let { p ->
                val label = when (p.type) { "XTREAM" -> "Xtream Codes"; "JELLYFIN" -> "Jellyfin"; else -> p.type }
                "$label · ${p.name}"
            },
            onDismiss           = { movieWithDetails = null; isLoadingDetails = false },
            onToggleWatchlist   = {
                watchlistViewModel.toggleWatchlist(WatchlistEntity(id = movie.id,
                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                    type = WatchlistType.MOVIE, name = movie.name, posterUrl = movie.posterUrl, streamUrl = movie.streamUrl), isBookmarked)
            },
            onDownload          = {
                app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
                    context = context,
                    streamUrl = movie.streamUrl,
                    title = movie.name,
                    posterUrl = movie.posterUrl
                )
                movieWithDetails = null
                isLoadingDetails = false
            },
            onPlay              = { startPosition -> movieWithDetails = null; isLoadingDetails = false; onMovieClick(movie.streamUrl, movie.id, startPosition, movie.name) },
            onFetchCertification = { viewModel.fetchCertificationIfMissing(movie.id, movie.name) },
            onFetchOriginalLanguage = { viewModel.fetchOriginalLanguageIfMissing(movie.id, movie.name) },
            whisperManager = viewModel.whisperSubtitleManager,
            onFetchRtData       = { viewModel.fetchRtDataIfMissing(movie.id, movie.name) },
            onFetchTrailerUrl   = { viewModel.fetchTrailerUrl(movie) },
        )
    }
}

// ── Loading placeholder ───────────────────────────────────────────────────────

@Composable
fun MoviesLoadingPlaceholder(loaded: Int, total: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val pct = if (total > 0) loaded.toFloat() / total.toFloat() else 0f
    val animPct by animateFloatAsState(pct, tween(600), label = "vodFill")
    val easeInOutSine = remember { CubicBezierEasing(0.45f, 0f, 0.55f, 1f) }
    val pulseAnim by rememberInfiniteTransition(label = "pulse").animateFloat(0.15f, 1f,
        infiniteRepeatable(tween(1200, easing = easeInOutSine), RepeatMode.Reverse), label = "logoPulse")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NexStreamNLogo(primary, surfaceVariant, if (total > 0) animPct else 1f,
            Modifier.size(160.dp).alpha(if (total > 0) 1f else pulseAnim))
        Spacer(Modifier.height(24.dp))
        Text(if (total > 0 && loaded >= total) "Movies ready" else "Loading movies...",
            style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
        if (total > 0) { Spacer(Modifier.height(6.dp)); Text("$loaded of $total", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant.copy(alpha = 0.6f)) }
    }
}

@Composable
fun NexStreamNLogo(fillColor: Color, unfillColor: Color, fillPercent: Float, modifier: Modifier = Modifier) {
    val pathStrings = remember { listOf("M69.25,52.75c-0.08,6.84-7.57,11.03-13.48,6.82c-4.83,3.38-10.3,1.23-12.42-2.73c-2.24-4.17-0.66-9.88,4.7-11.91c-1.5-4.59,1.05-8.87,4.23-10.34c3.75-1.73,8.1-0.75,10.67,2.88c2.4-1.45,4.94-1.72,7.56-0.72c2.15,0.83,3.66,2.36,4.61,4.47C76.88,45.13,75.3,51.12,69.25,52.75z","M56.56,28.7c-0.59,0.3-0.78,0.58-0.65,1.23c0.1,0.51,0.02,1.06,0.02,1.63c-4.63,0.21-7.73,2.53-9.71,6.42c-0.88-0.67-1.72-1.43-2.67-2.01c-2.75-1.69-5.7-2.06-8.78-1.01c-0.46,0.16-0.74,0.07-1.15-0.22c-1.13-0.79-2.36-1.46-3.53-2.19c-0.24-0.15-0.51-0.34-0.63-0.58c-1.66-3.44-0.58-7.82,2.5-10.09c0.54-0.4,0.78-0.76,0.81-1.48c0.19-3.58,1.91-6.23,5.28-7.53c3.34-1.29,6.41-0.59,9.01,1.91c0.06,0.05,0.11,0.1,0.22,0.2c2.61-2.07,5.48-2.7,8.55-1.5c3.28,1.29,5.14,3.8,5.32,7.34C61.32,24.37,59.73,27.05,56.56,28.7z","M44.27,44.7c-3.19,2.45-4.38,5.83-3.92,9.91c-3.63,0.25-6.41,1.88-8.43,4.81c-0.86-0.21-1.71-0.4-2.54-0.65c-0.18-0.05-0.35-0.27-0.45-0.45c-2.28-3.83-7.74-5.26-11.66-3.07c-0.2,0.11-0.54,0.16-0.74,0.07c-0.82-0.36-1.79-0.36-2.32-1.35c-0.83-1.55-1.35-3.15-1.2-4.9c0.28-3.14,1.78-5.5,4.63-6.97c0.3-0.16,0.62-0.56,0.68-0.89c0.58-3.46,2.93-6.06,6.31-6.9c3.24-0.81,6.68,0.46,8.72,3.22c0.06,0.08,0.12,0.16,0.16,0.23c0.8-0.31,1.57-0.68,2.37-0.91c3.3-0.93,6.77,0.26,8.86,2.98c0.53,0.68,0.76,1.36,0.8,2.28C45.58,43.29,45.25,43.94,44.27,44.7z","M99.33,55.19c-2.27,4.08-7.82,5.81-12.44,2.52c-2.52,2.04-5.35,2.61-8.42,1.5c-2.78-1.01-4.47-3.07-5.24-5.94c2.99-2.21,4.71-5.12,4.65-8.9c-0.06-3.77-1.82-6.65-5-8.83c2.33-0.53,4.39-0.15,6.4,1.01c1.1-2.02,2.78-3.21,4.86-3.86c1.92-0.6,3.84-0.45,5.69,0.34c3.15,1.34,5.93,4.93,4.74,10.06c2.69,0.95,4.57,2.74,5.45,5.49C100.76,50.88,100.49,53.11,99.33,55.19z","M32.42,72.51c-0.18,0.15-0.37,0.28-0.58,0.37c-1.53,0.72-3.07,1.44-4.7,2.19c-6.54-4.93-17.7-4-22.82,3.71c-4.18-1.83-5.94-7.71-1.42-11.35c0.91-0.73,1.23-1.34,1.22-2.52c-0.03-6.45,6.99-10.4,12.55-7.15c0.16,0.09,0.31,0.18,0.5,0.3c1.73-1.88,3.85-2.08,6.09-1.46c2.15,0.6,3.82,1.81,4.33,4.12c0.87,0.13,1.76,0.14,2.58,0.4c2.54,0.78,4.13,2.54,4.58,5.16C35.19,68.77,34.37,70.88,32.42,72.51z","M82.71,30.79c-0.13,0.17-0.37,0.27-0.55,0.4c-1.07,0.76-2.12,1.54-3.21,2.28c-0.23,0.16-0.6,0.3-0.84,0.22c-2.64-0.79-5.2-0.65-7.71,0.5c-0.21,0.1-0.5,0.12-0.73,0.08c-1.95-0.39-3.85-0.26-5.73,0.37c-0.2,0.07-0.53,0.06-0.66-0.06c-1.49-1.39-3.21-2.33-5.2-2.76c-0.18-0.9,0-1.51,0.82-2.09c3.83-2.72,5.31-7.45,3.83-11.9c-0.05-0.15-0.09-0.3-0.13-0.44c2.41-1.63,7.14-1.65,10.04,2.35c1.62-0.94,3.37-1.39,5.25-1.07c2.85,0.49,4.88,2.09,5.9,4.79C84.78,26.08,84.38,28.55,82.71,30.79z","M85.26,73.57c-0.19,0.14-0.58,0.21-0.78,0.11c-6.2-3.18-12.18-2.77-18,1c-0.79-0.46-1.55-0.96-2.35-1.37c-0.8-0.4-1.65-0.72-2.55-1.09c1.39-2.88,1.48-5.84,0.24-8.88c4.19-0.61,7.13-2.84,8.83-6.79c0.98,0.23,1.9,0.53,2.58,1.37c2.15,2.68,4.99,4,8.4,3.91c1.21-0.03,2.43-0.43,3.62-0.73c0.37-0.09,0.58-0.1,0.87,0.18C89.5,64.57,89.09,70.79,85.26,73.57z","M113.04,75.64c-0.79,1.44-1.96,2.46-3.5,3.02c-0.21,0.08-0.64-0.01-0.75-0.17c-4.27-6.2-13.09-8.15-20.1-4.85c-0.16,0.07-0.31,0.14-0.64,0.28c3.1-4.26,3.31-8.49,0.64-12.86c5.13,1.35,9.25-0.15,12.38-4.54c2.22,1.7,3.47,3.92,3.75,6.7c2.54,0.21,4.65,1.22,6.44,2.96C113.89,68.72,114.65,72.71,113.04,75.64z","M59.51,71.64c-0.23,0.44-0.51,0.55-0.98,0.51c-4.26-0.42-8.15,0.57-11.64,3.07c-3.34-2.44-7.09-3.4-11.22-3.11c2.3-4.34,1.6-8.16-1.96-11.57c1.67-2.39,3.92-3.73,6.88-3.77c0.2,0,0.48,0.24,0.58,0.45c1.6,3.3,4.19,5.33,7.8,5.99c2.14,0.39,4.23,0.11,6.19-0.86c0.44-0.22,0.77-0.2,1.2-0.01c0.73,0.32,1.49,0.61,2.26,0.77c0.59,0.13,0.95,0.35,1.19,0.92C60.92,66.62,60.81,69.14,59.51,71.64z","M102.9,76.25c-4.79-2.82-12-2.63-16.81,2.06c-5.22-5.24-14.47-5.15-19.59,0c-5.23-5.26-14.47-5.12-19.61,0.02c-2.73-2.64-6.02-3.9-9.81-3.9c-3.78,0.01-7.06,1.28-9.78,3.91c-5.74-5.79-16.03-4.87-20.27,0.91c-1.78,2.43-2.58,5.07-1.96,8.01c2.29,10.94,4.64,21.88,6.97,32.81c1.52,7.12,3.04,14.25,4.55,21.37c1.49,7,2.97,14,4.45,21c0.53,2.52,1.08,5.04,1.62,7.55h68.08c0.04-0.12,0.06-0.19,0.08-0.26c2.18-10.26,4.36-20.52,6.53-30.78c1.36-6.41,2.71-12.83,4.07-19.25c1.99-9.4,4-18.79,5.99-28.19c0.38-1.79,0.93-3.57,1.07-5.39C108.83,81.65,106.63,78.45,102.9,76.25zM50.11,167.64H37.19c-0.16-1.28-0.32-2.55-0.47-3.82c-0.67-5.65-1.33-11.3-1.99-16.95c-0.59-5.11-1.17-10.23-1.77-15.34c-0.65-5.52-1.31-11.04-1.96-16.56c-0.54-4.65-1.06-9.3-1.61-13.95c-0.65-5.55-1.28-11.1-1.98-16.64c-0.2-1.55,0.5-2.7,1.36-3.82c4.24-5.56,14.27-4.82,17.54,1.3c0.44,0.83,0.66,1.69,0.69,2.65c0.17,5.62,0.4,11.23,0.62,16.85c0.26,6.73,0.52,13.45,0.77,20.18c0.11,2.94,0.21,5.89,0.33,8.83c0.34,8.53,0.69,17.07,1.02,25.6c0.15,3.78,0.28,7.56,0.42,11.33C50.16,167.4,50.13,167.5,50.11,167.64zM85.81,86.1c-0.56,4.33-1.03,8.68-1.54,13.02c-0.55,4.65-1.1,9.3-1.65,13.95c-0.49,4.19-0.97,8.37-1.45,12.56c-0.54,4.62-1.09,9.25-1.63,13.87c-0.5,4.27-0.98,8.53-1.48,12.8c-0.54,4.62-1.08,9.25-1.64,13.87c-0.18,1.5-0.21,1.5-1.72,1.5H63.24c0.11-2.79,0.21-5.45,0.31-8.11c0.24-6.24,0.47-12.47,0.71-18.71c0.26-6.83,0.54-13.66,0.8-20.49c0.3-7.92,0.59-15.84,0.9-23.75c0.15-3.99,0.36-7.97,0.46-11.95c0.04-1.75,0.72-3.15,1.81-4.42c4.13-4.8,12.76-4.47,16.61,0.53C86.12,82.44,86.05,84.19,85.81,86.1z") }
    val paths = remember(pathStrings) { pathStrings.map { PathParser().parsePathString(it).toPath() } }
    val b = remember(paths) { paths.fold(paths.first().getBounds()) { acc, p -> val pb = p.getBounds(); androidx.compose.ui.geometry.Rect(minOf(acc.left,pb.left),minOf(acc.top,pb.top),maxOf(acc.right,pb.right),maxOf(acc.bottom,pb.bottom)) } }
    androidx.compose.foundation.Canvas(modifier = modifier.clipToBounds()) {
        val s = minOf(size.width/b.width, size.height/b.height)
        val ox = (size.width-b.width*s)/2f-b.left*s; val oy = (size.height-b.height*s)/2f-b.top*s
        withTransform({ translate(ox,oy); scale(s,s,pivot=androidx.compose.ui.geometry.Offset.Zero) }) { paths.forEach { drawPath(it,unfillColor) } }
        clipRect(0f,size.height-size.height*fillPercent,size.width,size.height) { withTransform({ translate(ox,oy); scale(s,s,pivot=androidx.compose.ui.geometry.Offset.Zero) }) { paths.forEach { drawPath(it,fillColor) } } }
    }
}

// ── Movie card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(
    movie: MovieGridItem,
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
                        .data(movie.posterUrl)
                        .size(180, 252)  // 6 cols on 1080p ≈ 165dp each
                        .crossfade(false)
                        .memoryCacheKey(movie.posterUrl)
                        .diskCacheKey(movie.posterUrl)
                        .build(),
                    contentDescription = movie.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isFocused) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.07f)))
                // Continue badge — top left (play icon only)
                if (movie.lastPlayedPosition > 0L) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Icon(Icons.Default.PlayCircle, null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(10.dp))
                    }
                }
                // Certification badge — top right
                if (movie.certification != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Text(
                            movie.certification,
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
                    Text(movie.name, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(end = 28.dp))
                    // My List icon — bottom right above title
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

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class MoviesViewModel @Inject constructor(
    val repository: PlaylistRepository,
    val progressRepository: WatchProgressRepository,
    val whisperSubtitleManager: WhisperSubtitleManager
) : ViewModel() {
    @Inject lateinit var profileManager: ProfileManager
    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vodLoadedCount: StateFlow<Int> = repository.vodLoadedCount
    val vodTotalCount: StateFlow<Int> = repository.vodTotalCount

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allMovies: StateFlow<List<MovieGridItem>> = playlists.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { repository.getMovieGridItems(it.id) }) { arrays -> arrays.flatMap { it } }
            .debounce(150) // Coalesce rapid batch inserts during fetch
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategories(): Flow<List<String>> = profileManager.activeProfile.flatMapLatest { profile ->
        if (profile == null) {
            repository.getMovieCategories()
        } else {
            combine(repository.getMovieCategories(), repository.getBlockedCategoriesFlow(profile.id, "MOVIE")) { cats, blocked ->
                cats.filter { it !in blocked }
            }
        }
    }
    fun getMoviesByCategory(category: String?): Flow<List<MovieGridItem>> =
        if (category == null) allMovies
        else playlists.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { repository.getMovieGridItemsByCategory(it.id, category) }) { arrays ->
                arrays.flatMap { it }
            }
            // No debounce: data is already in DB at category-switch time; combine waits for all
            // playlist flows before emitting, so no rapid-fire updates occur
        }
    fun getProgressItemIds(profileId: String) = progressRepository.getProgressItemIds(profileId)

    fun getFilteredCategories(allCategories: List<String>, type: String): List<String> {
        val activeProfile = profileManager.activeProfile.value ?: return allCategories
        val blocked = profileManager.getBlockedCategoriesCached(activeProfile.id, type)
        return allCategories.filter { it !in blocked }
    }

    suspend fun fetchRtDataIfMissing(movieId: String, movieName: String) =
        repository.fetchRtDataForMovieSingle(movieId, movieName)

    suspend fun fetchCertificationIfMissing(movieId: String, movieName: String): String? =
        repository.fetchCertificationForMovieSingle(movieId, movieName)

    suspend fun fetchOriginalLanguageIfMissing(movieId: String, movieName: String): String? =
        repository.fetchOriginalLanguageForMovieSingle(movieId, movieName)

    suspend fun fetchTrailerUrl(movie: app.nexstream.player.data.local.entity.MovieEntity): String? {
        if (!movie.trailerUrl.isNullOrBlank()) return movie.trailerUrl
        return repository.fetchTrailerUrlForMovie(movie.name)
    }

}