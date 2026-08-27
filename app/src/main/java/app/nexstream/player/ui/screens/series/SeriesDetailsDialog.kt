package app.nexstream.player.ui.screens.series

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.subtitle.WhisperSubtitleManager
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.saveWhisperSubtitles
import app.nexstream.player.ui.theme.saveWhisperTranslateTo
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// Whisper only: transcribe (output in detected language) or translate (always English)
private val VOICE_SUBTITLE_MODES = listOf<Pair<Boolean?, String>>(
    null  to "None",
    false to "Transcribe",
    true  to "→ English"
)

private fun extractYoutubeId(url: String): String? {
    val patterns = listOf(
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/watch\\?.*v=([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/embed/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/v/([A-Za-z0-9_-]{11})"),
    )
    return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}

private const val LOW_SPACE_BUFFER_EP = 250L * 1024 * 1024

private fun getAvailableStorageBytesEp(): Long {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return stat.availableBlocksLong * stat.blockSizeLong
}

private suspend fun getEpisodeSizeBytes(streamUrl: String): Long = withContext(Dispatchers.IO) {
    try {
        val headConn = URL(streamUrl).openConnection() as HttpURLConnection
        headConn.requestMethod = "HEAD"
        headConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        headConn.connectTimeout = 8_000; headConn.readTimeout = 8_000
        headConn.instanceFollowRedirects = true; headConn.connect()
        val headSize = headConn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        headConn.disconnect()
        if (headSize > 0) return@withContext headSize
        val getConn = URL(streamUrl).openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        getConn.setRequestProperty("Range", "bytes=0-0")
        getConn.connectTimeout = 8_000; getConn.readTimeout = 8_000
        getConn.instanceFollowRedirects = true; getConn.connect()
        val contentRange = getConn.getHeaderField("Content-Range")
        getConn.disconnect()
        if (contentRange != null) {
            val total = contentRange.substringAfterLast("/").trim().toLongOrNull()
            if (total != null && total > 0) return@withContext total
        }
        -1L
    } catch (_: Exception) { -1L }
}

private fun formatBytesEp(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "%.0f KB".format(bytes / 1024.0)
    }
}

private sealed class InfoBarState {
    object Idle : InfoBarState()
    object ResumeChoice : InfoBarState()
    object PlayChoice : InfoBarState()
    object CheckingSize : InfoBarState()
    data class StorageInfo(
        val episodeSize: Long,
        val available: Long,
        val notEnough: Boolean,
        val lowAfter: Boolean
    ) : InfoBarState()
    object LowSpaceWarning : InfoBarState()
}

@Composable
fun SeriesDetailsDialog(
    series: SeriesEntity,
    episodes: List<EpisodeEntity>,
    seasons: List<Int>,
    episodeProgressMap: Map<String, Long> = emptyMap(),
    isLoading: Boolean,
    isBookmarked: Boolean = false,
    initialFocusEpisodeId: String? = null,
    maxAgeRating: String? = null,
    allowNr: Boolean = true,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onDownloadEpisode: ((streamUrl: String, title: String) -> Unit)? = null,
    onFetchCertification: (suspend () -> String?)? = null,
    onFetchOriginalLanguage: (suspend () -> String?)? = null,
    whisperManager: WhisperSubtitleManager? = null,
    onRatingOverride: ((String) -> Unit)? = null,
    playlistName: String? = null,
    onFetchTrailerUrl: (suspend () -> String?)? = null,
    onGoToSeries: (() -> Unit)? = null,
    onPlayEpisode: (streamUrl: String, episodeId: String, startPosition: Long, seriesId: String, seriesName: String, seasonNum: Int, episodeNum: Int, episodeName: String) -> Unit
) {
    val accent     = LocalNsAccent.current
    val background = LocalNsBackground.current

    var selectedSeason by remember(seasons, initialFocusEpisodeId, episodes, episodeProgressMap) {
        val targetSeason = if (initialFocusEpisodeId != null)
            episodes.firstOrNull { it.id == initialFocusEpisodeId }?.seasonNum
        else
            episodes
                .filter { (episodeProgressMap[it.id] ?: 0L) > 0L }
                .maxWithOrNull(compareBy({ it.seasonNum }, { it.episodeNum }))
                ?.seasonNum
        val season = (if (targetSeason != null && targetSeason in seasons) targetSeason else null)
            ?: seasons.firstOrNull() ?: 1
        mutableStateOf(season)
    }
    var seasonDropdownExpanded by remember { mutableStateOf(false) }

    val episodesForSeason = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNum == selectedSeason }.sortedBy { it.episodeNum }
    }

    val defaultIndex = remember(episodesForSeason, initialFocusEpisodeId, episodeProgressMap) {
        if (initialFocusEpisodeId != null) {
            val idx = episodesForSeason.indexOfFirst { it.id == initialFocusEpisodeId }
            if (idx >= 0) return@remember idx
        }
        val first = episodesForSeason.indexOfFirst { (episodeProgressMap[it.id] ?: 0L) <= 0L }
        if (first >= 0) first else 0
    }

    val hasMultiSeason  = seasons.size > 1
    val seasonsLoaded   = seasons.isNotEmpty()

    val context = LocalContext.current
    val voiceTranslateOptions = VOICE_SUBTITLE_MODES
    val showVoiceTranslate = whisperManager != null

    var trailerUrl by remember(series.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(series.id) {
        if (onFetchTrailerUrl != null) {
            val fetched = onFetchTrailerUrl()
            if (!fetched.isNullOrBlank()) trailerUrl = fetched
        }
    }
    val hasTrailer by remember { derivedStateOf { !trailerUrl.isNullOrBlank() } }
    val showTrailerState = remember { mutableStateOf(false) }
    var showTrailer by showTrailerState
    val trailerStartedState = remember { mutableStateOf(false) }
    var trailerStarted by trailerStartedState
    LaunchedEffect(showTrailer) { if (!showTrailer) trailerStartedState.value = false }

    val trailerCount   = if (hasTrailer) 1 else 0
    val hasGoToSeries  = onGoToSeries != null
    val goToSeriesIdx  = if (hasGoToSeries) 2 else -1
    val seasonBaseIdx  = 2 + (if (hasGoToSeries) 1 else 0)
    val whisperBtnIdx  = seasonBaseIdx + (if (hasMultiSeason) 1 else 0)
    val trailerBtnIdx  = if (hasTrailer) whisperBtnIdx + (if (showVoiceTranslate) 1 else 0) else -1
    val barButtonCount = 2 + (if (hasGoToSeries) 1 else 0) + (if (hasMultiSeason) 1 else 0) + (if (showVoiceTranslate) 1 else 0) + trailerCount

    var selectedButton  by remember { mutableStateOf(if (hasMultiSeason) seasonBaseIdx else 1) }
    var pressedButton   by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(barButtonCount) {
        if (selectedButton >= barButtonCount) selectedButton = barButtonCount - 1
    }

    var inGrid           by remember { mutableStateOf(false) }
    var focusedGridIndex by remember { mutableStateOf(defaultIndex) }
    var infoBarState     by remember { mutableStateOf<InfoBarState>(InfoBarState.Idle) }
    var overlayButton    by remember { mutableStateOf(0) }
    var voiceTranslateIndex by remember { mutableStateOf(0) }

    val focusedEpisode = remember(focusedGridIndex, episodesForSeason) {
        episodesForSeason.getOrNull(focusedGridIndex)
    }

    // NR rating override — user can manually set rating when cert is NR/null
    var ratingOverride    by remember { mutableStateOf<String?>(null) }
    var showRatingPicker  by remember { mutableStateOf(false) }

    // Displayed cert — may be fetched on open if blank, or overridden by user
    var fetchedCert by remember(series.certification) { mutableStateOf(series.certification) }
    LaunchedEffect(series.id) {
        if (fetchedCert.isNullOrBlank() && onFetchCertification != null) {
            val fetched = onFetchCertification()
            if (!fetched.isNullOrBlank()) fetchedCert = fetched
        }
    }
    val displayedCert by remember { derivedStateOf { ratingOverride ?: fetchedCert } }

    var displayedLang by remember(series.originalLanguage) { mutableStateOf(series.originalLanguage) }
    LaunchedEffect(series.id) {
        if (displayedLang.isNullOrBlank() && onFetchOriginalLanguage != null) {
            val fetched = onFetchOriginalLanguage()
            if (!fetched.isNullOrBlank()) displayedLang = fetched
        }
    }

    // Restrict playback if cert (possibly fetched live) exceeds profile's max age rating
    val isContentRestricted by remember(maxAgeRating, allowNr) {
        derivedStateOf {
            val cert = displayedCert
            when {
                cert.isNullOrBlank() -> false
                maxAgeRating == null -> cert == "NR" && !allowNr
                else -> !isAllowedByAgeRatingSD(cert, maxAgeRating, allowNr)
            }
        }
    }

    var focusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusTick) { if (focusTick > 0) infoBarState = InfoBarState.Idle }

    val dialogFocus         = remember { FocusRequester() }
    val gridFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val gridState           = remember(selectedSeason) { LazyListState() }
    val scope               = rememberCoroutineScope()

    // When restriction kicks in (cert fetched live), exit the episode grid so D-pad can't trigger play
    LaunchedEffect(isContentRestricted) {
        if (isContentRestricted) {
            inGrid = false
        }
    }

    LaunchedEffect(seasons) {
        if (seasons.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        selectedButton = if (hasMultiSeason) seasonBaseIdx else 1
        if (episodesForSeason.isNotEmpty() && !isContentRestricted) {
            inGrid = true
            val target = defaultIndex.coerceIn(0, episodesForSeason.lastIndex)
            focusedGridIndex = target
            kotlinx.coroutines.delay(80)
            try { gridFocusRequesters[target]?.requestFocus() } catch (_: Exception) {
                try { dialogFocus.requestFocus() } catch (_: Exception) {}
            }
        } else {
            try { dialogFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedSeason) {
        focusedGridIndex = 0; inGrid = false; infoBarState = InfoBarState.Idle
        gridFocusRequesters.clear()
    }

    LaunchedEffect(episodesForSeason, defaultIndex) {
        if (episodesForSeason.isNotEmpty() && defaultIndex > 0) {
            gridState.scrollToItem(defaultIndex)
            focusedGridIndex = defaultIndex
        }
    }

    fun startDownloadFlow(ep: EpisodeEntity) {
        infoBarState = InfoBarState.CheckingSize
        overlayButton = 1
        scope.launch {
            val available = getAvailableStorageBytesEp()
            val size = getEpisodeSizeBytes(ep.streamUrl)
            val notEnough = size > 0 && available < size
            val lowAfter  = size > 0 && available >= size && (available - size) < LOW_SPACE_BUFFER_EP
            infoBarState = InfoBarState.StorageInfo(size, available, notEnough, lowAfter)
            overlayButton = if (notEnough) 0 else 1
        }
    }

    fun playWithWhisper(action: () -> Unit) {
        if (whisperManager == null) { action(); return }
        val mode = voiceTranslateOptions.getOrNull(voiceTranslateIndex)?.first  // null=None, false=Transcribe, true=→EN
        if (mode == null) {
            scope.launch { context.saveWhisperSubtitles(false); action() }
            return
        }
        whisperManager.setTranslateToEnglish(mode)
        scope.launch {
            context.saveWhisperSubtitles(true)
            context.saveWhisperTranslateTo(mode)
            action()
        }
    }

    fun playEp(ep: EpisodeEntity, fromStart: Boolean) {
        val resumePos = episodeProgressMap[ep.id] ?: 0L
        playWithWhisper {
            onPlayEpisode(ep.streamUrl, ep.id,
                if (!fromStart && resumePos > 0L) resumePos else 0L,
                series.id, series.name, ep.seasonNum, ep.episodeNum, ep.name)
        }
    }

    if (showRatingPicker) {
        RatingPickerDialogSD(
            onDismiss = { showRatingPicker = false },
            onSelect  = { rating ->
                ratingOverride = rating
                onRatingOverride?.invoke(rating)
                showRatingPicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) {
            dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setDimAmount(0.95f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            // ── Full-bleed backdrop ───────────────────────────────────────────
            if (showTrailer && !trailerUrl.isNullOrBlank()) {
                val videoId = remember(trailerUrl) { extractYoutubeId(trailerUrl!!) }
                if (videoId != null) {
                    val mainHandler = remember { Handler(Looper.getMainLooper()) }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                settings.javaScriptEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun onTrailerEnded() {
                                        mainHandler.post { showTrailerState.value = false }
                                    }
                                    @JavascriptInterface
                                    fun onTrailerStarted() {
                                        mainHandler.post { trailerStartedState.value = true }
                                    }
                                }, "TrailerBridge")
                                val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0}body{background:#000;overflow:hidden}#p{position:fixed;top:0;left:0;width:100%;height:100%}</style>
</head><body>
<div id="p"></div>
<script>
var s=document.createElement('script');s.src='https://www.youtube.com/iframe_api';document.head.appendChild(s);
var player;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{
    videoId:'$videoId',
    playerVars:{autoplay:1,controls:1,playsinline:1,rel:0,modestbranding:1,origin:'https://www.themoviedb.org'},
    events:{onStateChange:function(e){if(e.data===0)TrailerBridge.onTrailerEnded();if(e.data===1)TrailerBridge.onTrailerStarted();}}
  });
}
</script>
</body></html>"""
                                loadDataWithBaseURL("https://www.themoviedb.org/", html, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    val overlayAlpha by animateFloatAsState(
                        targetValue   = if (trailerStarted) 0f else 1f,
                        animationSpec = tween(400),
                        label         = "trailerOverlay"
                    )
                    if (overlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = overlayAlpha }
                                .background(Color.Black)
                        )
                    }
                } else {
                    showTrailer = false
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(series.backdropUrl ?: series.posterUrl)
                        .crossfade(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            }

            // ── Gradient overlay — fades out during trailer ───────────────────
            AnimatedVisibility(
                visible = !showTrailer,
                enter   = fadeIn(tween(400)),
                exit    = fadeOut(tween(400)),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.40f),
                                0.40f to Color.Black.copy(alpha = 0.55f),
                                0.72f to Color.Black.copy(alpha = 0.80f),
                                1.0f  to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    )
                )
            }

            // ── Floating Close (top-right) ────────────────────────────────────
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                DialogActionPill(
                    icon      = Icons.Default.Close,
                    label     = "Close",
                    selected  = !inGrid && selectedButton == 0,
                    isPressed = pressedButton == 0,
                    accent    = accent,
                    onClick   = onDismiss,
                )
            }

            // ── Content column ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false

                        val overlay = infoBarState
                        if (overlay !is InfoBarState.Idle && overlay !is InfoBarState.CheckingSize) {
                            val overlayCount = when (overlay) {
                                is InfoBarState.ResumeChoice    -> if (onDownloadEpisode != null) 3 else 2
                                is InfoBarState.PlayChoice      -> if (onDownloadEpisode != null) 2 else 1
                                is InfoBarState.StorageInfo     -> 2
                                is InfoBarState.LowSpaceWarning -> 2
                                else -> 0
                            }
                            when (e.key) {
                                Key.DirectionLeft  -> { overlayButton = (overlayButton - 1 + overlayCount) % overlayCount; true }
                                Key.DirectionRight -> { overlayButton = (overlayButton + 1) % overlayCount; true }
                                Key.DirectionUp    -> { infoBarState = InfoBarState.Idle; true }
                                Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                    val ep = focusedEpisode
                                    when (overlay) {
                                        is InfoBarState.ResumeChoice -> when (overlayButton) {
                                            0 -> { if (ep != null) playEp(ep, fromStart = true) }
                                            1 -> { if (ep != null) playEp(ep, fromStart = false) }
                                            2 -> { if (ep != null) startDownloadFlow(ep) }
                                        }
                                        is InfoBarState.PlayChoice -> when (overlayButton) {
                                            0 -> { if (ep != null) playEp(ep, fromStart = true) }
                                            1 -> { if (ep != null) startDownloadFlow(ep) }
                                        }
                                        is InfoBarState.StorageInfo -> when (overlayButton) {
                                            0 -> { infoBarState = InfoBarState.Idle }
                                            1 -> {
                                                if (overlay.lowAfter) infoBarState = InfoBarState.LowSpaceWarning
                                                else if (!overlay.notEnough && ep != null) {
                                                    onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum} E${ep.episodeNum}")
                                                    infoBarState = InfoBarState.Idle
                                                }
                                            }
                                        }
                                        is InfoBarState.LowSpaceWarning -> when (overlayButton) {
                                            0 -> { infoBarState = InfoBarState.Idle }
                                            1 -> {
                                                if (ep != null) onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum} E${ep.episodeNum}")
                                                infoBarState = InfoBarState.Idle
                                            }
                                        }
                                        else -> {}
                                    }
                                    true
                                }
                                Key.Back -> { infoBarState = InfoBarState.Idle; true }
                                else -> false
                            }
                            return@onKeyEvent true
                        }

                        when (e.key) {
                            Key.DirectionLeft -> {
                                if (!inGrid) { selectedButton = (selectedButton - 1 + barButtonCount) % barButtonCount; true }
                                else {
                                    if (focusedGridIndex > 0) {
                                        val prev = focusedGridIndex - 1
                                        focusedGridIndex = prev
                                        scope.launch {
                                            gridState.animateScrollToItem(prev)
                                            try { gridFocusRequesters[prev]?.requestFocus() } catch (_: Exception) {}
                                        }
                                        true
                                    } else false
                                }
                            }
                            Key.DirectionRight -> {
                                if (!inGrid) { selectedButton = (selectedButton + 1) % barButtonCount; true }
                                else {
                                    if (focusedGridIndex < episodesForSeason.lastIndex) {
                                        val next = focusedGridIndex + 1
                                        focusedGridIndex = next
                                        scope.launch {
                                            gridState.animateScrollToItem(next)
                                            try { gridFocusRequesters[next]?.requestFocus() } catch (_: Exception) {}
                                        }
                                        true
                                    } else false
                                }
                            }
                            Key.DirectionDown -> {
                                if (!inGrid && episodesForSeason.isNotEmpty()) {
                                    inGrid = true
                                    val target = focusedGridIndex.coerceIn(0, episodesForSeason.lastIndex)
                                    scope.launch {
                                        gridState.animateScrollToItem(target)
                                        kotlinx.coroutines.delay(60)
                                        try { gridFocusRequesters[target]?.requestFocus() } catch (_: Exception) {}
                                    }
                                    true
                                } else if (inGrid) {
                                    inGrid = false
                                    selectedButton = barButtonCount - 1
                                    try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (inGrid) {
                                    inGrid = false
                                    try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            Key.Back -> { onDismiss(); true }
                            Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                if (!inGrid) {
                                    val btn = selectedButton
                                    pressedButton = btn
                                    scope.launch {
                                        delay(120)
                                        pressedButton = null
                                        when {
                                            btn == 0 -> onDismiss()
                                            btn == 1 -> onToggleWatchlist()
                                            btn == goToSeriesIdx && hasGoToSeries -> onGoToSeries?.invoke()
                                            btn == seasonBaseIdx && hasMultiSeason -> seasonDropdownExpanded = true
                                            btn == whisperBtnIdx && showVoiceTranslate ->
                                                voiceTranslateIndex = (voiceTranslateIndex + 1) % voiceTranslateOptions.size
                                            btn == trailerBtnIdx && trailerBtnIdx >= 0 && !trailerUrl.isNullOrBlank() -> showTrailer = !showTrailer
                                        }
                                    }
                                    true
                                } else {
                                    val ep = episodesForSeason.getOrNull(focusedGridIndex)
                                    if (ep != null) {
                                        if (isContentRestricted) {
                                            // Consume key — restriction UI shown in episode row
                                        } else if ((episodeProgressMap[ep.id] ?: 0L) > 0L) {
                                            infoBarState = InfoBarState.ResumeChoice
                                            overlayButton = 1
                                        } else {
                                            if (onDownloadEpisode != null) {
                                                infoBarState = InfoBarState.PlayChoice
                                                overlayButton = 0
                                            } else {
                                                playEp(ep, fromStart = true)
                                            }
                                        }
                                        true
                                    } else false
                                }
                            }
                            else -> false
                        }
                    }
            ) {
                Spacer(Modifier.weight(1f))

                // ── Series info ───────────────────────────────────────────────
                Column(
                    modifier            = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text       = series.name,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (!displayedLang.isNullOrEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, null, tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(13.dp))
                                Text(displayedLang!!.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.70f))
                            }
                        }
                        if (!series.rating.isNullOrEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                Text(series.rating!!.toDoubleOrNull()?.let { "%.1f".format(it) } ?: series.rating!!, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        val isNrRating = displayedCert.isNullOrBlank() ||
                            displayedCert == "NR" || displayedCert == "Not Rated"
                        if (!displayedCert.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                                    .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(displayedCert!!, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        if (isNrRating) {
                            Icon(
                                imageVector        = Icons.Default.Edit,
                                contentDescription = "Set rating",
                                tint               = Color.White.copy(alpha = 0.60f),
                                modifier           = Modifier
                                    .size(13.dp)
                                    .clickable { showRatingPicker = true },
                            )
                        }
                        if (seasons.isNotEmpty()) {
                            Text(
                                text     = "${seasons.size} Season${if (seasons.size != 1) "s" else ""}",
                                fontSize = 13.sp,
                                color    = Color.White.copy(alpha = 0.70f),
                            )
                        }
                        if (!series.genre.isNullOrEmpty()) {
                            Text("·", color = Color.White.copy(alpha = 0.40f), fontSize = 13.sp)
                            Text(series.genre!!, fontSize = 13.sp, color = Color.White.copy(alpha = 0.70f))
                        }
                    }
                    if (!playlistName.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PlaylistPlay, null, modifier = Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.45f))
                            Text(playlistName, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
                        }
                    }
                    if (!series.director.isNullOrEmpty()) {
                        Text(
                            text     = "Dir. ${series.director}",
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!series.cast.isNullOrEmpty()) {
                        Text(
                            text     = series.cast!!,
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!series.plot.isNullOrBlank()) {
                        Text(
                            text     = series.plot!!,
                            fontSize = 13.sp,
                            color    = Color.White.copy(alpha = 0.70f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // ── Action buttons ────────────────────────────────────────
                    if (seasonsLoaded) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            DialogActionPill(
                                icon      = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label     = if (isBookmarked) "Remove" else "My List",
                                selected  = !inGrid && selectedButton == 1,
                                isPressed = pressedButton == 1,
                                accent    = accent,
                                onClick   = onToggleWatchlist,
                            )
                            if (hasGoToSeries) {
                                DialogActionPill(
                                    icon      = Icons.Default.VideoLibrary,
                                    label     = "Go to Series",
                                    selected  = !inGrid && selectedButton == goToSeriesIdx,
                                    isPressed = pressedButton == goToSeriesIdx,
                                    accent    = accent,
                                    onClick   = { onGoToSeries?.invoke() },
                                )
                            }
                            if (hasMultiSeason) {
                                Box {
                                    DialogActionPill(
                                        icon      = Icons.Default.Tv,
                                        label     = "Season $selectedSeason",
                                        trailing  = Icons.Default.ArrowDropDown,
                                        selected  = !inGrid && selectedButton == 2,
                                        isPressed = pressedButton == 2,
                                        accent    = accent,
                                        onClick   = { seasonDropdownExpanded = true },
                                    )
                                    DropdownMenu(
                                        expanded         = seasonDropdownExpanded,
                                        onDismissRequest = {
                                            seasonDropdownExpanded = false
                                            selectedButton = seasonBaseIdx
                                            try { dialogFocus.requestFocus() } catch (_: Exception) {}
                                        }
                                    ) {
                                        seasons.forEach { season ->
                                            DropdownMenuItem(
                                                text = { Text("Season $season") },
                                                onClick = { selectedSeason = season; seasonDropdownExpanded = false },
                                                leadingIcon = {
                                                    if (season == selectedSeason)
                                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                    else
                                                        Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (showVoiceTranslate) {
                                val modeLabel = voiceTranslateOptions.getOrNull(voiceTranslateIndex)?.second ?: "None"
                                DialogActionPill(
                                    icon      = Icons.Default.RecordVoiceOver,
                                    label     = "AI: $modeLabel",
                                    selected  = !inGrid && selectedButton == whisperBtnIdx,
                                    isPressed = pressedButton == whisperBtnIdx,
                                    accent    = accent,
                                    onClick   = { voiceTranslateIndex = (voiceTranslateIndex + 1) % voiceTranslateOptions.size },
                                )
                            }
                            if (hasTrailer) {
                                DialogActionPill(
                                    icon      = if (showTrailer) Icons.Default.Stop else Icons.Default.Movie,
                                    label     = if (showTrailer) "Stop Trailer" else "Trailer",
                                    selected  = !inGrid && selectedButton == trailerBtnIdx,
                                    isPressed = pressedButton == trailerBtnIdx,
                                    accent    = accent,
                                    onClick   = { if (!trailerUrl.isNullOrBlank()) showTrailer = !showTrailer },
                                )
                            }
                        }
                    }
                }

                // ── Info bar ──────────────────────────────────────────────────
                val infoBarHeight = when (infoBarState) {
                    is InfoBarState.StorageInfo, is InfoBarState.LowSpaceWarning -> 72.dp
                    else -> 52.dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(infoBarHeight)
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (val state = infoBarState) {
                        is InfoBarState.Idle -> {
                            if (focusedEpisode != null) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier            = Modifier.align(Alignment.CenterStart),
                                ) {
                                    Text(
                                        "E${focusedEpisode.episodeNum} · ${focusedEpisode.name}",
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Color.White,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                    )
                                    if (!focusedEpisode.plot.isNullOrEmpty())
                                        Text(
                                            focusedEpisode.plot!!,
                                            fontSize = 12.sp,
                                            color    = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                }
                            } else {
                                Text(
                                    "Select an episode",
                                    fontSize = 13.sp,
                                    color    = Color.White.copy(alpha = 0.40f),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                            }
                        }
                        is InfoBarState.ResumeChoice -> {
                            val ep = focusedEpisode
                            Row(
                                modifier              = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                OverlayButton("↺  Start Over", overlayButton == 0) {
                                    if (ep != null) { overlayButton = 0; playEp(ep, fromStart = true) }
                                }
                                OverlayButton("▶  Resume",    overlayButton == 1) {
                                    if (ep != null) { overlayButton = 1; playEp(ep, fromStart = false) }
                                }
                                if (onDownloadEpisode != null)
                                    OverlayButton("⬇  Download", overlayButton == 2) {
                                        if (ep != null) { overlayButton = 2; startDownloadFlow(ep) }
                                    }
                            }
                        }
                        is InfoBarState.PlayChoice -> {
                            val ep = focusedEpisode
                            Row(
                                modifier              = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                OverlayButton("▶  Play", overlayButton == 0) {
                                    if (ep != null) { overlayButton = 0; playEp(ep, fromStart = true) }
                                }
                                if (onDownloadEpisode != null)
                                    OverlayButton("⬇  Download", overlayButton == 1) {
                                        if (ep != null) { overlayButton = 1; startDownloadFlow(ep) }
                                    }
                            }
                        }
                        is InfoBarState.CheckingSize -> {
                            Row(
                                modifier              = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Text("Checking size…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                        is InfoBarState.StorageInfo -> {
                            Column(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalAlignment   = Alignment.CenterHorizontally,
                                verticalArrangement   = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Episode: ${formatBytesEp(state.episodeSize)}  ·  Available: ${formatBytesEp(state.available)}",
                                    fontSize = 12.sp,
                                    color    = when {
                                        state.notEnough -> MaterialTheme.colorScheme.error
                                        state.lowAfter  -> MaterialTheme.colorScheme.tertiary
                                        else            -> Color.White.copy(alpha = 0.75f)
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayButton("Cancel",   overlayButton == 0) { infoBarState = InfoBarState.Idle }
                                    OverlayButton("Download", overlayButton == 1, enabled = !state.notEnough) {
                                        val ep = focusedEpisode
                                        if (state.lowAfter) infoBarState = InfoBarState.LowSpaceWarning
                                        else if (!state.notEnough && ep != null) {
                                            onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum} E${ep.episodeNum}")
                                            infoBarState = InfoBarState.Idle
                                        }
                                    }
                                }
                            }
                        }
                        is InfoBarState.LowSpaceWarning -> {
                            Column(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalAlignment   = Alignment.CenterHorizontally,
                                verticalArrangement   = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Low storage — less than ${formatBytesEp(LOW_SPACE_BUFFER_EP)} will remain.",
                                    fontSize = 12.sp,
                                    color    = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayButton("Cancel",          overlayButton == 0) { infoBarState = InfoBarState.Idle }
                                    OverlayButton("Continue Anyway", overlayButton == 1) {
                                        val ep = focusedEpisode
                                        if (ep != null) onDownloadEpisode?.invoke(ep.streamUrl, "${series.name} S${ep.seasonNum} E${ep.episodeNum}")
                                        infoBarState = InfoBarState.Idle
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Episode row ───────────────────────────────────────────────
                when {
                    isContentRestricted -> Box(
                        Modifier.fillMaxWidth().height(190.dp).background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                            Text("Content Restricted", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(
                                "This series is not available with your current profile settings.",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    isLoading -> Box(
                        Modifier.fillMaxWidth().height(190.dp).background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Loading episodes…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    episodesForSeason.isEmpty() -> Box(
                        Modifier.fillMaxWidth().height(190.dp).background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No episodes available", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    else -> LazyRow(
                        state                 = gridState,
                        modifier              = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color.Black.copy(alpha = 0.70f)),
                        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(episodesForSeason, key = { _, ep -> ep.id }) { index, episode ->
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(fr) { gridFocusRequesters[index] = fr }

                            val isFocused       = inGrid && focusedGridIndex == index
                            val episodePos      = episodeProgressMap[episode.id] ?: 0L
                            val hasProgress     = episodePos > 0L
                            val progressFraction = remember(episodePos, episode.duration) {
                                if (hasProgress && !episode.duration.isNullOrEmpty()) {
                                    val ms = parseDurationToMs(episode.duration!!)
                                    if (ms > 0L) (episodePos.toFloat() / ms.toFloat()).coerceIn(0f, 1f) else 0f
                                } else 0f
                            }

                            Box(
                                modifier = Modifier
                                    .width(236.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) accent else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .focusRequester(fr)
                                    .focusable()
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused && inGrid) {
                                            focusedGridIndex = index
                                            focusTick++
                                        }
                                    }
                                    .clickable {
                                        if (!isContentRestricted) {
                                            focusedGridIndex = index; inGrid = true
                                            infoBarState = if (hasProgress) InfoBarState.ResumeChoice else InfoBarState.PlayChoice
                                        }
                                    }
                            ) {
                                val thumbModel = episode.posterUrl?.takeIf { it.isNotEmpty() }
                                    ?: series.posterUrl?.takeIf { it.isNotEmpty() }
                                    ?: series.backdropUrl
                                if (thumbModel != null) {
                                    AsyncImage(
                                        model              = thumbModel,
                                        contentDescription = null,
                                        modifier           = Modifier.fillMaxSize(),
                                        contentScale       = ContentScale.Crop,
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
                                }
                                // Dark scrim — lighter on focus
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Color.Black.copy(alpha = if (isFocused) 0.10f else 0.38f)
                                    )
                                )
                                // Episode number badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.70f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "E${episode.episodeNum}",
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Color.White,
                                    )
                                }
                                // Play icon on focus
                                if (isFocused) {
                                    Icon(
                                        Icons.Default.PlayArrow, null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(32.dp).align(Alignment.Center),
                                    )
                                }
                                // Watched tick
                                if (progressFraction >= 0.9f) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("✓", fontSize = 10.sp, color = background)
                                    }
                                }
                                // Progress bar
                                if (progressFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(progressFraction).background(accent))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun isAllowedByAgeRatingSD(cert: String?, maxAge: String?, allowNr: Boolean): Boolean {
    if (cert == "NR" || cert == null) return allowNr
    if (maxAge == null) return true
    val order = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
    val certOrder = order[cert] ?: return true
    val maxOrder  = order[maxAge] ?: return true
    return certOrder <= maxOrder
}

// ── Top-bar action pill ───────────────────────────────────────────────────────
@Composable
private fun DialogActionPill(
    icon:      ImageVector,
    label:     String,
    selected:  Boolean,
    isPressed: Boolean = false,
    accent:    Color,
    trailing:  ImageVector? = null,
    onClick:   () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) accent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(14.dp),
            tint               = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        )
        Text(
            text       = label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        )
        if (trailing != null) {
            Icon(
                imageVector        = trailing,
                contentDescription = null,
                modifier           = Modifier.size(14.dp),
                tint               = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// ── Inline overlay button ─────────────────────────────────────────────────────
@Composable
private fun OverlayButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick            = onClick,
            enabled            = enabled,
            shape              = RoundedCornerShape(6.dp),
            contentPadding     = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick        = onClick,
            enabled        = enabled,
            shape          = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors         = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border         = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        ) {
            Text(label, fontSize = 12.sp)
        }
    }
}

private fun parseDurationToMs(duration: String): Long {
    return try {
        val trimmed = duration.trim()
        if (!trimmed.contains(":")) return (trimmed.toDouble() * 1000).toLong()
        val parts = trimmed.split(":").map { it.toDouble().toLong() }
        when (parts.size) {
            3    -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            2    -> (parts[0] * 60 + parts[1]) * 1000
            1    -> parts[0] * 1000
            else -> 0L
        }
    } catch (_: Exception) { 0L }
}

@Composable
private fun RatingPickerDialogSD(
    onDismiss: () -> Unit,
    onSelect:  (String) -> Unit,
) {
    val accent  = LocalNsAccent.current
    val bgColor = LocalNsBackground.current
    val ratings = listOf("G", "PG", "PG-13", "R", "NC-17", "U", "12", "12A", "15", "18", "R18", "TV-Y", "TV-G", "TV-PG", "TV-14", "TV-MA", "NR")
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xF0161616),
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Set Rating", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                ratings.chunked(4).forEach { rowRatings ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowRatings.forEach { rating ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent)
                                    .clickable { onSelect(rating) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(rating, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bgColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
