package app.nexstream.player.ui.screens.catchup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
                val deferreds   = channels.map { channel ->
                    async(Dispatchers.IO) {
                        try {
                            val epgId = channel.epgChannelId ?: return@async
                            val result = repository.getProgramsForChannelInRange(epgId, start, now)
                                .first()
                                .filter { it.endTime <= now && it.startTime >= start }
                            accumulated.withLock {
                                partial.addAll(result)
                                // Emit partial so dates/panel update immediately
                                _listings.value = partial.distinctBy { "${it.title}|${it.startTime}" }
                                    .sortedByDescending { it.startTime }
                            }
                        } catch (_: Exception) {}
                    }
                }
                deferreds.awaitAll()
            } // end coroutineScope
            val combined = partial

            val deduped = combined
                .distinctBy { "${it.title}|${it.startTime}" }
                .sortedByDescending { it.startTime }
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
                                _thumbnails.value = cached.toMap()
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CatchUp", "TMDB '$title': ${e::class.simpleName}")
                }
            }
            if (newEntries.isNotEmpty()) tmdbDao.upsertAll(newEntries)
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
    onDialogOpen:       (Boolean) -> Unit = {},
    onDownloadEpisode:  ((streamUrl: String, title: String) -> Unit)? = null,
    restoreTick:        Int = 0,
    viewModel:          CatchUpViewModel     = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel   = hiltViewModel()
) {
    val nsTheme     = LocalNexStreamTheme.current
    val sTheme      = nsTheme.sidebar
    val allListings by viewModel.getCatchUpListings().collectAsState()
    val isLoading   by viewModel.isLoadingListings.collectAsState()
    val thumbnails  by viewModel.thumbnails.collectAsState()
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()

    val today      = remember { todayStart() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(catchUpChannels.map { it.id }) {
        if (catchUpChannels.isNotEmpty()) viewModel.fetchAllCatchUpListings(catchUpChannels)
    }

    // Programmes filtered by date AND only those with a TMDB poster
    val selectedDateMs = if (selectedDateKey != null && selectedDateKey != "FAVOURITES")
        selectedDateKey.toLongOrNull() else null

    val programmesForView = remember(allListings, selectedDateMs, selectedDateKey, thumbnails, watchlistIds) {
        val dateFiltered = if (selectedDateMs == null) allListings
        else { val end = selectedDateMs + 86_400_000L; allListings.filter { it.startTime >= selectedDateMs && it.startTime < end } }

        val withPosters = dateFiltered.filter { thumbnails.containsKey(it.title) }

        val dedupedByTitle = withPosters.distinctBy { it.title }.sortedBy { it.title }

        // Favourites filter — programme title must be in watchlist
        if (selectedDateKey == "FAVOURITES") {
            dedupedByTitle.filter { prog -> watchlistIds.any { id -> id.contains(prog.title) } }
        } else dedupedByTitle
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

    BackHandler(enabled = selectedProgramme != null) { selectedProgramme = null }

    LaunchedEffect(selectedProgramme) { onDialogOpen(selectedProgramme != null) }

    if (selectedProgramme != null && episodesForProgramme.isNotEmpty()) {
        val isBookmarked = watchlistIds.any { it.contains(selectedProgramme!!) }
        CatchUpDetailsDialog(
            programmeName = selectedProgramme!!,
            posterUrl     = thumbnails[selectedProgramme!!],
            episodes      = episodesForProgramme,
            channels      = catchUpChannels,
            today         = today,
            timeFormat    = timeFormat,
            isBookmarked  = isBookmarked,
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

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = when {
                    selectedDateKey == "FAVOURITES" -> "Catch Up · Favourites"
                    selectedDateKey != null         -> "Catch Up · ${catchUpDateLabel(selectedDateKey.toLong(), today)}"
                    else                            -> "Catch Up · All"
                },
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        when {
            catchUpChannels.isEmpty() -> EmptyState(Icons.Default.Replay, "No catch-up channels available")
            isLoading                 -> LoadingState()
            programmesForView.isEmpty() && thumbnails.isEmpty() -> LoadingState("Loading posters…")
            programmesForView.isEmpty() -> EmptyState(Icons.Default.Schedule, "No programmes available")
            else -> {
                // Map programmes to PosterItems for PosterGridView — same as Series/Movies
                val posterItems = remember(programmesForView, thumbnails) {
                    programmesForView.map { prog ->
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
                    val isBookmarked = watchlistIds.any { it.contains(title) }
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

                // Scroll to top when date changes
                LaunchedEffect(selectedDateKey) {
                    gridViewRef.value?.scrollToIndex(0)
                    gridViewRef.value?.requestItemFocus(0)
                }

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        app.nexstream.player.ui.components.PosterGridView(ctx).also { gridViewRef.value = it; onGridViewReady(it) }.apply {
                            setColumnCount(5)
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
                                override fun onLeftEdge()  {}  // trapped — don't exit to rail
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
                            view.requestItemFocus(0)
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
// Details dialog — mirrors SeriesDetailsDialog with My List button, no seasons
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

@Composable
private fun CatchUpOverlayButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
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
    onToggleWatchlist: () -> Unit,
    viewModel:         CatchUpViewModel,
    onDownloadEpisode: ((streamUrl: String, title: String) -> Unit)? = null,
    onDismiss:         () -> Unit,
    onPlay:            (streamUrl: String, programmeName: String, subtitle: String, description: String, durationMs: Long) -> Unit
) {
    val sevenDaysAgo = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val limitedEpisodes = remember(episodes) {
        episodes.filter { it.startTime >= sevenDaysAgo }
            .distinctBy { "${it.startTime}|${it.endTime}" }
            .sortedByDescending { it.startTime }
    }

    // Focus state — mirrors SeriesDetailsDialog exactly
    val barButtonCount   = 2  // Close | My List
    var selectedButton   by remember { mutableIntStateOf(1) } // default My List (rightmost)
    var inGrid           by remember { mutableStateOf(false) }
    var focusedGridIndex by remember { mutableIntStateOf(0) }
    var infoBarState     by remember { mutableStateOf<CatchUpInfoBarState>(CatchUpInfoBarState.Idle) }
    var overlayButton    by remember { mutableIntStateOf(0) }

    val focusedEpisode = limitedEpisodes.getOrNull(focusedGridIndex)

    LaunchedEffect(focusedGridIndex) { infoBarState = CatchUpInfoBarState.Idle }

    val dialogFocus      = remember { FocusRequester() }
    val closeButtonFR    = remember { FocusRequester() }
    val gridFRs          = remember(limitedEpisodes.size) { mutableMapOf<Int, FocusRequester>() }
    val gridState        = rememberLazyGridState()
    val scope            = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        // Focus first grid item on load — this is working correctly
        if (limitedEpisodes.isNotEmpty()) {
            inGrid = true
            focusedGridIndex = 0
            try { gridFRs[0]?.requestFocus() } catch (_: Exception) {
                try { dialogFocus.requestFocus() } catch (_: Exception) {}
            }
        } else {
            try { dialogFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    fun buildUrl(ep: ProgramEntity): String? {
        // ProgramEntity.channelId = epgChannelId string (e.g. "itv1.uk") from XMLTV parser
        // Match by epgChannelId first, then fall back to id
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
        infoBarState = CatchUpInfoBarState.CheckingSize
        overlayButton = 1
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
        val url       = buildUrl(ep) ?: return
        val dateLabel = catchUpDateLabel(startOfDay(ep.startTime), today)
        val timeFmt   = timeFormat.format(java.util.Date(ep.startTime))
        val durMins   = ((ep.endTime - ep.startTime) / 60_000L).toInt()
        val subtitle  = "$dateLabel · $timeFmt · ${formatDur(durMins)}"
        val desc      = ep.description ?: ""
        onDismiss()
        onPlay(url, programmeName, subtitle, desc, ep.endTime - ep.startTime)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true
        )
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.90f).fillMaxHeight(0.90f),
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false

                        // ── Overlay active — intercept all nav ─────────────────
                        val overlayActive = infoBarState !is CatchUpInfoBarState.Idle
                        if (overlayActive) {
                            val maxBtn = when (infoBarState) {
                                is CatchUpInfoBarState.PlayChoice     -> if (onDownloadEpisode != null) 2 else 1
                                is CatchUpInfoBarState.StorageInfo    -> 2
                                is CatchUpInfoBarState.LowSpaceWarning-> 2
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
                            // ── Normal navigation ──────────────────────────────
                            when (e.key) {
                                Key.DirectionLeft -> {
                                    if (!inGrid) { selectedButton = (selectedButton - 1 + barButtonCount) % barButtonCount; true }
                                    else {
                                        val col = focusedGridIndex % CATCHUP_GRID_COLS
                                        if (col > 0) { focusedGridIndex--; scope.launch { gridFRs[focusedGridIndex]?.requestFocus() }; true }
                                        else false
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (!inGrid) { selectedButton = (selectedButton + 1) % barButtonCount; true }
                                    else {
                                        val col = focusedGridIndex % CATCHUP_GRID_COLS
                                        if (col < CATCHUP_GRID_COLS - 1 && focusedGridIndex < limitedEpisodes.lastIndex) {
                                            focusedGridIndex++; scope.launch { gridFRs[focusedGridIndex]?.requestFocus() }; true
                                        } else false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (!inGrid && limitedEpisodes.isNotEmpty()) {
                                        inGrid = true
                                        val target = focusedGridIndex.coerceIn(0, limitedEpisodes.lastIndex)
                                        scope.launch {
                                            gridState.animateScrollToItem(target)
                                            kotlinx.coroutines.delay(60)
                                            gridFRs[target]?.requestFocus()
                                        }
                                        true
                                    } else if (inGrid) {
                                        val next = focusedGridIndex + CATCHUP_GRID_COLS
                                        if (next < limitedEpisodes.size) {
                                            focusedGridIndex = next
                                            scope.launch { gridState.animateScrollToItem(next); kotlinx.coroutines.delay(40); gridFRs[next]?.requestFocus() }
                                            true
                                        } else {
                                            // Bottom of grid → action bar
                                            inGrid = false; selectedButton = barButtonCount - 1
                                            try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                    } else false
                                }
                                Key.DirectionUp -> {
                                    if (inGrid) {
                                        val prev = focusedGridIndex - CATCHUP_GRID_COLS
                                        if (prev >= 0) {
                                            focusedGridIndex = prev
                                            scope.launch { gridState.animateScrollToItem(prev); kotlinx.coroutines.delay(40); gridFRs[prev]?.requestFocus() }
                                        } else {
                                            inGrid = false
                                            try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                        }
                                        true
                                    } else if (!inGrid && limitedEpisodes.isNotEmpty()) {
                                        inGrid = true
                                        val lastRow = ((limitedEpisodes.size - 1) / CATCHUP_GRID_COLS) * CATCHUP_GRID_COLS
                                        val target = (lastRow + (focusedGridIndex % CATCHUP_GRID_COLS)).coerceAtMost(limitedEpisodes.lastIndex)
                                        focusedGridIndex = target
                                        scope.launch { gridState.animateScrollToItem(target); kotlinx.coroutines.delay(60); gridFRs[target]?.requestFocus() }
                                        true
                                    } else false
                                }
                                Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                    if (!inGrid) {
                                        when (selectedButton) {
                                            0 -> onDismiss()
                                            1 -> onToggleWatchlist()
                                        }
                                        true
                                    } else {
                                        val ep = limitedEpisodes.getOrNull(focusedGridIndex)
                                        if (ep != null) {
                                            infoBarState = CatchUpInfoBarState.PlayChoice
                                            overlayButton = 0
                                        }
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                    }
            ) {
                // ── Header / backdrop ─────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    if (posterUrl != null) {
                        AsyncImage(model = posterUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)))
                    ))
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(model = posterUrl, contentDescription = programmeName,
                                modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(programmeName, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${limitedEpisodes.size} episode${if (limitedEpisodes.size != 1) "s" else ""} · last 7 days",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }

                // ── Info bar / inline overlay — mirrors SeriesDetailsDialog ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (val st = infoBarState) {
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
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Checking size…", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is CatchUpInfoBarState.StorageInfo -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Episode: ${formatBytesCu(st.size)}  ·  Available: ${formatBytesCu(st.available)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when { st.notEnough -> MaterialTheme.colorScheme.error; st.lowAfter -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CatchUpOverlayButton("Cancel", overlayButton == 0) { infoBarState = CatchUpInfoBarState.Idle }
                                    CatchUpOverlayButton("Download", overlayButton == 1, enabled = !st.notEnough) {}
                                }
                            }
                        }
                        is CatchUpInfoBarState.LowSpaceWarning -> {
                            Column(modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Low storage — less than ${formatBytesCu(LOW_SPACE_BUFFER_CU)} will remain after download.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CatchUpOverlayButton("Cancel", overlayButton == 0) { infoBarState = CatchUpInfoBarState.Idle }
                                    CatchUpOverlayButton("Continue Anyway", overlayButton == 1) {}
                                }
                            }
                        }
                        is CatchUpInfoBarState.Idle -> {
                            if (focusedEpisode != null) {
                                val ep        = focusedEpisode
                                val dateLabel = catchUpDateLabel(startOfDay(ep.startTime), today)
                                val timeLabel = timeFormat.format(java.util.Date(ep.startTime))
                                val durMins   = ((ep.endTime - ep.startTime) / 60_000L).toInt()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text("$dateLabel · $timeLabel · ${formatDur(durMins)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!ep.description.isNullOrEmpty()) {
                                        Text(ep.description!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            } else {
                                Text("Select an episode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.align(Alignment.CenterStart))
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Episode grid ──────────────────────────────────────────────
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(CATCHUP_GRID_COLS),
                    state                 = gridState,
                    modifier              = Modifier.fillMaxWidth().weight(1f),
                    contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = limitedEpisodes,
                        key   = { idx, ep -> "${idx}_${ep.startTime}_${ep.endTime}" }
                    ) { index, episode ->
                        val fr        = remember { FocusRequester() }
                        LaunchedEffect(fr) { gridFRs[index] = fr }
                        val isFocused = inGrid && focusedGridIndex == index
                        val dateLabel = remember(episode.startTime) { catchUpDateLabel(startOfDay(episode.startTime), today) }
                        val timeLabel = remember(episode.startTime) { timeFormat.format(java.util.Date(episode.startTime)) }
                        val durMins   = ((episode.endTime - episode.startTime) / 60_000L).toInt()

                        Box(
                            modifier = Modifier
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .focusRequester(fr)
                                .focusable()
                                .onFocusChanged { fs ->
                                    if (fs.isFocused) {
                                        focusedGridIndex = index
                                        inGrid           = true
                                        infoBarState     = CatchUpInfoBarState.Idle
                                    }
                                }
                                .then(
                                    if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable {
                                    focusedGridIndex = index
                                    inGrid           = true
                                    infoBarState     = CatchUpInfoBarState.PlayChoice
                                    overlayButton    = 0
                                }
                        ) {
                            // Thumbnail — TMDB poster
                            if (posterUrl != null) {
                                AsyncImage(model = posterUrl, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                            // Dim overlay
                            Box(Modifier.fillMaxSize().background(
                                Color.Black.copy(alpha = if (isFocused) 0.15f else 0.45f)
                            ))
                            // Date badge — top left
                            Surface(modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                                Text(dateLabel, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                            }
                            // Channel name badge — top right
                            val chName = remember(episode.channelId) {
                                channels.firstOrNull { it.epgChannelId == episode.channelId }?.name
                                    ?: channels.firstOrNull { it.id == episode.channelId }?.name
                                        ?.take(12) // truncate long names
                            }
                            if (chName != null) {
                                Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                                    Text(chName, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                                }
                            }
                            // Time + duration
                            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                                shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.75f)) {
                                Text("$timeLabel · ${formatDur(durMins)}", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
                            }
                            if (isFocused) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                                    modifier = Modifier.size(28.dp).align(Alignment.Center))
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Action bar — mirrors SeriesDetailsDialog exactly ──────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.weight(1f))

                    // 0 = Close
                    val closeSelected = !inGrid && selectedButton == 0
                    OutlinedButton(
                        onClick  = onDismiss,
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (closeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor   = if (closeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border   = androidx.compose.foundation.BorderStroke(
                            width = if (closeSelected) 2.dp else 1.dp,
                            color = if (closeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.focusRequester(closeButtonFR)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Close")
                    }

                    // 1 = My List (default focus — rightmost)
                    val myListSelected = !inGrid && selectedButton == 1
                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (myListSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor   = if (myListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        border  = androidx.compose.foundation.BorderStroke(
                            width = if (myListSelected) 2.dp else 1.dp,
                            color = if (myListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isBookmarked) "Remove from My List" else "Add to My List")
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