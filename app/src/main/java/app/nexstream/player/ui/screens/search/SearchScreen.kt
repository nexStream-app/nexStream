package app.nexstream.player.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import kotlinx.coroutines.launch
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.screens.movies.ModernMovieDetailsDialog
import app.nexstream.player.ui.screens.series.SeriesDetailsDialog
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import androidx.compose.material3.HorizontalDivider

@Composable
fun SearchScreen(
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (movie: MovieEntity) -> Unit,
    onSeriesClick: (series: SeriesEntity) -> Unit,
    onGoToMovie: (name: String) -> Unit = {},
    onGoToEpgForChannel: (channelName: String) -> Unit = {},
    selectedType: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onRequestSidebarFocus: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val personDetail by viewModel.personDetail.collectAsState()
    val personDetailLoading by viewModel.personDetailLoading.collectAsState()
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchMovieDialog   by remember { mutableStateOf<MovieEntity?>(null) }
    var searchMovieUpdated  by remember { mutableStateOf<MovieEntity?>(null) }
    var searchMovieReady    by remember { mutableStateOf(false) }
    var searchSeriesDialog   by remember { mutableStateOf<SeriesEntity?>(null) }
    var searchSeriesUpdated  by remember { mutableStateOf<SeriesEntity?>(null) }
    var searchSeriesEpisodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var searchSeriesSeasons  by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searchSeriesLoading  by remember { mutableStateOf(false) }

    LaunchedEffect(searchMovieDialog) {
        val m = searchMovieDialog ?: run { searchMovieUpdated = null; searchMovieReady = false; return@LaunchedEffect }
        searchMovieReady = false
        searchMovieUpdated = null
        val detailed = viewModel.loadMovieDetails(m)
        if (detailed != null) searchMovieUpdated = detailed
        searchMovieReady = true
    }

    LaunchedEffect(searchSeriesDialog) {
        val s = searchSeriesDialog ?: run { searchSeriesUpdated = null; return@LaunchedEffect }
        searchSeriesLoading  = true
        searchSeriesEpisodes = emptyList()
        searchSeriesSeasons  = emptyList()
        searchSeriesUpdated  = null
        val resolvedId = viewModel.resolveSeriesId(s.id)
        val localEps = viewModel.getLocalEpisodes(resolvedId)
        if (localEps.isNotEmpty()) {
            searchSeriesEpisodes = localEps
            searchSeriesSeasons  = viewModel.getLocalSeasons(resolvedId)
        } else {
            val (updated, fetchedEps) = viewModel.loadSeriesDetails(s)
            searchSeriesEpisodes = fetchedEps
            searchSeriesSeasons  = fetchedEps.map { it.seasonNum }.distinct().sorted()
            if (updated != null) searchSeriesUpdated = updated
        }
        searchSeriesLoading = false
    }

    val wrappedChannelClick: (String, String) -> Unit = { _, name ->
        onGoToEpgForChannel(name)
    }
    val handleProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = { prog ->
        scope.launch {
            val ch = viewModel.getChannelByEpgId(prog.channelId)
            if (ch != null) onGoToEpgForChannel(ch.name)
        }
    }
    val wrappedMovieClick: (MovieEntity) -> Unit = { movie ->
        searchMovieDialog = movie
    }
    val wrappedSeriesClick: (SeriesEntity) -> Unit = { series ->
        searchSeriesDialog = series
    }
    val handlePersonClick: (PersonResult) -> Unit = { person ->
        if (results.people.size == 1 && results.people.first().name == person.name) {
            viewModel.selectPerson(person)
        } else {
            viewModel.onQueryChange(person.name)
        }
    }
    val handleCastMovieClick: (MovieEntity) -> Unit  = { searchMovieDialog = it }
    val handleCastSeriesClick: (SeriesEntity) -> Unit = { searchSeriesDialog = it }

    val searchHint = when (selectedType) {
        "Live TV" -> "Search channels…"
        "Movies"  -> "Search movies…"
        "Series"  -> "Search series…"
        "People"  -> "Search people…"
        else      -> "Search channels, movies, series, people…"
    }

    var showKeyboard by remember { mutableStateOf(false) }
    val searchFocusRequester = firstItemFocusRequester ?: remember { FocusRequester() }

    BackHandler(enabled = showKeyboard) {
        showKeyboard = false
        try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val uiStyle = LocalUiStyle.current
        if (uiStyle == UiStyle.MODERN) {
            ModernSearchContent(
                query                  = query,
                results                = results,
                selectedType           = selectedType,
                searchHint             = searchHint,
                searchFocusRequester   = searchFocusRequester,
                onOpenKeyboard         = { showKeyboard = true },
                onClearQuery           = viewModel::clearQuery,
                onChannelClick         = wrappedChannelClick,
                onMovieClick           = wrappedMovieClick,
                onSeriesClick          = wrappedSeriesClick,
                onPersonClick          = handlePersonClick,
                onCastMovieClick       = handleCastMovieClick,
                onCastSeriesClick      = handleCastSeriesClick,
                onProgrammeClick       = handleProgrammeClick,
                onRequestSidebarFocus  = onRequestSidebarFocus,
            )
        } else {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ───────────────────────────────────────────────────────
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

            // ── Search bar ───────────────────────────────────────────────────
            var searchBarFocused by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { searchBarFocused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { showKeyboard = true; true }
                            Key.DirectionLeft -> { onRequestSidebarFocus(); true }
                            else -> false
                        }
                    }
                    .clickable { showKeyboard = true },
                shape = RoundedCornerShape(12.dp),
                color = if (searchBarFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (searchBarFocused) 4.dp else 1.dp,
                shadowElevation = if (searchBarFocused) 4.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchBarFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = query.ifEmpty { searchHint },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            query.isEmpty()  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            searchBarFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                            else             -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.clearQuery() }
                        )
                    }
                }
            }

            // ── Results ──────────────────────────────────────────────────────
            if (query.length < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            when (selectedType) {
                                "People" -> "Search by director or cast member name"
                                else     -> "Type to search"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (results.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text(
                            "Searching for \"$query\"…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedType == null) {
                // ── All: LazyColumn with horizontal LazyRow sections ──────────
                val personImages by viewModel.personImageUrls.collectAsState()
                SearchAllResults(
                    results = results,
                    firstItemFocusRequester = firstItemFocusRequester,
                    personImages = personImages,
                    onLoadPersonImage = viewModel::loadPersonImage,
                    onChannelClick = wrappedChannelClick,
                    onMovieClick = wrappedMovieClick,
                    onSeriesClick = wrappedSeriesClick,
                    onPersonClick = handlePersonClick,
                    onCastMovieClick = handleCastMovieClick,
                    onCastSeriesClick = handleCastSeriesClick,
                    onProgrammeClick = handleProgrammeClick
                )
            } else {
                // ── Specific panel: card grid ─────────────────────────────────
                SearchPanelGrid(
                    results = results,
                    selectedType = selectedType,
                    firstItemFocusRequester = firstItemFocusRequester,
                    onChannelClick = wrappedChannelClick,
                    onMovieClick = wrappedMovieClick,
                    onSeriesClick = wrappedSeriesClick,
                    query = query,
                    onCastMovieClick = handleCastMovieClick,
                    onCastSeriesClick = handleCastSeriesClick,
                    onProgrammeClick = handleProgrammeClick
                )
            }
        }
        } // end else (Classic UI)

        if (searchMovieDialog != null && searchMovieReady) {
            val movie = searchMovieDialog!!
            val displayMovie = searchMovieUpdated ?: movie
            val isMovieBookmarked = movie.id in watchlistIds
            ModernMovieDetailsDialog(
                movie                   = displayMovie,
                isBookmarked            = isMovieBookmarked,
                onDismiss               = { searchMovieDialog = null },
                onPlay                  = { _ -> onMovieClick(displayMovie); searchMovieDialog = null },
                onToggleWatchlist       = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id        = movie.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type      = WatchlistType.MOVIE,
                            name      = movie.name,
                            posterUrl = movie.posterUrl,
                            streamUrl = movie.streamUrl
                        ),
                        isMovieBookmarked
                    )
                },
                onFetchCertification    = { viewModel.fetchMovieCertification(displayMovie.id, displayMovie.name) },
                onFetchOriginalLanguage = { viewModel.fetchMovieOriginalLanguage(displayMovie.id, displayMovie.name) },
                onFetchRtData           = { viewModel.fetchMovieRtData(displayMovie.id, displayMovie.name) },
                onFetchTrailerUrl       = { viewModel.fetchMovieTrailerUrl(displayMovie) },
            )
        }

        searchSeriesDialog?.let { series ->
            val displaySeries = searchSeriesUpdated ?: series
            val isSeriesBookmarked = series.id in watchlistIds
            SeriesDetailsDialog(
                series                  = displaySeries,
                episodes                = searchSeriesEpisodes,
                seasons                 = searchSeriesSeasons,
                isLoading               = searchSeriesLoading,
                isBookmarked            = isSeriesBookmarked,
                onDismiss               = { searchSeriesDialog = null },
                onToggleWatchlist       = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id        = series.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type      = WatchlistType.SERIES,
                            name      = series.name,
                            posterUrl = series.posterUrl,
                            streamUrl = null
                        ),
                        isSeriesBookmarked
                    )
                },
                onFetchCertification    = { viewModel.fetchSeriesCertification(displaySeries.id, displaySeries.name) },
                onFetchOriginalLanguage = { viewModel.fetchSeriesOriginalLanguage(displaySeries.id, displaySeries.name) },
                onFetchTrailerUrl       = { viewModel.fetchSeriesTrailerUrl(displaySeries.name) },
                onGoToSeries            = { onSeriesClick(displaySeries); searchSeriesDialog = null },
                onPlayEpisode           = { streamUrl, _, _, _, seriesName, _, _, episodeName ->
                    onChannelClick(streamUrl, "$seriesName — $episodeName")
                    searchSeriesDialog = null
                }
            )
        }

        if (selectedPerson != null) {
            PeopleDetailsDialog(
                person    = selectedPerson!!,
                detail    = personDetail,
                isLoading = personDetailLoading,
                onDismiss = { viewModel.selectPerson(null) },
                onCreditClick = { movie, series ->
                    if (movie != null) searchMovieDialog = movie
                    else if (series != null) searchSeriesDialog = series
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
        TvKeyboardSheet(
            visible       = showKeyboard,
            value         = query,
            onValueChange = viewModel::onQueryChange,
            onDone        = {
                showKeyboard = false
                try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
            },
            onDismiss     = {
                showKeyboard = false
                try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
            },
            hint          = searchHint,
            modifier      = Modifier.fillMaxSize()
        )
    }
}

// ── All panel: horizontal LazyRow sections ─────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchAllResults(
    results: SearchResults,
    firstItemFocusRequester: FocusRequester?,
    personImages: Map<String, String?>,
    onLoadPersonImage: (String) -> Unit,
    onChannelClick: (String, String) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onPersonClick: (PersonResult) -> Unit = {},
    onCastMovieClick: (MovieEntity) -> Unit = {},
    onCastSeriesClick: (SeriesEntity) -> Unit = {},
    onProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = {}
) {
    val hasAny = results.channels.isNotEmpty() || results.programmes.isNotEmpty() ||
                 results.movies.isNotEmpty() || results.series.isNotEmpty() ||
                 results.people.isNotEmpty() ||
                 results.peopleMovies.isNotEmpty() || results.peopleSeries.isNotEmpty()

    if (!hasAny) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }
    val firstSection = when {
        results.channels.isNotEmpty()    -> "channels"
        results.programmes.isNotEmpty()  -> "programmes"
        results.movies.isNotEmpty()      -> "movies"
        results.series.isNotEmpty()      -> "series"
        results.people.isNotEmpty()      -> "people"
        results.peopleMovies.isNotEmpty()-> "pmovies"
        else                             -> "pseries"
    }
    val bgColor = MaterialTheme.colorScheme.background

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
    ) {
        if (results.channels.isNotEmpty()) {
            stickyHeader(key = "header_channels") {
                SearchSectionHeader("Channels (${results.channels.size})", bgColor, isFirst = firstSection == "channels")
            }
            item(key = "section_channels") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.channels, key = { _, ch -> "sch_${ch.id}" }) { idx, ch ->
                        ContentCard(
                            name = ch.name,
                            posterUrl = ch.logoUrl,
                            defaultIcon = Icons.Default.Tv,
                            focusRequester = if (firstSection == "channels" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onChannelClick(ch.streamUrl, ch.name) }
                        )
                    }
                }
            }
        }
        if (results.programmes.isNotEmpty()) {
            stickyHeader(key = "header_programmes") {
                SearchSectionHeader("Programmes (${results.programmes.size})", bgColor, isFirst = firstSection == "programmes")
            }
            item(key = "section_programmes") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.programmes, key = { _, p -> "sprog_${p.id}" }) { idx, prog ->
                        ContentCard(
                            name = prog.title,
                            posterUrl = prog.icon,
                            defaultIcon = Icons.Default.CalendarToday,
                            focusRequester = if (firstSection == "programmes" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onProgrammeClick(prog) }
                        )
                    }
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            stickyHeader(key = "header_movies") {
                SearchSectionHeader("Movies (${results.movies.size})", bgColor, isFirst = firstSection == "movies")
            }
            item(key = "section_movies") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.movies, key = { _, m -> "smov_${m.id}" }) { idx, movie ->
                        ContentCard(
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            defaultIcon = Icons.Default.Movie,
                            focusRequester = if (firstSection == "movies" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
            }
        }
        if (results.series.isNotEmpty()) {
            stickyHeader(key = "header_series") {
                SearchSectionHeader("Series (${results.series.size})", bgColor, isFirst = firstSection == "series")
            }
            item(key = "section_series") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.series, key = { _, s -> "sser_${s.id}" }) { idx, s ->
                        ContentCard(
                            name = s.name,
                            posterUrl = s.posterUrl,
                            badge = if (s.seasonCount > 0) "${s.seasonCount}S" else null,
                            defaultIcon = Icons.Default.VideoLibrary,
                            focusRequester = if (firstSection == "series" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onSeriesClick(s) }
                        )
                    }
                }
            }
        }
        if (results.people.isNotEmpty()) {
            stickyHeader(key = "header_people") {
                SearchSectionHeader("People (${results.people.size})", bgColor, isFirst = firstSection == "people")
            }
            item(key = "section_people") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.people, key = { _, p -> "sp_${p.name}" }) { idx, person ->
                        LaunchedEffect(person.name) { onLoadPersonImage(person.name) }
                        ContentCard(
                            name = person.name,
                            posterUrl = personImages[person.name] ?: person.imageUrl,
                            defaultIcon = Icons.Default.Person,
                            focusRequester = if (firstSection == "people" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onPersonClick(person) }
                        )
                    }
                }
            }
        }
        if (results.peopleMovies.isNotEmpty()) {
            stickyHeader(key = "header_pmovies") {
                SearchSectionHeader("Movies by cast/director (${results.peopleMovies.size})", bgColor, isFirst = firstSection == "pmovies")
            }
            item(key = "section_pmovies") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.peopleMovies, key = { _, m -> "spm_${m.id}" }) { idx, movie ->
                        ContentCard(
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            defaultIcon = Icons.Default.Movie,
                            focusRequester = if (firstSection == "pmovies" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onCastMovieClick(movie) }
                        )
                    }
                }
            }
        }
        if (results.peopleSeries.isNotEmpty()) {
            stickyHeader(key = "header_pseries") {
                SearchSectionHeader("Series by cast/director (${results.peopleSeries.size})", bgColor, isFirst = firstSection == "pseries")
            }
            item(key = "section_pseries") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.peopleSeries, key = { _, s -> "sps_${s.id}" }) { idx, s ->
                        ContentCard(
                            name = s.name,
                            posterUrl = s.posterUrl,
                            defaultIcon = Icons.Default.VideoLibrary,
                            focusRequester = if (firstSection == "pseries" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onCastSeriesClick(s) }
                        )
                    }
                }
            }
        }
    }
}

