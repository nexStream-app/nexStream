package app.nexstream.player.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.ui.screens.watchlist.ChannelDetailsDialog
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import kotlinx.coroutines.launch
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.screens.movies.ModernMovieDetailsDialog
import app.nexstream.player.ui.screens.series.SeriesDetailsDialog
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import androidx.compose.material3.HorizontalDivider
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var searchSeriesEpisodes    by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var searchSeriesSeasons     by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searchSeriesLoading     by remember { mutableStateOf(false) }
    var searchSeriesProgressMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var searchMovieResumePos    by remember { mutableStateOf(0L) }

    data class ProgramDialogState(
        val channel: ChannelEntity,
        val currentProgram: ProgramEntity?,
        val nextProgram: ProgramEntity?
    )
    var programDialogState by remember { mutableStateOf<ProgramDialogState?>(null) }

    LaunchedEffect(searchMovieDialog) {
        val m = searchMovieDialog ?: run { searchMovieUpdated = null; searchMovieReady = false; searchMovieResumePos = 0L; return@LaunchedEffect }
        searchMovieReady = false
        searchMovieUpdated = null
        searchMovieResumePos = watchlistViewModel.getMoviePosition(m.id)
        val detailed = viewModel.loadMovieDetails(m)
        if (detailed != null) searchMovieUpdated = detailed
        searchMovieReady = true
    }

    LaunchedEffect(searchSeriesDialog) {
        val s = searchSeriesDialog ?: run { searchSeriesUpdated = null; searchSeriesProgressMap = emptyMap(); return@LaunchedEffect }
        searchSeriesLoading     = true
        searchSeriesEpisodes    = emptyList()
        searchSeriesSeasons     = emptyList()
        searchSeriesUpdated     = null
        searchSeriesProgressMap = watchlistViewModel.getEpisodeProgressMap(s.id)
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

    val wrappedChannelClick: (String, String) -> Unit = { streamUrl, _ ->
        val ch = results.channels.find { it.streamUrl == streamUrl }
        if (ch != null) {
            scope.launch {
                val epgId = ch.epgChannelId ?: ch.id
                val current = viewModel.getCurrentProgram(epgId)
                val next    = viewModel.getNextProgram(epgId)
                programDialogState = ProgramDialogState(ch, current, next)
            }
        }
    }
    val handleProgrammeClick: (ProgramEntity) -> Unit = { prog ->
        scope.launch {
            val ch = viewModel.getChannelByEpgId(prog.channelId)
            if (ch != null) {
                val epgId = ch.epgChannelId ?: ch.id
                val current = viewModel.getCurrentProgram(epgId)
                val next    = viewModel.getNextProgram(epgId)
                programDialogState = ProgramDialogState(ch, current, next)
            }
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
                    onPersonClick = handlePersonClick,
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
                resumePosition          = searchMovieResumePos,
                isBookmarked            = isMovieBookmarked,
                onDismiss               = { searchMovieDialog = null },
                onPlay                  = { startPos -> onMovieClick(displayMovie); searchMovieDialog = null },
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
                series             = displaySeries,
                episodes           = searchSeriesEpisodes,
                seasons            = searchSeriesSeasons,
                episodeProgressMap = searchSeriesProgressMap,
                isLoading          = searchSeriesLoading,
                isBookmarked       = isSeriesBookmarked,
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

        programDialogState?.let { state ->
            val isChannelBookmarked = state.channel.id in watchlistIds
            ChannelDetailsDialog(
                channel           = state.channel,
                currentProgram    = state.currentProgram,
                nextProgram       = state.nextProgram,
                isBookmarked      = isChannelBookmarked,
                onDismiss         = { programDialogState = null },
                onWatch           = { onChannelClick(state.channel.streamUrl, state.channel.name); programDialogState = null },
                onToggleWatchlist = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id        = state.channel.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type      = WatchlistType.CHANNEL,
                            name      = state.channel.name,
                            posterUrl = state.channel.logoUrl,
                            streamUrl = state.channel.streamUrl
                        ),
                        isChannelBookmarked
                    )
                },
                onGoToEpg         = { onGoToEpgForChannel(state.channel.name); programDialogState = null }
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
        results.peopleMovies.isNotEmpty() || results.peopleSeries.isNotEmpty() -> "pcast"
        else                             -> ""
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
        if (results.peopleMovies.isNotEmpty() || results.peopleSeries.isNotEmpty()) {
            val castTotal = results.peopleMovies.size + results.peopleSeries.size
            stickyHeader(key = "header_pcast") {
                SearchSectionHeader("By cast/director ($castTotal)", bgColor, isFirst = firstSection == "pcast")
            }
            item(key = "section_pcast") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    itemsIndexed(results.peopleMovies, key = { _, m -> "spm_${m.id}" }) { idx, movie ->
                        ContentCard(
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            defaultIcon = Icons.Default.Movie,
                            focusRequester = if (firstSection == "pcast" && idx == 0) firstFR else null,
                            onFocused = {},
                            onClick = { onCastMovieClick(movie) }
                        )
                    }
                    itemsIndexed(results.peopleSeries, key = { _, s -> "sps_${s.id}" }) { idx, s ->
                        ContentCard(
                            name = s.name,
                            posterUrl = s.posterUrl,
                            defaultIcon = Icons.Default.VideoLibrary,
                            focusRequester = if (firstSection == "pcast" && results.peopleMovies.isEmpty() && idx == 0) firstFR else null,
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
    onPersonClick: (PersonResult) -> Unit = {},
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
            "Movies" -> (results.movies + results.peopleMovies).distinctBy { it.id }.map { m ->
                GridItem(m.id, m.name, m.posterUrl, null, Icons.Default.Movie) { onMovieClick(m) }
            }
            "Series" -> (results.series + results.peopleSeries).distinctBy { it.id }.map { s ->
                GridItem(s.id, s.name, s.posterUrl, if (s.seasonCount > 0) "${s.seasonCount}S" else null, Icons.Default.VideoLibrary) { onSeriesClick(s) }
            }
            "People" -> results.people.map { p ->
                GridItem("p_${p.name}", p.name, p.imageUrl, null, Icons.Default.Person) { onPersonClick(p) }
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

// ── Programme channel dialog ───────────────────────────────────────────────────

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun fmtTime(ms: Long) = timeFormat.format(Date(ms))

@Composable
private fun SearchChannelProgramDialog(
    channel: ChannelEntity,
    currentProgram: ProgramEntity?,
    nextProgram: ProgramEntity?,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onGoToEpg: () -> Unit
) {
    val accent = LocalNsAccent.current
    // Button order: 0=Close 1=EPG 2=Watch
    var selectedButton by remember { mutableIntStateOf(2) }
    val dialogFocus    = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape    = RoundedCornerShape(16.dp),
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + 3) % 3; true }
                            Key.DirectionRight -> { selectedButton = (selectedButton + 1) % 3; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                when (selectedButton) { 0 -> onDismiss(); 1 -> onGoToEpg(); 2 -> onWatch() }
                                true
                            }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
            ) {
                // Backdrop: channel logo
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model              = channel.logoUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxWidth().height(180.dp),
                        contentScale       = ContentScale.Fit,
                        alignment          = Alignment.Center
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF1A1A2E)))
                }

                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp).background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.15f),
                                0.6f to Color.Black.copy(alpha = 0.75f),
                                1.0f to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    )
                )

                // Close button top-right
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedButton == 0) accent else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.clickable(onClick = onDismiss)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(13.dp), tint = Color.White)
                            Text("Close", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }

                // Bottom content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Channel name
                    Text(
                        text       = channel.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )

                    // Current programme
                    if (currentProgram != null) {
                        ProgramRow(
                            label   = "Now",
                            program = currentProgram,
                            accent  = accent
                        )
                    }

                    // Up next
                    if (nextProgram != null) {
                        ProgramRow(
                            label   = "Next",
                            program = nextProgram,
                            accent  = null
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // EPG button
                        ProgramDialogPill(
                            icon       = Icons.Default.CalendarToday,
                            label      = "Go to EPG",
                            isSelected = selectedButton == 1,
                            accent     = accent,
                            onClick    = onGoToEpg
                        )
                        Spacer(Modifier.weight(1f))
                        // Watch Live
                        ProgramDialogPill(
                            icon       = Icons.Default.PlayArrow,
                            label      = "Watch Live",
                            isSelected = selectedButton == 2,
                            accent     = accent,
                            onClick    = onWatch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(label: String, program: ProgramEntity, accent: androidx.compose.ui.graphics.Color?) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = (accent ?: Color.White.copy(alpha = 0.2f)).let { if (accent != null) it.copy(alpha = 0.85f) else it }
        ) {
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text     = "${fmtTime(program.startTime)}–${fmtTime(program.endTime)}  ${program.title}",
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White.copy(alpha = if (accent != null) 1f else 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProgramDialogPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        shape    = RoundedCornerShape(50.dp),
        color    = if (isSelected) accent else Color.White.copy(alpha = 0.15f),
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color.White)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Medium)
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
