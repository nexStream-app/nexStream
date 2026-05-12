package app.nexstream.player.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.ui.components.TvKeyboard
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import androidx.compose.material3.HorizontalDivider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


@Composable
fun SearchScreen(
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (movie: MovieEntity) -> Unit,
    onSeriesClick: (series: SeriesEntity) -> Unit,
    selectedType: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: SearchViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val firstResultFocus = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Themed header ────────────────────────────────────────────────
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

            // ── Search bar — full width, opens TvKeyboard dialog on select ────
            var searchBarFocused by remember { mutableStateOf(false) }
            var showKeyboard by remember { mutableStateOf(false) }
            val searchFocusRequester = firstItemFocusRequester ?: remember { FocusRequester() }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester)
                    .onFocusChanged { searchBarFocused = it.isFocused }
                    .onKeyEvent { e ->
                        when {
                            e.type == KeyEventType.KeyDown && (
                                    e.key == Key.Enter ||
                                            e.key == Key.NumPadEnter ||
                                            e.key == Key.DirectionCenter
                                    ) -> { showKeyboard = true; true }
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
                        text = query.ifEmpty { "Search channels, movies, series..." },
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

            // ── TvKeyboard dialog — same pattern as MoviesScreen/SeriesScreen ─
            if (showKeyboard) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = {
                        showKeyboard = false
                        try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                    },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = query.ifEmpty { "Type to search..." },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (query.isEmpty())
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("│", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            TvKeyboard(
                                value = query,
                                onValueChange = viewModel::onQueryChange,
                                onDone = {
                                    showKeyboard = false
                                    try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }

            // ── Results — filtered by panel selection ────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
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
                                "Type to search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val showChannels = selectedType == null || selectedType == "Live TV"
                    val showMovies   = selectedType == null || selectedType == "Movies"
                    val showSeries   = selectedType == null || selectedType == "Series"

                    val hasAny = (showChannels && (results.channels.isNotEmpty() || results.programmes.isNotEmpty())) ||
                                 (showMovies && results.movies.isNotEmpty()) ||
                                 (showSeries && results.series.isNotEmpty())

                    if (!hasAny) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No results for \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Determine first focus target: first non-empty visible section
                        val firstSectionHasChannels = showChannels && (results.channels.isNotEmpty() || results.programmes.isNotEmpty())
                        val firstSectionHasMovies   = !firstSectionHasChannels && showMovies && results.movies.isNotEmpty()
                        androidx.compose.foundation.lazy.LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            if (showChannels && results.channels.isNotEmpty()) {
                                item {
                                    ResultSectionHeader("Channels (${results.channels.size})")
                                }
                                results.channels.forEachIndexed { index, channel ->
                                    item(key = "ch_${channel.id}") {
                                        val isBookmarked by watchlistViewModel.isInWatchlist(channel.id).collectAsState(initial = false)
                                        SearchResultRow(
                                            imageUrl = channel.logoUrl, title = channel.name, subtitle = channel.groupTitle,
                                            icon = Icons.Default.Tv, isBookmarked = isBookmarked,
                                            focusRequester = if (firstSectionHasChannels && index == 0) firstResultFocus else null,
                                            onBookmark = {
                                                watchlistViewModel.toggleWatchlist(
                                                    WatchlistEntity(id = channel.id,
                                                        profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                                        type = WatchlistType.CHANNEL, name = channel.name,
                                                        posterUrl = channel.logoUrl, streamUrl = channel.streamUrl), isBookmarked)
                                                scope.launch { snackbarHostState.showSnackbar(if (!isBookmarked) "${channel.name} added to My List" else "${channel.name} removed from My List") }
                                            },
                                            onClick = { onChannelClick(channel.streamUrl, channel.name) }
                                        )
                                    }
                                }
                            }
                            if (showChannels && results.programmes.isNotEmpty()) {
                                item {
                                    ResultSectionHeader("Programmes (${results.programmes.size})")
                                }
                                results.programmes.forEachIndexed { index, programme ->
                                    item(key = "prog_${programme.id}") {
                                        val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                                        val timeRange = "${timeFormat.format(java.util.Date(programme.startTime))} – ${timeFormat.format(java.util.Date(programme.endTime))}"
                                        SearchResultRow(
                                            imageUrl = programme.icon, title = programme.title, subtitle = timeRange,
                                            icon = Icons.Default.CalendarToday,
                                            focusRequester = if (firstSectionHasChannels && results.channels.isEmpty() && index == 0) firstResultFocus else null,
                                            onClick = {
                                                val ch = results.channels.firstOrNull { it.epgChannelId == programme.channelId }
                                                if (ch != null) onChannelClick(ch.streamUrl, ch.name)
                                            }
                                        )
                                    }
                                }
                            }
                            if (showMovies && results.movies.isNotEmpty()) {
                                item { ResultSectionHeader("Movies (${results.movies.size})") }
                                results.movies.forEachIndexed { index, movie ->
                                    item(key = "mov_${movie.id}") {
                                        val isBookmarked by watchlistViewModel.isInWatchlist(movie.id).collectAsState(initial = false)
                                        SearchResultRow(
                                            imageUrl = movie.posterUrl, title = movie.name, subtitle = movie.categoryName,
                                            icon = Icons.Default.Movie, isBookmarked = isBookmarked,
                                            focusRequester = if (firstSectionHasMovies && index == 0) firstResultFocus else null,
                                            onBookmark = {
                                                watchlistViewModel.toggleWatchlist(
                                                    WatchlistEntity(id = movie.id,
                                                        profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                                        type = WatchlistType.MOVIE, name = movie.name,
                                                        posterUrl = movie.posterUrl, streamUrl = movie.streamUrl), isBookmarked)
                                                scope.launch { snackbarHostState.showSnackbar(if (!isBookmarked) "${movie.name} added to My List" else "${movie.name} removed from My List") }
                                            },
                                            onClick = { onMovieClick(movie) }
                                        )
                                    }
                                }
                            }
                            if (showSeries && results.series.isNotEmpty()) {
                                item { ResultSectionHeader("Series (${results.series.size})") }
                                results.series.forEachIndexed { index, s ->
                                    item(key = "ser_${s.id}") {
                                        val isBookmarked by watchlistViewModel.isInWatchlist(s.id).collectAsState(initial = false)
                                        SearchResultRow(
                                            imageUrl = s.posterUrl, title = s.name,
                                            subtitle = if (s.seasonCount > 0) "${s.seasonCount} season${if (s.seasonCount > 1) "s" else ""}" else s.categoryName,
                                            icon = Icons.Default.VideoLibrary, isBookmarked = isBookmarked,
                                            focusRequester = if (!firstSectionHasChannels && !firstSectionHasMovies && index == 0) firstResultFocus else null,
                                            onBookmark = {
                                                watchlistViewModel.toggleWatchlist(
                                                    WatchlistEntity(id = s.id,
                                                        profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                                        type = WatchlistType.SERIES, name = s.name,
                                                        posterUrl = s.posterUrl, streamUrl = null), isBookmarked)
                                                scope.launch { snackbarHostState.showSnackbar(if (!isBookmarked) "${s.name} added to My List" else "${s.name} removed from My List") }
                                            },
                                            onClick = { onSeriesClick(s) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun ResultSectionHeader(title: String) {
    val nsTheme = LocalNexStreamTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = nsTheme.sidebar.categoryText.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Tv,
    isBookmarked: Boolean = false,
    focusRequester: FocusRequester? = null,
    onBookmark: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onBookmark),
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
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .size(80, 80)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(

                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onBookmark != null) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                    else if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}