// ── Specific panel: vertical card grid ────────────────────────────────────────

@Composable
private fun SearchPanelGrid(
    results: SearchResults,
    selectedType: String,
    firstItemFocusRequester: FocusRequester?,
    onChannelClick: (String, String) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    query: String,
    onCastMovieClick: (MovieEntity) -> Unit = {},
    onCastSeriesClick: (SeriesEntity) -> Unit = {},
    onProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = {}
) {
    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }

    // Build flat item list for the active panel
    data class GridItem(
        val id: String,
        val name: String,
        val posterUrl: String?,
        val badge: String?,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val onClick: () -> Unit
    )

    val gridItems: List<GridItem> = when (selectedType) {
            "Live TV" -> results.channels.map { ch ->
                GridItem(ch.id, ch.name, ch.logoUrl, null, Icons.Default.Tv) { onChannelClick(ch.streamUrl, ch.name) }
            } + results.programmes.map { prog ->
                GridItem(prog.id, prog.title, prog.icon, null, Icons.Default.CalendarToday) {
                    onProgrammeClick(prog)
                }
            }
            "Movies" -> results.movies.map { m ->
                GridItem(m.id, m.name, m.posterUrl, null, Icons.Default.Movie) { onMovieClick(m) }
            }
            "Series" -> results.series.map { s ->
                GridItem(s.id, s.name, s.posterUrl, if (s.seasonCount > 0) "${s.seasonCount}S" else null, Icons.Default.VideoLibrary) { onSeriesClick(s) }
            }
            "People" -> results.peopleMovies.map { m ->
                GridItem("pm_${m.id}", m.name, m.posterUrl, "MOVIE", Icons.Default.Movie) { onCastMovieClick(m) }
            } + results.peopleSeries.map { s ->
                GridItem("ps_${s.id}", s.name, s.posterUrl, "SERIES", Icons.Default.VideoLibrary) { onCastSeriesClick(s) }
            }
        else -> emptyList()
    }

    if (gridItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when (selectedType) {
                    "People" -> "Search by director or cast member name"
                    else     -> "No results for \"$query\""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        gridItemsIndexed(gridItems, key = { _, gi -> "pg_${gi.id}" }) { idx, gi ->
            ContentCard(
                name = gi.name,
                posterUrl = gi.posterUrl,
                badge = gi.badge,
                defaultIcon = gi.icon,
                focusRequester = if (idx == 0) firstFR else null,
                onFocused = {},
                onClick = gi.onClick
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun SearchSectionHeader(
    title: String,
    bgColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    isFirst: Boolean = false,
) {
    val nsTheme = LocalNexStreamTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(top = if (isFirst) 4.dp else 20.dp, bottom = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = nsTheme.sidebar.categoryText,
        )
    }
}
