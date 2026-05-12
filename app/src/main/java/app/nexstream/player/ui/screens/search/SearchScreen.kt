package app.nexstream.player.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.ui.components.TvKeyboard
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: SearchViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val firstResultFocus = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Search bar — full width, opens TvKeyboard dialog on select ────
            var searchBarFocused by remember { mutableStateOf(false) }
            var showKeyboard by remember { mutableStateOf(false) }
            val searchFocusRequester = remember { FocusRequester() }

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

            // ── Results — full width ──────────────────────────────────────────
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
                    val tabs = listOf(
                        "Channels (${results.channels.size})",
                        "Programmes (${results.programmes.size})",
                        "Movies (${results.movies.size})",
                        "Series (${results.series.size})"
                    )
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }

                    val totalResults = results.channels.size + results.programmes.size +
                            results.movies.size + results.series.size

                    if (totalResults == 0) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No results for \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        when (selectedTab) {
                            0 -> ChannelResults(
                                channels = results.channels,
                                firstResultFocus = firstResultFocus,
                                onChannelClick = onChannelClick,
                                watchlistViewModel = watchlistViewModel,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )
                            1 -> ProgrammeResults(
                                programmes = results.programmes,
                                firstResultFocus = firstResultFocus,
                                channels = results.channels,
                                onChannelClick = onChannelClick
                            )
                            2 -> MovieResults(
                                movies = results.movies,
                                firstResultFocus = firstResultFocus,
                                onMovieClick = onMovieClick,
                                watchlistViewModel = watchlistViewModel,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )
                            3 -> SeriesResults(
                                series = results.series,
                                firstResultFocus = firstResultFocus,
                                onSeriesClick = onSeriesClick,
                                watchlistViewModel = watchlistViewModel,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )
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
private fun ChannelResults(
    channels: List<ChannelEntity>,
    firstResultFocus: FocusRequester,
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    watchlistViewModel: WatchlistViewModel,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (channels.isEmpty()) { EmptyTabMessage("No channels found"); return }
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
            val isBookmarked by watchlistViewModel.isInWatchlist(channel.id).collectAsState(initial = false)
            SearchResultRow(
                imageUrl = channel.logoUrl, title = channel.name, subtitle = channel.groupTitle,
                icon = Icons.Default.Tv, isBookmarked = isBookmarked,
                focusRequester = if (index == 0) firstResultFocus else null,
                onBookmark = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id = channel.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type = WatchlistType.CHANNEL,
                            name = channel.name,
                            posterUrl = channel.logoUrl,
                            streamUrl = channel.streamUrl
                        ), isBookmarked
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (!isBookmarked) "${channel.name} added to My List"
                            else "${channel.name} removed from My List"
                        )
                    }
                },
                onClick = { onChannelClick(channel.streamUrl, channel.name) }
            )
        }
    }
}

@Composable
private fun ProgrammeResults(
    programmes: List<ProgramEntity>,
    firstResultFocus: FocusRequester,
    channels: List<ChannelEntity>,
    onChannelClick: (streamUrl: String, channelName: String) -> Unit
) {
    if (programmes.isEmpty()) { EmptyTabMessage("No programmes found"); return }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(programmes, key = { _, p -> p.id }) { index, programme ->
            val timeRange = "${timeFormat.format(Date(programme.startTime))} – ${timeFormat.format(Date(programme.endTime))}"
            SearchResultRow(
                imageUrl = programme.icon, title = programme.title, subtitle = timeRange,
                icon = Icons.Default.CalendarToday,
                focusRequester = if (index == 0) firstResultFocus else null,
                onClick = {
                    val channel = channels.firstOrNull { it.epgChannelId == programme.channelId }
                    if (channel != null) onChannelClick(channel.streamUrl, channel.name)
                }
            )
        }
    }
}

@Composable
private fun MovieResults(
    movies: List<MovieEntity>,
    firstResultFocus: FocusRequester,
    onMovieClick: (MovieEntity) -> Unit,
    watchlistViewModel: WatchlistViewModel,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (movies.isEmpty()) { EmptyTabMessage("No movies found"); return }
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
            val isBookmarked by watchlistViewModel.isInWatchlist(movie.id).collectAsState(initial = false)
            SearchResultRow(
                imageUrl = movie.posterUrl, title = movie.name, subtitle = movie.categoryName,
                icon = Icons.Default.Movie, isBookmarked = isBookmarked,
                focusRequester = if (index == 0) firstResultFocus else null,
                onBookmark = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id = movie.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type = WatchlistType.MOVIE,
                            name = movie.name,
                            posterUrl = movie.posterUrl,
                            streamUrl = movie.streamUrl
                        ), isBookmarked
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (!isBookmarked) "${movie.name} added to My List"
                            else "${movie.name} removed from My List"
                        )
                    }
                },
                onClick = { onMovieClick(movie) }
            )
        }
    }
}

@Composable
private fun SeriesResults(
    series: List<SeriesEntity>,
    firstResultFocus: FocusRequester,
    onSeriesClick: (SeriesEntity) -> Unit,
    watchlistViewModel: WatchlistViewModel,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (series.isEmpty()) { EmptyTabMessage("No series found"); return }
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(series, key = { _, s -> s.id }) { index, s ->
            val isBookmarked by watchlistViewModel.isInWatchlist(s.id).collectAsState(initial = false)
            SearchResultRow(
                imageUrl = s.posterUrl, title = s.name,
                subtitle = if (s.seasonCount > 0) "${s.seasonCount} season${if (s.seasonCount > 1) "s" else ""}" else s.categoryName,
                icon = Icons.Default.VideoLibrary, isBookmarked = isBookmarked,
                focusRequester = if (index == 0) firstResultFocus else null,
                onBookmark = {
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id = s.id,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type = WatchlistType.SERIES,
                            name = s.name,
                            posterUrl = s.posterUrl,
                            streamUrl = null
                        ), isBookmarked
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (!isBookmarked) "${s.name} added to My List"
                            else "${s.name} removed from My List"
                        )
                    }
                },
                onClick = { onSeriesClick(s) }
            )
        }
    }
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