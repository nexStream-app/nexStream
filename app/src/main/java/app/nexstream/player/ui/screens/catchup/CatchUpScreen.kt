package app.nexstream.player.ui.screens.catchup

import androidx.activity.compose.BackHandler
import app.nexstream.player.ui.components.TvKeyboardSheet
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.TmdbPosterDao
import app.nexstream.player.data.local.dao.getByTitles
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.TmdbPosterEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.net.HttpURLConnection
import java.net.URL
import android.os.Environment
import android.os.StatFs
import java.util.*
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import app.nexstream.player.R

// ─────────────────────────────────────────────────────────────────────────────
// Date helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Int.ordinal(): String = when {
    this % 100 in 11..13 -> "${this}th"
    this % 10 == 1        -> "${this}st"
    this % 10 == 2        -> "${this}nd"
    this % 10 == 3        -> "${this}rd"
    else                  -> "${this}th"
}

fun catchUpDateLabel(ms: Long, today: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val num = cal.get(Calendar.DAY_OF_MONTH).ordinal()
    val day = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
    return when (ms) {
        today               -> "Today"
        today - 86_400_000L -> "Yesterday"
        else                -> "$day $num"
    }
}

fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun todayStart(): Long = startOfDay(System.currentTimeMillis())

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class CatchUpViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val tmdbDao:    TmdbPosterDao
) : ViewModel() {

    private val _listings   = MutableStateFlow<List<ProgramEntity>>(emptyList())
    private val _loading    = MutableStateFlow(false)
    private val _thumbnails = MutableStateFlow<Map<String, String>>(emptyMap())

    private val tmdbToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJhYzA2NjBlNjM2MGVlNDE0NmQyNDA3MzUyOTQ2ZmRkYiIsIm5iZiI6MTc3Njg3MzQ2OS4wNjA5OTk5LCJzdWIiOiI2OWU4ZWZmZDQyM2ZhZDJlYzhlMGNmY2QiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.1gbVrIUZezfVmRoFk692A__HsaO9GahlPeMT6h2JBKo"
    private val httpClient  = OkHttpClient()

    val isLoadingListings: StateFlow<Boolean>             = _loading.asStateFlow()
    val thumbnails:        StateFlow<Map<String, String>> = _thumbnails.asStateFlow()
    val playlists = repository.getAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // Available dates derived from listings — updates as parallel fetches complete
    val availableDates: StateFlow<List<Long>> = _listings
        .map { listings ->
            listings.map { app.nexstream.player.ui.screens.catchup.startOfDay(it.startTime) }
                .distinct().sortedDescending()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allCatchUpChannels: StateFlow<List<ChannelEntity>> = repository.getAllPlaylists()
        .flatMapLatest { playlists ->
            if (playlists.isEmpty()) flowOf(emptyList())
            else combine(playlists.map { repository.getChannelsByPlaylist(it.id) }) { arrays ->
                arrays.flatMap { it }.filter { it.tvArchive == 1 }.sortedBy { it.sortIndex }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetchAllCatchUpListings(channels: List<ChannelEntity>) {
        viewModelScope.launch {
            _loading.value    = true
            _listings.value   = emptyList()
            _thumbnails.value = emptyMap()
            val now   = System.currentTimeMillis()
            val start = now - 7 * 86_400_000L

            // Run all channel queries in PARALLEL — dramatically faster than sequential
            // Emit partial results as each channel completes — dates appear immediately
            val accumulated = Mutex()
            val partial     = mutableListOf<ProgramEntity>()
            coroutineScope {
                val deferreds = channels.map { channel ->
                    async(Dispatchers.IO) {
                        try {
                            val epgId = channel.epgChannelId ?: return@async
                            val result = repository.getProgramsForChannelInRange(epgId, start, now)
                                .first()
                                .filter { it.endTime <= now && it.startTime >= start }
                            accumulated.withLock { partial.addAll(result) }
                        } catch (_: Exception) {}
                    }
                }
                deferreds.awaitAll()
            } // end coroutineScope

            val deduped = partial
                .distinctBy { "${it.title}|${it.startTime}" }
                .sortedBy { it.title }
            android.util.Log.d("CatchUp", "Total: ${deduped.size} listings, ${deduped.map { it.title }.distinct().size} unique")
            _listings.value = deduped
            fetchThumbnails(deduped)
            _loading.value = false
        }
    }

    private fun fetchThumbnails(listings: List<ProgramEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val titles = listings.map { it.title }.distinct()
            // Load cached first
            val cached = tmdbDao.getByTitles(titles).associate { it.title to it.posterUrl }.toMutableMap()
            if (cached.isNotEmpty()) _thumbnails.value = cached.toMap()

            // Fetch only uncached
            val uncached  = titles.filter { it !in cached }
            val newEntries = mutableListOf<TmdbPosterEntity>()
            uncached.forEach { title ->
                try {
                    val enc = URLEncoder.encode(title, "UTF-8")
                    for (endpoint in listOf(
                        "https://api.themoviedb.org/3/search/tv?query=$enc&include_adult=false&language=en-GB&page=1",
                        "https://api.themoviedb.org/3/search/multi?query=$enc&include_adult=false&language=en-US&page=1"
                    )) {
                        val response = httpClient.newCall(
                            Request.Builder().url(endpoint).get()
                                .addHeader("accept", "application/json")
                                .addHeader("Authorization", "Bearer $tmdbToken")
                                .build()
                        ).execute()
                        val body = response.body?.string(); response.close()
                        if (response.isSuccessful && body != null) {
                            val path = Regex(""""poster_path":"(/[^"]+)"""").find(body)?.groupValues?.get(1)
                            if (path != null) {
                                val url = "https://image.tmdb.org/t/p/w300$path"
                                cached[title] = url
                                newEntries.add(TmdbPosterEntity(title, url))
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CatchUp", "TMDB '$title': ${e::class.simpleName}")
                }
            }
            if (newEntries.isNotEmpty()) {
                tmdbDao.upsertAll(newEntries)
                // Single emission for all newly fetched posters — avoids per-card redraws
                _thumbnails.value = cached.toMap()
            }
        }
    }

    fun getCatchUpListings(): StateFlow<List<ProgramEntity>> = _listings.asStateFlow()

    fun buildTimeshiftUrl(channel: ChannelEntity, programme: ProgramEntity): String {
        val streamId = channel.streamId ?: extractStreamId(channel.streamUrl)
        val durMins  = ((programme.endTime - programme.startTime) / 60_000L).coerceAtLeast(1L)
        // Use LOCAL time — most IPTV providers expect local time in timeshift URLs, not UTC
        val cal = Calendar.getInstance().apply { timeInMillis = programme.startTime }
        val y  = cal.get(Calendar.YEAR)
        val mo = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val d  = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
        val h  = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
        val mi = "%02d".format(cal.get(Calendar.MINUTE))
        val parts  = channel.streamUrl.split("/")
        val server = parts.take(3).joinToString("/")
        val user   = parts.getOrNull(4) ?: ""
        val pass   = parts.getOrNull(5) ?: ""
        android.util.Log.d("CatchUpDebug", "buildTimeshiftUrl: streamUrl=${channel.streamUrl}")
        android.util.Log.d("CatchUpDebug", "parts=$parts")
        android.util.Log.d("CatchUpDebug", "server=$server user=$user pass=$pass streamId=$streamId")
        android.util.Log.d("CatchUpDebug", "startTime=${programme.startTime} durMins=$durMins date=$y-$mo-$d:$h-$mi (LOCAL TIME)")
        return "$server/timeshift/$user/$pass/$durMins/$y-$mo-$d:$h-$mi/$streamId.ts"
    }

    private fun extractStreamId(url: String) = url.substringAfterLast("/").substringBefore(".")
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CatchUpScreen(
    catchUpChannels:    List<ChannelEntity>,
    selectedDateKey:    String?,
    onPlayTimeshift:    (streamUrl: String, programmeName: String, subtitle: String, description: String, durationMs: Long) -> Unit,
    onGridViewReady:    (app.nexstream.player.ui.components.PosterGridView?) -> Unit = {},
    onContentFocused:   () -> Unit = {},  // called when grid receives focus — MainScreen sets zone=CONTENT
    onRequestSidebarFocus: () -> Unit = {},
    onDialogOpen:       (Boolean) -> Unit = {},
    onDownloadEpisode:  ((streamUrl: String, title: String) -> Unit)? = null,
    showSearch:              Boolean = false,
    restoreTick:             Int = 0,
    onKeyboardDismissed:      (() -> Unit)? = null,
    onKeyboardDismissedEmpty: (() -> Unit)? = null,
    onDateSelect: (String?) -> Unit = {},
    viewModel:          CatchUpViewModel     = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel   = hiltViewModel()
) {
    val nsTheme     = LocalNexStreamTheme.current
    val sTheme      = nsTheme.sidebar
    val allListings by viewModel.getCatchUpListings().collectAsState()
    val isLoading   by viewModel.isLoadingListings.collectAsState()
    val thumbnails  by viewModel.thumbnails.collectAsState()
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    val playlists    by viewModel.playlists.collectAsState()

    val today      = remember { todayStart() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(catchUpChannels.map { it.id }) {
        if (catchUpChannels.isNotEmpty()) viewModel.fetchAllCatchUpListings(catchUpChannels)
    }

    var searchQuery    by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var showKeyboard   by remember { mutableStateOf(false) }

    LaunchedEffect(showSearch) {
        if (showSearch) { searchQuery = ""; debouncedQuery = ""; kotlinx.coroutines.delay(100); showKeyboard = true }
        else { showKeyboard = false }
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
            if (searchQuery.isNotBlank()) onKeyboardDismissed?.invoke()
            else onKeyboardDismissedEmpty?.invoke()
        }
        wasShowingKeyboard = showKeyboard
    }

    // Programmes filtered by date AND only those with a TMDB poster
    val selectedDateMs = if (selectedDateKey != null && selectedDateKey != "FAVOURITES")
        selectedDateKey.toLongOrNull() else null

    val programmesForView = remember(allListings, selectedDateMs, selectedDateKey, watchlistIds, debouncedQuery) {
        val dateFiltered = if (selectedDateMs == null) allListings
        else { val end = selectedDateMs + 86_400_000L; allListings.filter { it.startTime >= selectedDateMs && it.startTime < end } }

        val dedupedByTitle = dateFiltered.distinctBy { it.title }.sortedBy { it.title }

        val listByDate = if (selectedDateKey == "FAVOURITES") {
            dedupedByTitle.filter { prog -> prog.title.hashCode().toString() in watchlistIds }
        } else dedupedByTitle

        if (showSearch && debouncedQuery.isNotBlank())
            listByDate.filter { it.title.contains(debouncedQuery, ignoreCase = true) }
        else listByDate
    }

    var selectedProgramme     by remember { mutableStateOf<String?>(null) }
    var lastProgrammeForDialog by rememberSaveable { mutableStateOf<String?>(null) }

    // Reopen dialog when player closes — mirrors SeriesScreen.restoreTick
    LaunchedEffect(restoreTick) {
        if (restoreTick > 0 && lastProgrammeForDialog != null && selectedProgramme == null) {
            selectedProgramme = lastProgrammeForDialog
        }
    }
    // Also reopen when listings load after restore tick (listings may not be ready yet)
    LaunchedEffect(allListings, restoreTick) {
        if (restoreTick > 0 && lastProgrammeForDialog != null && selectedProgramme == null
            && allListings.any { it.title == lastProgrammeForDialog }) {
            selectedProgramme = lastProgrammeForDialog
        }
    }

    val episodesForProgramme = remember(allListings, selectedProgramme) {
        if (selectedProgramme == null) emptyList()
        else allListings.filter { it.title == selectedProgramme }
            .distinctBy { "${it.startTime}|${it.endTime}" }
            .sortedByDescending { it.startTime }
    }

    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    BackHandler(enabled = showKeyboard) { showKeyboard = false }
    BackHandler(enabled = selectedProgramme != null) { selectedProgramme = null }

    LaunchedEffect(selectedProgramme) { onDialogOpen(selectedProgramme != null) }

    if (selectedProgramme != null && episodesForProgramme.isNotEmpty()) {
        val isBookmarked = selectedProgramme!!.hashCode().toString() in watchlistIds
        val catchUpPlaylistName = if (playlists.size > 1) {
            val firstChannel = episodesForProgramme.firstOrNull()?.let { ep ->
                catchUpChannels.find { ch -> ch.epgChannelId == ep.channelId }
            }
            playlists.find { it.id == firstChannel?.playlistId }?.name
        } else null
        CatchUpDetailsDialog(
            programmeName = selectedProgramme!!,
            posterUrl     = thumbnails[selectedProgramme!!],
            episodes      = episodesForProgramme,
            channels      = catchUpChannels,
            today         = today,
            timeFormat    = timeFormat,
            isBookmarked  = isBookmarked,
            playlistName  = catchUpPlaylistName,
            onToggleWatchlist = {
                watchlistViewModel.toggleWatchlist(
                    WatchlistEntity(
                        id        = selectedProgramme!!.hashCode().toString(),
                        profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                        type      = WatchlistType.SERIES,
                        name      = selectedProgramme!!,
                        posterUrl = thumbnails[selectedProgramme!!],
                        streamUrl = null
                    ),
                    isBookmarked
                )
            },
            viewModel         = viewModel,
            onDownloadEpisode = onDownloadEpisode,
            onDismiss  = { selectedProgramme = null },
            onPlay     = { url, name, subtitle, desc, dur ->
                selectedProgramme = null  // close dialog while player open
                onPlayTimeshift(url, name, subtitle, desc, dur)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val uiStyle = LocalUiStyle.current
        if (uiStyle == UiStyle.MODERN) {
            ModernCatchUpContent(
                programmes      = programmesForView,
                thumbnails      = thumbnails,
                catchUpChannels = catchUpChannels,
                isLoading       = isLoading,
                today           = today,
                timeFormat      = timeFormat,
                onProgrammeClick = { title -> selectedProgramme = title },
            )
        } else {
        Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = when {
                    selectedDateKey == "FAVOURITES" -> "Favourites"
                    selectedDateKey != null         -> catchUpDateLabel(selectedDateKey.toLong(), today)
                    else                            -> "All"
                },
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        when {
            catchUpChannels.isEmpty() -> EmptyState(Icons.Default.Replay, "No catch-up channels available")
            isLoading                 -> LoadingState()
            programmesForView.isEmpty() -> EmptyState(Icons.Default.Schedule, "No programmes available")
            else -> {
                // Map programmes to PosterItems for PosterGridView — only show cards that have a TMDB poster
                val posterItems = remember(programmesForView, thumbnails) {
                    programmesForView
                        .filter { thumbnails[it.title] != null }
                        .map { prog ->
                            app.nexstream.player.ui.components.PosterItem(
                                id        = prog.title,
                                name      = prog.title,
                                posterUrl = thumbnails[prog.title],
                                badge     = catchUpChannels.firstOrNull { it.id == prog.channelId }?.name?.take(14)
                            )
                        }
                }
                val primaryColor       = MaterialTheme.colorScheme.primary.toArgb()
                val onPrimaryColor     = MaterialTheme.colorScheme.onPrimary.toArgb()
                val tertiaryColor      = MaterialTheme.colorScheme.tertiary.toArgb()
                val onTertiaryColor    = MaterialTheme.colorScheme.onTertiary.toArgb()
                val surfaceVariantColor= MaterialTheme.colorScheme.surfaceVariant.toArgb()
                val surfaceColor       = MaterialTheme.colorScheme.surface.toArgb()
                val onSurfaceColor     = MaterialTheme.colorScheme.onSurface.toArgb()
                val gridViewRef        = remember { mutableStateOf<app.nexstream.player.ui.components.PosterGridView?>(null) }
                // Use a holder so update{} can refresh the callback with latest state
                val onLongClickRef     = remember { mutableStateOf<((String) -> Unit)?>(null) }
                onLongClickRef.value   = { title ->
                    val isBookmarked = title.hashCode().toString() in watchlistIds
                    watchlistViewModel.toggleWatchlist(
                        WatchlistEntity(
                            id        = title.hashCode().toString(),
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type      = WatchlistType.SERIES,
                            name      = title,
                            posterUrl = thumbnails[title],
                            streamUrl = null
                        ), isBookmarked
                    )
                }

                // Scroll to top when date changes — do NOT steal focus
                LaunchedEffect(selectedDateKey) {
                    gridViewRef.value?.scrollToIndex(0)
                }

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        app.nexstream.player.ui.components.PosterGridView(ctx).also { gridViewRef.value = it; onGridViewReady(it) }.apply {
                            setColumnCount(6)
                            blockFocus()
                            callbacks = object : app.nexstream.player.ui.components.PosterGridCallbacks {
                                override fun onItemClick(item: app.nexstream.player.ui.components.PosterItem, index: Int) {
                                    selectedProgramme = item.id
                                }
                                override fun onItemLongClick(item: app.nexstream.player.ui.components.PosterItem, index: Int) {
                                    onLongClickRef.value?.invoke(item.id)
                                }
                                override fun onItemFocused(index: Int) {
                                    onContentFocused()  // grid has focus → zone = CONTENT
                                }
                                override fun onLeftEdge()  { onRequestSidebarFocus() }
                                override fun onTopEdge()   {}  // trapped — don't exit to header
                            }
                        }
                    },
                    update = { view ->
                        view.primaryColor        = primaryColor
                        view.onPrimaryColor      = onPrimaryColor
                        view.tertiaryColor       = tertiaryColor
                        view.onTertiaryColor     = onTertiaryColor
                        view.surfaceVariantColor = surfaceVariantColor
                        view.surfaceColor        = surfaceColor
                        view.onSurfaceColor      = onSurfaceColor
                        val prevSize = view.itemCount
                        view.setItems(posterItems)
                        if (prevSize != posterItems.size) {
                            view.scrollToIndex(0)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                        .focusable(false)
                )
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
            hint          = "Search catch-up…",
            modifier      = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LoadingState(message: String = "Loading catch-up…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Details dialog — styled to match SeriesDetailsDialog
// ─────────────────────────────────────────────────────────────────────────────

private const val LOW_SPACE_BUFFER_CU = 250L * 1024 * 1024

private fun getAvailableStorageBytesCu(): Long {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return stat.availableBlocksLong * stat.blockSizeLong
}

private suspend fun getCatchUpSizeBytes(streamUrl: String): Long = withContext(Dispatchers.IO) {
    try {
        val conn = URL(streamUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        conn.connectTimeout = 8_000; conn.readTimeout = 8_000
        conn.instanceFollowRedirects = true; conn.connect()
        val size = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        conn.disconnect()
        if (size > 0) return@withContext size
        val getConn = URL(streamUrl).openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        getConn.setRequestProperty("Range", "bytes=0-0")
        getConn.connectTimeout = 8_000; getConn.readTimeout = 8_000
        getConn.instanceFollowRedirects = true; getConn.connect()
        val contentRange = getConn.getHeaderField("Content-Range")
        getConn.disconnect()
        contentRange?.substringAfterLast("/")?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
    } catch (_: Exception) { -1L }
}

private fun formatBytesCu(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when { gb >= 1.0 -> "%.2f GB".format(gb); mb >= 1.0 -> "%.0f MB".format(mb); else -> "%.0f KB".format(bytes / 1024.0) }
}

// ── InfoBarState — mirrors SeriesDetailsDialog ───────────────────────────────
private sealed class CatchUpInfoBarState {
    object Idle        : CatchUpInfoBarState()
    object PlayChoice  : CatchUpInfoBarState()
    object CheckingSize: CatchUpInfoBarState()
    data class StorageInfo(val size: Long, val available: Long, val notEnough: Boolean, val lowAfter: Boolean) : CatchUpInfoBarState()
    object LowSpaceWarning : CatchUpInfoBarState()
}

// ── Action pill — matches DialogActionPill in SeriesDetailsDialog exactly ────
@Composable
private fun CatchUpDialogPill(
    icon:       ImageVector?,
    label:      String,
    isSelected: Boolean,
    isPressed:  Boolean = false,
    accent:     Color,
    onClick:    () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (isSelected) accent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(14.dp),
                tint               = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            text       = label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
        )
    }
}

// ── Overlay button — matches SeriesDetailsDialog style ────────────────────────
@Composable
private fun CatchUpOverlayButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) {
            Text(label, fontSize = 12.sp)
        }
    }
}

private const val CATCHUP_GRID_COLS = 4

@Composable
fun CatchUpDetailsDialog(
    programmeName:     String,
    posterUrl:         String?,
    episodes:          List<ProgramEntity>,
    channels:          List<ChannelEntity>,
    today:             Long,
    timeFormat:        SimpleDateFormat,
    isBookmarked:      Boolean,
    playlistName:      String? = null,
    onToggleWatchlist: () -> Unit,
    viewModel:         CatchUpViewModel,
    onDownloadEpisode: ((streamUrl: String, title: String) -> Unit)? = null,
    onDismiss:         () -> Unit,
    onPlay:            (streamUrl: String, programmeName: String, subtitle: String, description: String, durationMs: Long) -> Unit
) {
    val accent     = LocalNsAccent.current
    val background = LocalNsBackground.current
    val sevenDaysAgo = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val limitedEpisodes = remember(episodes) {
        episodes.filter { it.startTime >= sevenDaysAgo }
            .distinctBy { "${it.startTime}|${it.endTime}" }
            .sortedByDescending { it.startTime }
    }

    val barButtonCount   = 2  // 0 = Close, 1 = My List
    var selectedButton   by remember { mutableIntStateOf(1) }
    var pressedButton    by remember { mutableStateOf<Int?>(null) }
    var inGrid           by remember { mutableStateOf(false) }
    var focusedGridIndex by remember { mutableIntStateOf(0) }
    var infoBarState     by remember { mutableStateOf<CatchUpInfoBarState>(CatchUpInfoBarState.Idle) }
    var overlayButton    by remember { mutableIntStateOf(0) }

    val focusedEpisode = limitedEpisodes.getOrNull(focusedGridIndex)

    // infoBarState reset on D-pad focus change is handled directly in onFocusChanged below

    val dialogFocus = remember { FocusRequester() }
    val gridFRs     = remember(limitedEpisodes.size) { mutableMapOf<Int, FocusRequester>() }
    val gridState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        if (limitedEpisodes.isNotEmpty()) {
            inGrid = true; focusedGridIndex = 0
            try { gridFRs[0]?.requestFocus() } catch (_: Exception) {
                try { dialogFocus.requestFocus() } catch (_: Exception) {}
            }
        } else {
            try { dialogFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    fun buildUrl(ep: ProgramEntity): String? {
        val ch = channels.firstOrNull { it.epgChannelId == ep.channelId }
            ?: channels.firstOrNull { it.id == ep.channelId }
            ?: channels.firstOrNull()
            ?: return null
        android.util.Log.d("CatchUpDebug", "=== buildUrl ===")
        android.util.Log.d("CatchUpDebug", "Programme: title=${ep.title} channelId=${ep.channelId} start=${ep.startTime} end=${ep.endTime}")
        android.util.Log.d("CatchUpDebug", "Channel: name=${ch.name} id=${ch.id} epgChannelId=${ch.epgChannelId} streamId=${ch.streamId} streamUrl=${ch.streamUrl}")
        val url = viewModel.buildTimeshiftUrl(ch, ep)
        android.util.Log.d("CatchUpDebug", "Built URL: $url")
        return url
    }

    fun startDownloadFlow(ep: ProgramEntity) {
        infoBarState = CatchUpInfoBarState.CheckingSize; overlayButton = 1
        scope.launch {
            val url       = buildUrl(ep) ?: run { infoBarState = CatchUpInfoBarState.Idle; return@launch }
            val available = getAvailableStorageBytesCu()
            val size      = getCatchUpSizeBytes(url)
            val notEnough = size > 0 && available < size
            val lowAfter  = size > 0 && available >= size && (available - size) < LOW_SPACE_BUFFER_CU
            infoBarState  = CatchUpInfoBarState.StorageInfo(size, available, notEnough, lowAfter)
            overlayButton = if (notEnough) 0 else 1
        }
    }

    fun playEp(ep: ProgramEntity) {
        val url      = buildUrl(ep) ?: return
        val dateLabel = catchUpDateLabel(startOfDay(ep.startTime), today)
        val timeFmt  = timeFormat.format(java.util.Date(ep.startTime))
        val durMins  = ((ep.endTime - ep.startTime) / 60_000L).toInt()
        onDismiss()
        onPlay(url, programmeName, "$dateLabel · $timeFmt · ${formatDur(durMins)}", ep.description ?: "", ep.endTime - ep.startTime)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true,
        )
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
        ) {
            // ── Full-bleed backdrop ───────────────────────────────────────
            if (posterUrl != null) {
                AsyncImage(model = posterUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter)
            }

            // ── Gradient overlay ──────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f  to Color.Black.copy(alpha = 0.40f),
                            0.40f to Color.Black.copy(alpha = 0.55f),
                            0.72f to Color.Black.copy(alpha = 0.80f),
                            1.0f  to Color.Black.copy(alpha = 0.97f),
                        )
                    )
                )
            )

            // ── Floating Close at top-right ───────────────────────────────
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                CatchUpDialogPill(
                    icon       = Icons.Default.Close,
                    label      = "Close",
                    isSelected = !inGrid && selectedButton == 0,
                    isPressed  = pressedButton == 0,
                    accent     = accent,
                    onClick    = onDismiss,
                )
            }

            // ── Content column ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false

                        val overlayActive = infoBarState !is CatchUpInfoBarState.Idle &&
                                infoBarState !is CatchUpInfoBarState.CheckingSize
                        if (overlayActive) {
                            val maxBtn = when (infoBarState) {
                                is CatchUpInfoBarState.PlayChoice      -> if (onDownloadEpisode != null) 2 else 1
                                is CatchUpInfoBarState.StorageInfo     -> 2
                                is CatchUpInfoBarState.LowSpaceWarning -> 2
                                else -> 1
                            }
                            when (e.key) {
                                Key.DirectionLeft  -> { overlayButton = (overlayButton - 1 + maxBtn) % maxBtn; true }
                                Key.DirectionRight -> { overlayButton = (overlayButton + 1) % maxBtn; true }
                                Key.DirectionUp    -> { infoBarState = CatchUpInfoBarState.Idle; true }
                                Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                    val ep = focusedEpisode
                                    when (val st = infoBarState) {
                                        is CatchUpInfoBarState.PlayChoice -> when (overlayButton) {
                                            0 -> { if (ep != null) playEp(ep) }
                                            1 -> { if (ep != null && onDownloadEpisode != null) startDownloadFlow(ep) }
                                        }
                                        is CatchUpInfoBarState.StorageInfo -> when (overlayButton) {
                                            0 -> infoBarState = CatchUpInfoBarState.Idle
                                            1 -> if (!st.notEnough && ep != null) {
                                                val url = buildUrl(ep) ?: return@onKeyEvent true
                                                val lbl = "$programmeName · ${catchUpDateLabel(startOfDay(ep.startTime), today)}"
                                                if (st.lowAfter) infoBarState = CatchUpInfoBarState.LowSpaceWarning
                                                else { onDownloadEpisode?.invoke(url, lbl); infoBarState = CatchUpInfoBarState.Idle }
                                            }
                                        }
                                        is CatchUpInfoBarState.LowSpaceWarning -> when (overlayButton) {
                                            0 -> infoBarState = CatchUpInfoBarState.Idle
                                            1 -> if (ep != null) {
                                                val url = buildUrl(ep) ?: return@onKeyEvent true
                                                val lbl = "$programmeName · ${catchUpDateLabel(startOfDay(ep.startTime), today)}"
                                                onDownloadEpisode?.invoke(url, lbl)
                                                infoBarState = CatchUpInfoBarState.Idle
                                            }
                                        }
                                        else -> {}
                                    }
                                    true
                                }
                                Key.Back -> { infoBarState = CatchUpInfoBarState.Idle; true }
                                else -> false
                            }
                        } else {
                            when (e.key) {
                                Key.DirectionLeft -> {
                                    if (!inGrid) { selectedButton = (selectedButton - 1 + barButtonCount) % barButtonCount; true }
                                    else {
                                        if (focusedGridIndex > 0) {
                                            focusedGridIndex--
                                            scope.launch { gridState.animateScrollToItem(focusedGridIndex); kotlinx.coroutines.delay(40); gridFRs[focusedGridIndex]?.requestFocus() }
                                            true
                                        } else false
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (!inGrid) { selectedButton = (selectedButton + 1) % barButtonCount; true }
                                    else {
                                        if (focusedGridIndex < limitedEpisodes.lastIndex) {
                                            focusedGridIndex++
                                            scope.launch { gridState.animateScrollToItem(focusedGridIndex); kotlinx.coroutines.delay(40); gridFRs[focusedGridIndex]?.requestFocus() }
                                            true
                                        } else false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (!inGrid && limitedEpisodes.isNotEmpty()) {
                                        inGrid = true
                                        val target = focusedGridIndex.coerceIn(0, limitedEpisodes.lastIndex)
                                        scope.launch { gridState.animateScrollToItem(target); kotlinx.coroutines.delay(60); gridFRs[target]?.requestFocus() }
                                        true
                                    } else false
                                }
                                Key.DirectionUp -> {
                                    if (inGrid) {
                                        inGrid = false
                                        try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    } else if (!inGrid && limitedEpisodes.isNotEmpty()) {
                                        inGrid = true
                                        focusedGridIndex = focusedGridIndex.coerceIn(0, limitedEpisodes.lastIndex)
                                        scope.launch { gridState.animateScrollToItem(focusedGridIndex); kotlinx.coroutines.delay(60); gridFRs[focusedGridIndex]?.requestFocus() }
                                        true
                                    } else false
                                }
                                Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                    if (!inGrid) {
                                        val btn = selectedButton
                                        pressedButton = btn
                                        scope.launch {
                                            kotlinx.coroutines.delay(120); pressedButton = null
                                            when (btn) { 0 -> onDismiss(); 1 -> onToggleWatchlist() }
                                        }
                                        true
                                    } else {
                                        val ep = limitedEpisodes.getOrNull(focusedGridIndex)
                                        if (ep != null) { infoBarState = CatchUpInfoBarState.PlayChoice; overlayButton = 0 }
                                        true
                                    }
                                }
                                Key.Back -> { onDismiss(); true }
                                else -> false
                            }
                        }
                    }
            ) {
                Spacer(Modifier.weight(1f))

                // ── Programme info ────────────────────────────────────────
                Column(
                    modifier            = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(programmeName, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${limitedEpisodes.size} episode${if (limitedEpisodes.size != 1) "s" else ""} · last 7 days",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.70f),
                    )
                    if (!playlistName.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PlaylistPlay, null, modifier = Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.45f))
                            Text(playlistName, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        CatchUpDialogPill(
                            icon       = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label      = if (isBookmarked) "Remove from My List" else "My List",
                            isSelected = !inGrid && selectedButton == 1,
                            isPressed  = pressedButton == 1,
                            accent     = accent,
                            onClick    = onToggleWatchlist,
                        )
                    }
                }

                // ── Info bar ──────────────────────────────────────────────
                val infoBarHeight = when (infoBarState) {
                    is CatchUpInfoBarState.StorageInfo, is CatchUpInfoBarState.LowSpaceWarning -> 72.dp
                    else -> 52.dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(infoBarHeight)
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (val st = infoBarState) {
                        is CatchUpInfoBarState.Idle -> {
                            if (focusedEpisode != null) {
                                val ep        = focusedEpisode
                                val dateLabel = catchUpDateLabel(startOfDay(ep.startTime), today)
                                val timeLabel = timeFormat.format(java.util.Date(ep.startTime))
                                val durMins   = ((ep.endTime - ep.startTime) / 60_000L).toInt()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text("$dateLabel · $timeLabel · ${formatDur(durMins)}",
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!ep.description.isNullOrEmpty())
                                        Text(ep.description!!, fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                Text(stringResource(R.string.catchup_select_episode), fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.40f),
                                    modifier = Modifier.align(Alignment.CenterStart))
                            }
                        }
                        is CatchUpInfoBarState.PlayChoice -> {
                            Row(modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CatchUpOverlayButton("▶  Play", overlayButton == 0) { focusedEpisode?.let { playEp(it) } }
                                if (onDownloadEpisode != null)
                                    CatchUpOverlayButton("⬇  Download", overlayButton == 1) { focusedEpisode?.let { startDownloadFlow(it) } }
                            }
                        }
                        is CatchUpInfoBarState.CheckingSize -> {
                            Row(modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Text(stringResource(R.string.catchup_checking_size), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                        is CatchUpInfoBarState.StorageInfo -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Episode: ${formatBytesCu(st.size)}  ·  Available: ${formatBytesCu(st.available)}",
                                    fontSize = 12.sp,
                                    color = when {
                                        st.notEnough -> MaterialTheme.colorScheme.error
                                        st.lowAfter  -> MaterialTheme.colorScheme.tertiary
                                        else         -> Color.White.copy(alpha = 0.75f)
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CatchUpOverlayButton("Cancel",   overlayButton == 0) { infoBarState = CatchUpInfoBarState.Idle }
                                    CatchUpOverlayButton("Download", overlayButton == 1, enabled = !st.notEnough) {}
                                }
                            }
                        }
                        is CatchUpInfoBarState.LowSpaceWarning -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Low storage — less than ${formatBytesCu(LOW_SPACE_BUFFER_CU)} will remain.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CatchUpOverlayButton("Cancel",          overlayButton == 0) { infoBarState = CatchUpInfoBarState.Idle }
                                    CatchUpOverlayButton("Continue Anyway", overlayButton == 1) {}
                                }
                            }
                        }
                    }
                }

                // ── Episode grid ──────────────────────────────────────────
                when {
                    limitedEpisodes.isEmpty() -> Box(
                        Modifier.fillMaxWidth().height(200.dp).background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No episodes in the last 7 days", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    else -> LazyRow(
                        state                 = gridState,
                        modifier              = Modifier.fillMaxWidth().height(190.dp).background(Color.Black.copy(alpha = 0.70f)),
                        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(limitedEpisodes, key = { idx, ep -> "${idx}_${ep.startTime}_${ep.endTime}" }) { index, episode ->
                            val fr      = remember { FocusRequester() }
                            LaunchedEffect(fr) { gridFRs[index] = fr }
                            val isFocused = inGrid && focusedGridIndex == index
                            val dateLabel = remember(episode.startTime) { catchUpDateLabel(startOfDay(episode.startTime), today) }
                            val timeLabel = remember(episode.startTime) { timeFormat.format(java.util.Date(episode.startTime)) }
                            val durMins   = ((episode.endTime - episode.startTime) / 60_000L).toInt()
                            val chName    = remember(episode.channelId) {
                                (channels.firstOrNull { it.epgChannelId == episode.channelId }
                                    ?: channels.firstOrNull { it.id == episode.channelId })?.name?.take(12)
                            }

                            Box(
                                modifier = Modifier
                                    .width(236.dp)
                                    .fillParentMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) accent else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .focusRequester(fr)
                                    .focusable()
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused) {
                                            focusedGridIndex = index; inGrid = true
                                            infoBarState = CatchUpInfoBarState.Idle
                                        }
                                    }
                                    .clickable {
                                        focusedGridIndex = index; inGrid = true
                                        infoBarState = CatchUpInfoBarState.PlayChoice; overlayButton = 0
                                    }
                            ) {
                                if (posterUrl != null) {
                                    AsyncImage(model = posterUrl, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                        alignment = androidx.compose.ui.Alignment.TopCenter)
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
                                }
                                // Dark scrim — lighter on focus
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (isFocused) 0.10f else 0.38f)))
                                // Date badge — top start
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart).padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(dateLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                // Channel badge — top end
                                if (chName != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd).padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent.copy(alpha = 0.85f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(chName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                // Time + duration — bottom start
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart).padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text("$timeLabel · ${formatDur(durMins)}", fontSize = 11.sp, color = Color.White)
                                }
                                // Play icon on focus
                                if (isFocused) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                                        modifier = Modifier.size(32.dp).align(Alignment.Center))
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}


private fun formatDur(m: Int) = when {
    m / 60 > 0 && m % 60 > 0 -> "${m / 60}h ${m % 60}m"
    m / 60 > 0                -> "${m / 60}h"
    else                      -> "${m}m"
}