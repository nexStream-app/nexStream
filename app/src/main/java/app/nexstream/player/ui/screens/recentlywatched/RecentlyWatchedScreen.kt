package app.nexstream.player.ui.screens.recentlywatched

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.ui.components.ContentActionDialog
import app.nexstream.player.ui.screens.movies.ModernMovieDetailsDialog
import app.nexstream.player.ui.screens.series.SeriesDetailsDialog
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.components.TvKeyboardSheet
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import coil.compose.AsyncImage

@Composable
fun RecentlyWatchedScreen(
    onChannelClick: (streamUrl: String, name: String) -> Unit,
    onMovieClick: (item: RecentlyWatchedEntity) -> Unit,
    onEpisodeClick: (item: RecentlyWatchedEntity) -> Unit,
    onGoToMovie: (name: String) -> Unit = {},
    onGoToSeries: (seriesName: String) -> Unit = {},
    onGoToEpgForChannel: (channelName: String) -> Unit = {},
    selectedType: String? = null,
    showSearch: Boolean = false,
    showClearConfirm: Boolean = false,
    onClearDismissed: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    onLaunchPlayer: (url: String, movieId: String?, episodeId: String?, seriesId: String?, startPos: Long, title: String?, subtitle: String?) -> Unit = { _, _, _, _, _, _, _ -> },
    viewModel: RecentlyWatchedViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val items by viewModel.recentlyWatched.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    var dialogItem by remember { mutableStateOf<RecentlyWatchedEntity?>(null) }

    var movieDialogEntity    by remember { mutableStateOf<MovieEntity?>(null) }
    var movieDialogUpdated   by remember { mutableStateOf<MovieEntity?>(null) }
    var seriesDialogEntity   by remember { mutableStateOf<SeriesEntity?>(null) }
    var seriesDialogUpdated  by remember { mutableStateOf<SeriesEntity?>(null) }
    var seriesDialogEpisodes by remember { mutableStateOf<List<EpisodeEntity>>(emptyList()) }
    var seriesDialogSeasons  by remember { mutableStateOf<List<Int>>(emptyList()) }
    var seriesDialogLoading  by remember { mutableStateOf(false) }
    var dialogEntityLoading  by remember { mutableStateOf(false) }

    LaunchedEffect(dialogItem) {
        val item = dialogItem
        movieDialogEntity    = null
        movieDialogUpdated   = null
        seriesDialogEntity   = null
        seriesDialogUpdated  = null
        seriesDialogEpisodes = emptyList()
        seriesDialogSeasons  = emptyList()
        if (item == null) return@LaunchedEffect
        if (item.type != RecentlyWatchedType.CHANNEL) dialogEntityLoading = true
        when (item.type) {
            RecentlyWatchedType.MOVIE -> {
                val movieId = item.movieId ?: item.id
                val base = viewModel.getMovieById(movieId)
                movieDialogEntity = base
                if (base != null) {
                    val detailed = viewModel.loadMovieDetails(base)
                    if (detailed != null) movieDialogUpdated = detailed
                }
            }
            RecentlyWatchedType.EPISODE -> {
                val seriesId = item.seriesId ?: run { dialogEntityLoading = false; return@LaunchedEffect }
                val s = viewModel.getSeriesById(seriesId)
                seriesDialogEntity = s
                if (s != null) {
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
            else -> {}
        }
        dialogEntityLoading = false
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

    val filteredItems = remember(items, selectedType, debouncedQuery) {
        val byType = when (selectedType) {
            "Live TV"  -> items.filter { it.type == RecentlyWatchedType.CHANNEL }
            "Movies"   -> items.filter { it.type == RecentlyWatchedType.MOVIE }
            "Episodes" -> items.filter { it.type == RecentlyWatchedType.EPISODE }
            else       -> items
        }
        if (showSearch && debouncedQuery.isNotBlank())
            byType.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
        else byType
    }

    val firstItemFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        val uiStyle = LocalUiStyle.current
        if (uiStyle == UiStyle.MODERN) {
            ModernRecentlyWatchedContent(
                allItems                = items,
                filteredItems           = filteredItems,
                selectedType            = selectedType,
                progressMap             = progressMap,
                firstItemFocusRequester = firstItemFR,
                onItemClick             = { dialogItem = it },
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

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = sTheme.categoryText.copy(alpha = 0.3f))
                        Text("Nothing watched yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = sTheme.categoryText.copy(alpha = 0.6f))
                        Text("Content you watch will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = sTheme.categoryText.copy(alpha = 0.4f))
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${selectedType?.lowercase() ?: "items"} watched recently",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (selectedType == null) {
                // ── All: horizontal card rows by type ─────────────────────────
                val channels = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.CHANNEL } }
                val movies   = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.MOVIE } }
                val episodes = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.EPISODE } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (channels.isNotEmpty()) {
                        item(key = "section_channels") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecentSectionHeader("Live TV")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(channels, key = { _, it -> "ch_${it.id}" }) { idx, ch ->
                                        ContentCard(
                                            name = ch.name,
                                            posterUrl = ch.logoUrl,
                                            defaultIcon = Icons.Default.Tv,
                                            focusRequester = if (idx == 0) firstItemFR else null,
                                            onFocused = {},
                                            onClick = { dialogItem = ch }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (movies.isNotEmpty()) {
                        item(key = "section_movies") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecentSectionHeader("Movies")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(movies, key = { _, it -> "mov_${it.id}" }) { idx, movie ->
                                        ContentCard(
                                            name = movie.name,
                                            posterUrl = movie.logoUrl,
                                            defaultIcon = Icons.Default.Movie,
                                            focusRequester = if (idx == 0 && channels.isEmpty()) firstItemFR else null,
                                            onFocused = {},
                                            onClick = { dialogItem = movie }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        item(key = "section_episodes") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecentSectionHeader("Episodes")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    itemsIndexed(episodes, key = { _, it -> "ep_${it.id}" }) { idx, ep ->
                                        ContentCard(
                                            name = ep.name,
                                            posterUrl = ep.logoUrl,
                                            badge = ep.subtitle?.take(10),
                                            defaultIcon = Icons.Default.VideoLibrary,
                                            focusRequester = if (idx == 0 && channels.isEmpty() && movies.isEmpty()) firstItemFR else null,
                                            onFocused = {},
                                            onClick = { dialogItem = ep }
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
                            itemsIndexed(filteredItems, key = { _, it -> it.id }) { index, item ->
                                val icon = when (item.type) {
                                    RecentlyWatchedType.CHANNEL -> Icons.Default.Tv
                                    RecentlyWatchedType.MOVIE   -> Icons.Default.Movie
                                    RecentlyWatchedType.EPISODE -> Icons.Default.VideoLibrary
                                }
                                ContentCard(
                                    name = item.name,
                                    posterUrl = item.logoUrl,
                                    badge = if (item.type == RecentlyWatchedType.EPISODE) item.subtitle?.take(10) else null,
                                    defaultIcon = icon,
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
            hint          = "Search recent…",
            modifier      = Modifier.fillMaxSize()
        )
    }

    val selectedRecent = dialogItem
    if (selectedRecent != null && !dialogEntityLoading) {
        val movie  = movieDialogEntity
        val series = seriesDialogEntity

        when {
            selectedRecent.type == RecentlyWatchedType.MOVIE && movie != null -> {
                val displayMovie = movieDialogUpdated ?: movie
                val resumePos = displayMovie.lastPlayedPosition
                ModernMovieDetailsDialog(
                    movie          = displayMovie,
                    resumePosition = resumePos,
                    isBookmarked   = false,
                    onDismiss      = { movieDialogEntity = null; movieDialogUpdated = null; dialogItem = null },
                    onPlay         = { startPos ->
                        onLaunchPlayer(displayMovie.streamUrl, displayMovie.id, null, null, startPos, displayMovie.name, null)
                        movieDialogEntity = null; movieDialogUpdated = null; dialogItem = null
                    },
                    onFetchCertification    = { viewModel.fetchMovieCertification(displayMovie.id, displayMovie.name) },
                    onFetchOriginalLanguage = { viewModel.fetchMovieOriginalLanguage(displayMovie.id, displayMovie.name) },
                    onFetchRtData           = { viewModel.fetchMovieRtData(displayMovie.id, displayMovie.name) },
                    onFetchTrailerUrl       = { viewModel.fetchMovieTrailerUrl(displayMovie) },
                )
            }
            selectedRecent.type == RecentlyWatchedType.EPISODE && series != null -> {
                val displaySeries = seriesDialogUpdated ?: series
                SeriesDetailsDialog(
                    series        = displaySeries,
                    episodes      = seriesDialogEpisodes,
                    seasons       = seriesDialogSeasons,
                    isLoading     = seriesDialogLoading,
                    isBookmarked  = false,
                    onDismiss     = { seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null },
                    onGoToSeries  = {
                        seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null
                        onGoToSeries(selectedRecent.name)
                    },
                    onFetchCertification    = { viewModel.fetchSeriesCertification(displaySeries.id, displaySeries.name) },
                    onFetchOriginalLanguage = { viewModel.fetchSeriesOriginalLanguage(displaySeries.id, displaySeries.name) },
                    onFetchTrailerUrl       = { viewModel.fetchSeriesTrailerUrl(displaySeries.name) },
                    onPlayEpisode = { streamUrl, episodeId, startPos, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                        onLaunchPlayer(streamUrl, null, episodeId, seriesId, startPos, seriesName, "S${seasonNum}E${episodeNum} - $episodeName")
                        seriesDialogEntity = null; seriesDialogUpdated = null; dialogItem = null
                    }
                )
            }
            else -> {
                val goToLabel = when (selectedRecent.type) {
                    RecentlyWatchedType.CHANNEL -> "Go to EPG"
                    RecentlyWatchedType.MOVIE   -> "Go to Movies"
                    RecentlyWatchedType.EPISODE -> "Go to Series"
                }
                ContentActionDialog(
                    name        = selectedRecent.name,
                    posterUrl   = selectedRecent.logoUrl,
                    goToLabel   = goToLabel,
                    removeLabel = "Remove from Recent",
                    onDismiss   = { dialogItem = null },
                    onRemove    = {
                        dialogItem = null
                        viewModel.delete(selectedRecent.id)
                    },
                    onGoTo      = {
                        dialogItem = null
                        when (selectedRecent.type) {
                            RecentlyWatchedType.CHANNEL -> onGoToEpgForChannel(selectedRecent.name)
                            RecentlyWatchedType.MOVIE   -> onGoToMovie(selectedRecent.name)
                            RecentlyWatchedType.EPISODE -> onGoToSeries(selectedRecent.name)
                        }
                    }
                )
            }
        }
    }

    if (showClearConfirm) {
        val typeLabel = when (selectedType) {
            "Live TV"  -> "Live TV channels"
            "Movies"   -> "movies"
            "Episodes" -> "episodes"
            else       -> "everything"
        }
        ClearHistoryConfirmDialog(
            title   = "Clear History",
            message = "Remove $typeLabel from recently watched?",
            onDismiss = { onClearDismissed() },
            onConfirm = { viewModel.clearAllForType(selectedType); onClearDismissed() }
        )
    }
}

@Composable
internal fun ClearHistoryConfirmDialog(
    title: String,
    message: String,
    warningText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 0 = Cancel (default focus), 1 = Clear All
    var selected by remember { mutableStateOf(0) }
    val dialogFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); try { dialogFR.requestFocus() } catch (_: Exception) {} }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(Unit) {
            dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setDimAmount(0.95f)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.48f),
            shape    = RoundedCornerShape(16.dp),
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selected = (selected - 1 + 2) % 2; true }
                            Key.DirectionRight -> { selected = (selected + 1) % 2; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (selected == 0) onDismiss() else onConfirm(); true
                            }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (warningText != null) {
                    Text(warningText, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cancel — index 0, focused by default
                    ClearDialogPill(label = "Cancel", isSelected = selected == 0, isDestructive = false, onClick = onDismiss)
                    // Clear All — index 1
                    ClearDialogPill(label = "Clear All", isSelected = selected == 1, isDestructive = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun ClearDialogPill(label: String, isSelected: Boolean, isDestructive: Boolean, onClick: () -> Unit) {
    val accent = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bg     = if (isSelected) accent else Color.Transparent
    val fgText = if (isSelected) Color.White else accent
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(1.5.dp, accent, RoundedCornerShape(50.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fgText)
    }
}

@Composable
private fun RecentSectionHeader(title: String) {
    val nsTheme = LocalNexStreamTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = nsTheme.sidebar.categoryText
    )
}

@Composable
private fun RecentItemRow(
    item: RecentlyWatchedEntity,
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
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logoUrl.isNullOrEmpty()) {
                    AsyncImage(model = item.logoUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(imageVector = defaultIcon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface)
                if (item.subtitle != null) {
                    Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
