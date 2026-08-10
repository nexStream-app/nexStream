package app.nexstream.player.ui.screens.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.ui.components.ContentActionDialog
import app.nexstream.player.ui.screens.movies.ModernMovieDetailsDialog
import app.nexstream.player.ui.screens.series.SeriesDetailsDialog
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

data class PickItem(
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val overview: String,
    val mediaType: String,    // "movie" or "tv"
    val seedTitle: String     // what you watched that generated this pick
) {
    val posterUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
}

data class PickGroup(val seedTitle: String, val items: List<PickItem>)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class PicksViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val profileManager: ProfileManager,
    private val httpClient: OkHttpClient
) : ViewModel() {

    private val tmdbToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJhYzA2NjBlNjM2MGVlNDE0NmQyNDA3MzUyOTQ2ZmRkYiIsIm5iZiI6MTc3Njg3MzQ2OS4wNjA5OTk5LCJzdWIiOiI2OWU4ZWZmZDQyM2ZhZDJlYzhlMGNmY2QiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.1gbVrIUZezfVmRoFk692A__HsaO9GahlPeMT6h2JBKo"

    private val _groups  = MutableStateFlow<List<PickGroup>>(emptyList())
    private val _loading = MutableStateFlow(false)

    val groups:  StateFlow<List<PickGroup>> = _groups.asStateFlow()
    val loading: StateFlow<Boolean>         = _loading.asStateFlow()

    val recentlyWatched: StateFlow<List<RecentlyWatchedEntity>> =
        repository.getRecentlyWatched()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cached once per ViewModel lifetime — playlist rarely changes mid-session
    private var cachedAvailableNames: Set<String>? = null
    // Word index: significant word → set of normalized titles containing that word
    private var cachedWordIndex: Map<String, Set<String>>? = null
    // Track which seeds were last fetched — skip re-fetch if seeds and results haven't changed
    private var lastLoadedSeedIds: List<String> = emptyList()

    init {
        viewModelScope.launch { repository.pruneStaleAndBlockedRecentItems() }
    }

    fun loadPicks(seeds: List<RecentlyWatchedEntity>) {
        if (seeds.isEmpty()) return
        val dedupedSeeds = seeds
            .filter { it.type == RecentlyWatchedType.MOVIE || it.type == RecentlyWatchedType.EPISODE }
            .distinctBy { it.name.lowercase().trim() }
            .take(6)
        if (dedupedSeeds.isEmpty()) return

        // Skip re-fetch if seeds haven't changed and we already have results
        val seedIds = dedupedSeeds.map { it.id }
        if (seedIds == lastLoadedSeedIds && _groups.value.isNotEmpty()) return
        lastLoadedSeedIds = seedIds

        viewModelScope.launch {
            _loading.value = true

            // Fetch names once; build word index for O(1) candidate lookup during matching
            val availableNames = cachedAvailableNames ?: withContext(Dispatchers.IO) {
                val movies = repository.getAllMovieNames().map { normContent(it) }.toSet()
                val series = repository.getAllSeriesNames().map { normContent(it) }.toSet()
                (movies + series).also { cachedAvailableNames = it }
            }
            val wordIndex = cachedWordIndex ?: buildWordIndex(availableNames).also { cachedWordIndex = it }

            // Process all seeds concurrently; publish groups as each result arrives
            val mutex = Mutex()
            val orderedResults = mutableListOf<Pair<Int, PickGroup>>()
            dedupedSeeds.mapIndexed { idx, seed ->
                async(Dispatchers.IO) {
                    val picks = fetchRecommendations(seed, wordIndex)
                    if (picks.isNotEmpty()) {
                        mutex.withLock {
                            orderedResults.add(idx to PickGroup(cleanTitle(seed.name), picks))
                            _groups.value = orderedResults.sortedBy { it.first }.map { it.second }
                        }
                    }
                }
            }.awaitAll()

            _loading.value = false
        }
    }

    private fun buildWordIndex(names: Set<String>): Map<String, Set<String>> {
        val index = HashMap<String, MutableSet<String>>()
        for (norm in names) {
            norm.split(' ').filter { it.length >= 4 }.forEach { word ->
                index.getOrPut(word) { HashSet() }.add(norm)
            }
        }
        return index
    }

    private fun normContent(s: String): String = s.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    // Word-level intersection match — both titles must share all meaningful words (4+ chars)
    // from the shorter title. Prevents short words like "It", "Us", "Her" from producing
    // false positives via substring matching.
    private fun titlesMatch(recNorm: String, availNorm: String): Boolean {
        if (recNorm == availNorm) return true
        if (recNorm.length < 4 || availNorm.length < 4) return false
        val recWords   = recNorm.split(' ').filter { it.length >= 4 }.toSet()
        val availWords = availNorm.split(' ').filter { it.length >= 4 }.toSet()
        if (recWords.isEmpty() || availWords.isEmpty()) return false
        val shorter = if (recWords.size <= availWords.size) recWords else availWords
        val longer  = if (recWords.size <= availWords.size) availWords else recWords
        return shorter.all { it in longer }
    }

    private suspend fun fetchRecommendations(seed: RecentlyWatchedEntity, wordIndex: Map<String, Set<String>>): List<PickItem> =
        withContext(Dispatchers.IO) {
            try {
                val mediaType = if (seed.type == RecentlyWatchedType.MOVIE) "movie" else "tv"
                val tmdbId = searchTmdbId(seed.name, mediaType) ?: return@withContext emptyList()
                fetchRecommendationsForId(tmdbId, mediaType, seed.name, wordIndex)
            } catch (_: Exception) { emptyList() }
        }

    private fun cleanTitle(title: String): String {
        // Strip all pipe-separated prefixes (e.g. "4K | UK | Title" → "Title")
        val base = if (title.contains('|')) title.substringAfterLast('|') else title
        return base
            .replace(Regex("\\s*(4K|UHD|FHD|HD|SDR|HDR|HEVC|BluRay|BRRip|WEBRip|WEB-DL|DVDRip)\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
            .replace(Regex("\\s*\\[\\d{4}\\]\\s*$"), "")
            .trim()
    }

    private fun searchTmdbId(title: String, preferType: String): Int? {
        val encoded = java.net.URLEncoder.encode(cleanTitle(title), "UTF-8")
        val url = "https://api.themoviedb.org/3/search/multi?query=$encoded&language=en-US&include_adult=false&page=1"
        val response = httpClient.newCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $tmdbToken").build()).execute()
        val body = response.body?.string() ?: return null
        val results = JSONObject(body).optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (item.optString("media_type") == preferType) return item.optInt("id")
        }
        return null
    }

    private fun fetchRecommendationsForId(
        tmdbId: Int, mediaType: String, seedTitle: String,
        wordIndex: Map<String, Set<String>> = emptyMap()
    ): List<PickItem> {
        val url = "https://api.themoviedb.org/3/$mediaType/$tmdbId/recommendations?language=en-US&page=1"
        val response = httpClient.newCall(Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $tmdbToken").build()).execute()
        val body = response.body?.string() ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val items = mutableListOf<PickItem>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val title = item.optString("title").ifEmpty { item.optString("name") }
            if (title.isEmpty()) continue
            if (wordIndex.isNotEmpty() && !isInPlaylist(normContent(title), wordIndex)) continue
            val posterPath = item.optString("poster_path").takeIf { it.isNotEmpty() }
            items.add(PickItem(
                tmdbId    = item.optInt("id"),
                title     = title,
                posterPath = posterPath,
                overview  = item.optString("overview"),
                mediaType = mediaType,
                seedTitle = seedTitle
            ))
            if (items.size >= 20) break
        }
        return items
    }

    // Uses word index for O(1) candidate lookup instead of O(n) full scan
    private fun isInPlaylist(norm: String, wordIndex: Map<String, Set<String>>): Boolean {
        if (norm.isEmpty()) return false
        val words = norm.split(' ').filter { it.length >= 4 }
        if (words.isEmpty()) return false
        val candidates = wordIndex[words.first()] ?: return false
        return candidates.any { avail -> titlesMatch(norm, avail) }
    }

    suspend fun resolvePickMovie(pick: PickItem): Pair<MovieEntity?, MovieEntity?> =
        withContext(Dispatchers.IO) {
            val base = repository.findMoviesByTitle(pick.title).firstOrNull()
                ?: return@withContext Pair(null, null)
            val vodId = base.id.removePrefix("${base.playlistId}-")
            val detailed = try { repository.getMovieDetails(base.playlistId, vodId) } catch (_: Exception) { null }
            Pair(base, detailed)
        }

    suspend fun resolvePickSeries(pick: PickItem): Triple<SeriesEntity?, SeriesEntity?, List<EpisodeEntity>> =
        withContext(Dispatchers.IO) {
            val base = repository.findSeriesByTitle(pick.title).firstOrNull()
                ?: return@withContext Triple(null, null, emptyList())
            val localEps = try { repository.getEpisodesForSeries(base.id).first() } catch (_: Exception) { emptyList() }
            if (localEps.isNotEmpty()) return@withContext Triple(base, base, localEps)
            val (updated, fetchedEps) = try {
                repository.getSeriesDetails(base.playlistId, base.seriesId)
            } catch (_: Exception) { Pair(null, emptyList()) }
            Triple(base, updated, fetchedEps)
        }

    suspend fun fetchMovieCertification(movieId: String, movieName: String) =
        repository.fetchCertificationForMovieSingle(movieId, movieName)

    suspend fun fetchMovieOriginalLanguage(movieId: String, movieName: String) =
        repository.fetchOriginalLanguageForMovieSingle(movieId, movieName)

    suspend fun fetchMovieRtData(movieId: String, movieName: String) =
        repository.fetchRtDataForMovieSingle(movieId, movieName)

    suspend fun fetchMovieTrailerUrl(movie: MovieEntity): String? {
        if (!movie.trailerUrl.isNullOrBlank()) return movie.trailerUrl
        return repository.fetchTrailerUrlForMovie(movie.name)
    }

    suspend fun fetchSeriesCertification(seriesId: String, seriesName: String) =
        repository.fetchCertificationForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesOriginalLanguage(seriesId: String, seriesName: String) =
        repository.fetchOriginalLanguageForSeriesSingle(seriesId, seriesName)

    suspend fun fetchSeriesTrailerUrl(seriesName: String) =
        repository.fetchTrailerUrlForSeries(seriesName)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PicksScreen(
    selectedSeed:             String?,
    onContentFocused:         () -> Unit = {},
    onDialogOpen:             (Boolean) -> Unit = {},
    firstItemFocusRequester:  FocusRequester? = null,
    onPickSelected:           ((PickItem) -> Unit)? = null,
    watchlistIds:             Set<String> = emptySet(),
    onToggleWatchlist:        ((PickItem, Boolean) -> Unit)? = null,
    onPlayerLaunch:           ((url: String, movieId: String?, episodeId: String?, seriesId: String?, startPos: Long, title: String?, subtitle: String?, description: String?) -> Unit)? = null,
    viewModel: PicksViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val nsTheme  = LocalNexStreamTheme.current
    val sTheme   = nsTheme.sidebar
    val groups   by viewModel.groups.collectAsState()
    val loading  by viewModel.loading.collectAsState()
    val recent   by viewModel.recentlyWatched.collectAsState()
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    var dialogPick by remember { mutableStateOf<PickItem?>(null) }

    val entityWatchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    var pickMovieEntity    by remember { mutableStateOf<MovieEntity?>(null) }
    var pickMovieUpdated   by remember { mutableStateOf<MovieEntity?>(null) }
    var pickSeriesEntity   by remember { mutableStateOf<SeriesEntity?>(null) }
    var pickSeriesUpdated  by remember { mutableStateOf<SeriesEntity?>(null) }
    var pickSeriesEpisodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var pickSeriesSeasons  by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pickSeriesLoading  by remember { mutableStateOf(false) }
    var pickEntityNotFound by remember { mutableStateOf(false) }

    LaunchedEffect(dialogPick) {
        val pick = dialogPick
        pickMovieEntity    = null
        pickMovieUpdated   = null
        pickSeriesEntity   = null
        pickSeriesUpdated  = null
        pickSeriesEpisodes = emptyList()
        pickSeriesSeasons  = emptyList()
        pickEntityNotFound = false
        if (pick == null) return@LaunchedEffect
        if (pick.mediaType == "movie") {
            val (base, detailed) = viewModel.resolvePickMovie(pick)
            if (base != null) {
                pickMovieEntity  = base
                if (detailed != null) pickMovieUpdated = detailed
            } else {
                pickEntityNotFound = true
            }
        } else {
            pickSeriesLoading = true
            val (base, updated, episodes) = viewModel.resolvePickSeries(pick)
            if (base != null) {
                pickSeriesEntity   = base
                pickSeriesEpisodes = episodes
                pickSeriesSeasons  = episodes.map { it.seasonNum }.distinct().sorted()
                if (updated != null) pickSeriesUpdated = updated
            } else {
                pickEntityNotFound = true
            }
            pickSeriesLoading = false
        }
    }

    // Load picks on first load and whenever new items appear in recently watched
    LaunchedEffect(recent.map { it.id }) {
        if (recent.isNotEmpty() && !loading) {
            viewModel.loadPicks(recent)
        }
    }

    // Visible groups: all or filtered by selectedSeed
    val visibleGroups = remember(groups, selectedSeed) {
        if (selectedSeed == null) groups
        else groups.filter { it.seedTitle == selectedSeed }
    }

    val uiStyle = LocalUiStyle.current
    if (uiStyle == UiStyle.MODERN) {
        ModernPicksContent(
            visibleGroups           = visibleGroups,
            loading                 = loading,
            recentIsEmpty           = recent.isEmpty(),
            firstItemFocusRequester = firstItemFocusRequester,
            onContentFocused        = onContentFocused,
            onPickSelected          = { dialogPick = it },
        )
    } else {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text  = if (selectedSeed != null) "Picks · $selectedSeed" else "Picks",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        when {
            recent.isEmpty() && !loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Stars, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("Watch something first",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Your Picks will show recommendations based on what you've watched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
            loading && groups.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Finding picks for you…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            visibleGroups.isEmpty() && !loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recommendations found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(visibleGroups, key = { _, group -> group.seedTitle }) { idx, group ->
                        PickGroupRow(
                            group                    = group,
                            onContentFocused         = onContentFocused,
                            firstItemFocusRequester  = if (idx == 0) firstItemFocusRequester else null,
                            onPickSelected           = { pick -> dialogPick = pick }
                        )
                    }
                    if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Loading more picks…", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    } // end else (Classic UI)

    dialogPick?.let { pick ->
        when {
            pickMovieEntity != null -> {
                val displayMovie  = pickMovieUpdated ?: pickMovieEntity!!
                val isBookmarked  = displayMovie.id in entityWatchlistIds
                ModernMovieDetailsDialog(
                    movie          = displayMovie,
                    isBookmarked   = isBookmarked,
                    onDismiss      = { dialogPick = null },
                    onPlay         = { startPos ->
                        onPlayerLaunch?.invoke(displayMovie.streamUrl, displayMovie.id, null, null, startPos, displayMovie.name, null, null)
                        dialogPick = null
                    },
                    onToggleWatchlist = {
                        watchlistViewModel.toggleWatchlist(
                            WatchlistEntity(
                                id        = displayMovie.id,
                                profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                type      = WatchlistType.MOVIE,
                                name      = displayMovie.name,
                                posterUrl = displayMovie.posterUrl,
                                streamUrl = displayMovie.streamUrl
                            ),
                            isBookmarked
                        )
                    },
                    onFetchCertification    = { viewModel.fetchMovieCertification(displayMovie.id, displayMovie.name) },
                    onFetchOriginalLanguage = { viewModel.fetchMovieOriginalLanguage(displayMovie.id, displayMovie.name) },
                    onFetchRtData           = { viewModel.fetchMovieRtData(displayMovie.id, displayMovie.name) },
                    onFetchTrailerUrl       = { viewModel.fetchMovieTrailerUrl(displayMovie) },
                )
            }
            pickSeriesEntity != null -> {
                val displaySeries = pickSeriesUpdated ?: pickSeriesEntity!!
                val isBookmarked  = displaySeries.id in entityWatchlistIds
                SeriesDetailsDialog(
                    series        = displaySeries,
                    episodes      = pickSeriesEpisodes,
                    seasons       = pickSeriesSeasons,
                    isLoading     = pickSeriesLoading,
                    isBookmarked  = isBookmarked,
                    onDismiss     = { dialogPick = null },
                    onToggleWatchlist = {
                        watchlistViewModel.toggleWatchlist(
                            WatchlistEntity(
                                id        = displaySeries.id,
                                profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                type      = WatchlistType.SERIES,
                                name      = displaySeries.name,
                                posterUrl = displaySeries.posterUrl,
                                streamUrl = null
                            ),
                            isBookmarked
                        )
                    },
                    onFetchCertification    = { viewModel.fetchSeriesCertification(displaySeries.id, displaySeries.name) },
                    onFetchOriginalLanguage = { viewModel.fetchSeriesOriginalLanguage(displaySeries.id, displaySeries.name) },
                    onFetchTrailerUrl       = { viewModel.fetchSeriesTrailerUrl(displaySeries.name) },
                    onGoToSeries  = { onPickSelected?.invoke(pick); dialogPick = null },
                    onPlayEpisode = { streamUrl, episodeId, startPos, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                        onPlayerLaunch?.invoke(streamUrl, null, episodeId, seriesId, startPos, seriesName, "S${seasonNum}E${episodeNum} - $episodeName", null)
                        dialogPick = null
                    }
                )
            }
            pickEntityNotFound -> {
                // Title not matched in local DB — simple fallback
                val isBookmarked = pick.tmdbId.toString() in watchlistIds
                val goToLabel = if (pick.mediaType == "movie") "Go to Movies" else "Go to Series"
                ContentActionDialog(
                    name        = pick.title,
                    posterUrl   = pick.posterUrl,
                    goToLabel   = goToLabel,
                    removeLabel = if (isBookmarked) "Remove from My List" else "Add to My List",
                    removeIsDestructive = isBookmarked,
                    onDismiss   = { dialogPick = null },
                    onRemove    = { dialogPick = null; onToggleWatchlist?.invoke(pick, isBookmarked) },
                    onGoTo      = { dialogPick = null; onPickSelected?.invoke(pick) }
                )
            }
        }
    }
}

@Composable
private fun PickGroupRow(
    group:                   PickGroup,
    onContentFocused:        () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    onPickSelected:          ((PickItem) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Because you watched ${group.seedTitle}",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(group.items, key = { _, pick -> "${group.seedTitle}-${pick.tmdbId}" }) { idx, pick ->
                PickCard(
                    pick           = pick,
                    focusRequester = if (idx == 0) firstItemFocusRequester else null,
                    onFocused      = { onContentFocused() },
                    onSelected     = { onPickSelected?.invoke(pick) }
                )
            }
        }
    }
}

@Composable
private fun PickCard(
    pick:           PickItem,
    focusRequester: FocusRequester? = null,
    onFocused:      () -> Unit = {},
    onSelected:     () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale"
    )

    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = primary,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (
                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                )) { onSelected(); true } else false
            }
            .clickable { onSelected() }
            .focusable()
    ) {
        AsyncImage(
            model             = pick.posterUrl,
            contentDescription = pick.title,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier.fillMaxSize()
        )

        // Gradient + title overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
        )
        Text(
            text     = pick.title,
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )

        // Type badge
        Surface(
            modifier  = Modifier.align(Alignment.TopStart).padding(4.dp),
            shape     = RoundedCornerShape(4.dp),
            color     = if (pick.mediaType == "movie") primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
            tonalElevation = 0.dp
        ) {
            Text(
                text     = if (pick.mediaType == "movie") "MOVIE" else "TV",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onPrimary,
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

