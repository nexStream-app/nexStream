package app.nexstream.player.ui.screens.movies

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.remote.RtData
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val LOW_SPACE_BUFFER_MOVD = 250L * 1024 * 1024


private fun getAvailableStorageBytesMD(): Long {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return stat.availableBlocksLong * stat.blockSizeLong
}

private suspend fun getMovieSizeBytesMD(streamUrl: String): Long = withContext(Dispatchers.IO) {
    try {
        val headConn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
            connectTimeout = 8_000; readTimeout = 8_000; instanceFollowRedirects = true
        }
        headConn.connect()
        val headSize = headConn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        headConn.disconnect()
        if (headSize > 0) return@withContext headSize
        val getConn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
            setRequestProperty("Range", "bytes=0-0")
            connectTimeout = 8_000; readTimeout = 8_000; instanceFollowRedirects = true
        }
        getConn.connect()
        val range = getConn.getHeaderField("Content-Range")
        getConn.disconnect()
        range?.substringAfterLast("/")?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
    } catch (_: Exception) { -1L }
}

private fun formatBytesMD(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "%.0f KB".format(bytes / 1024.0)
    }
}

private fun extractYoutubeId(url: String): String? {
    val patterns = listOf(
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/watch\\?.*v=([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/embed/([A-Za-z0-9_-]{11})"),
        Regex("youtube\\.com/v/([A-Za-z0-9_-]{11})"),
    )
    return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}

// ── Main composable ────────────────────────────────────────────────────────────

@Composable
fun ModernMovieDetailsDialog(
    movie: MovieEntity,
    resumePosition: Long = 0L,
    isBookmarked: Boolean = false,
    isContentRestricted: Boolean = false,
    maxAgeRating: String? = null,
    allowNr: Boolean = true,
    playlistName: String? = null,
    onDismiss: () -> Unit,
    onPlay: (startPosition: Long) -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onDownload: () -> Unit = {},
    onFetchCertification: (suspend () -> String?)? = null,
    onFetchOriginalLanguage: (suspend () -> String?)? = null,
    onFetchRtData: (suspend () -> RtData?)? = null,
    onRatingOverride: ((String) -> Unit)? = null,
    onFetchTrailerUrl: (suspend () -> String?)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
) {
    val accent     = LocalNsAccent.current

    val hasProgress = resumePosition > 0L

    var trailerUrl by remember(movie.id) { mutableStateOf(movie.trailerUrl) }
    LaunchedEffect(movie.id) {
        if (trailerUrl.isNullOrBlank() && onFetchTrailerUrl != null) {
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

    // Button indices: 0=Close  1=MyList  2=Download  [3=RemoveRecent]  3/4=StartOver?  last=Play/Resume  [Trailer]
    // Restricted:     0=Close  1=MyList  [2=RemoveRecent]
    val trailerCount = if (hasTrailer && !isContentRestricted) 1 else 0
    val removeRecentBtnCount = if (onRemoveFromRecent != null) 1 else 0
    val buttonCount = when {
        isContentRestricted -> 2 + removeRecentBtnCount
        hasProgress         -> 5 + trailerCount + removeRecentBtnCount
        else                -> 4 + trailerCount + removeRecentBtnCount
    }
    val trailerBtnIdx = if (trailerCount > 0) buttonCount - 1 else -1
    var selectedButton by remember { mutableStateOf(if (isContentRestricted) 0 else buttonCount - 1 - trailerCount) }
    val scope         = rememberCoroutineScope()
    var pressedButton by remember { mutableStateOf<Int?>(null) }

    val dialogFocus = remember { FocusRequester() }

    // NR rating override — user can manually set rating when cert is NR/null
    var ratingOverride   by remember { mutableStateOf<String?>(null) }
    var showRatingPicker by remember { mutableStateOf(false) }

    var fetchedCert by remember(movie.certification) { mutableStateOf(movie.certification) }
    LaunchedEffect(movie.id) {
        if (fetchedCert.isNullOrBlank() && onFetchCertification != null) {
            val fetched = onFetchCertification()
            if (!fetched.isNullOrBlank()) fetchedCert = fetched
        }
    }
    val displayedCert by remember { derivedStateOf { ratingOverride ?: fetchedCert } }

    var displayedLang by remember(movie.originalLanguage) { mutableStateOf(movie.originalLanguage) }
    LaunchedEffect(movie.id) {
        if (displayedLang.isNullOrBlank() && onFetchOriginalLanguage != null) {
            val fetched = onFetchOriginalLanguage()
            if (!fetched.isNullOrBlank()) displayedLang = fetched
        }
    }

    val context = LocalContext.current

    // Re-evaluate restriction if cert was fetched live and profile has an age limit
    val isEffectivelyRestricted by remember(isContentRestricted, maxAgeRating, allowNr) {
        derivedStateOf {
            if (isContentRestricted) return@derivedStateOf true
            val cert = displayedCert
            when {
                cert.isNullOrBlank() -> false
                maxAgeRating == null -> cert == "NR" && !allowNr
                else -> !isAllowedByAgeRatingMD(cert, maxAgeRating, allowNr)
            }
        }
    }
    // Reset button highlight to a valid index when restriction hides play buttons
    LaunchedEffect(isEffectivelyRestricted) {
        if (isEffectivelyRestricted && selectedButton > 1) selectedButton = 0
    }
    LaunchedEffect(buttonCount) {
        if (selectedButton >= buttonCount) selectedButton = buttonCount - 1
    }

    var rtCriticsScore  by remember(movie.id) { mutableStateOf(movie.rtCriticsScore) }
    var rtAudienceScore by remember(movie.id) { mutableStateOf(movie.rtAudienceScore) }
    var rtConsensus     by remember(movie.id) { mutableStateOf(movie.rtConsensus) }
    var metascore       by remember(movie.id) { mutableStateOf(movie.metascore) }
    LaunchedEffect(movie.id) {
        if (rtCriticsScore == null && rtAudienceScore == null && rtConsensus.isNullOrBlank() && metascore == null && onFetchRtData != null) {
            val result = onFetchRtData()
            if (result != null) {
                rtCriticsScore  = result.criticsScore
                rtAudienceScore = result.audienceScore
                rtConsensus     = result.consensus
                metascore       = result.metascore
            }
        }
    }

    var showStorageInfo  by remember { mutableStateOf(false) }
    var showLowSpaceWarn by remember { mutableStateOf(false) }
    var movieSizeBytes   by remember { mutableStateOf<Long?>(null) }
    var availableBytes   by remember { mutableStateOf(0L) }

    val notEnoughSpace = remember(movieSizeBytes, availableBytes) {
        val sz = movieSizeBytes; sz != null && sz > 0 && availableBytes > 0 && availableBytes < sz
    }
    val lowSpaceAfterDl = remember(movieSizeBytes, availableBytes) {
        val sz = movieSizeBytes
        sz != null && sz > 0 && availableBytes > 0 &&
                availableBytes >= sz && (availableBytes - sz) < LOW_SPACE_BUFFER_MOVD
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    // ── Rating picker dialog ───────────────────────────────────────────────────
    if (showRatingPicker) {
        RatingPickerDialogMD(
            onDismiss = { showRatingPicker = false },
            onSelect  = { rating ->
                ratingOverride = rating
                onRatingOverride?.invoke(rating)
                showRatingPicker = false
            }
        )
    }

    // ── Low-space warning sub-dialog ──────────────────────────────────────────
    if (showLowSpaceWarn) {
        var lowSel by remember { mutableStateOf(0) }
        val lowFR = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); try { lowFR.requestFocus() } catch (_: Exception) {} }
        Dialog(
            onDismissRequest = { showLowSpaceWarn = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(0.55f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp).focusRequester(lowFR).focusable().onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { lowSel = (lowSel - 1 + 2) % 2; true }
                            Key.DirectionRight -> { lowSel = (lowSel + 1) % 2; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (lowSel == 0) showLowSpaceWarn = false
                                else { showLowSpaceWarn = false; onDownload() }
                                true
                            }
                            else -> false
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Low Storage Warning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "After this download you'll have less than ${formatBytesMD(LOW_SPACE_BUFFER_MOVD)} free. " +
                                "Running low on storage can cause instability. Continue anyway?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lowSel == 0) Button(onClick = { showLowSpaceWarn = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        } else OutlinedButton(onClick = { showLowSpaceWarn = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        if (lowSel == 1) Button(onClick = { showLowSpaceWarn = false; onDownload() }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Continue Anyway")
                        } else OutlinedButton(onClick = { showLowSpaceWarn = false; onDownload() }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Continue Anyway")
                        }
                    }
                }
            }
        }
    }

    // ── Storage info sub-dialog ───────────────────────────────────────────────
    if (showStorageInfo) {
        val movieSz = movieSizeBytes
        val cancelFR = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            availableBytes = getAvailableStorageBytesMD()
            movieSizeBytes = getMovieSizeBytesMD(movie.streamUrl)
        }
        LaunchedEffect(movieSizeBytes, availableBytes) {
            if (notEnoughSpace) { kotlinx.coroutines.delay(80); try { cancelFR.requestFocus() } catch (_: Exception) {} }
        }
        var storageSel by remember { mutableStateOf(if (notEnoughSpace) 0 else 1) }
        LaunchedEffect(notEnoughSpace) { if (notEnoughSpace) storageSel = 0 }
        Dialog(
            onDismissRequest = { showStorageInfo = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(0.55f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp).focusRequester(cancelFR).focusable().onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { if (!notEnoughSpace) storageSel = (storageSel - 1 + 2) % 2; true }
                            Key.DirectionRight -> { if (!notEnoughSpace) storageSel = (storageSel + 1) % 2; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (storageSel == 0) showStorageInfo = false
                                else { showStorageInfo = false; if (lowSpaceAfterDl) showLowSpaceWarn = true else if (!notEnoughSpace) onDownload() }
                                true
                            }
                            else -> false
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Storage Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Movie size:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (movieSz == null) "Checking…" else formatBytesMD(movieSz), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(formatBytesMD(availableBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (movieSz != null) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            when {
                                movieSz <= 0    -> Text("Movie size could not be determined.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                notEnoughSpace  -> Text("Not enough space. Free up at least ${formatBytesMD(movieSz - availableBytes + LOW_SPACE_BUFFER_MOVD)}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                lowSpaceAfterDl -> Text("Only ${formatBytesMD(availableBytes - movieSz)} will remain after download.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                                else            -> Text("Sufficient space available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (storageSel == 0) Button(onClick = { showStorageInfo = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        } else OutlinedButton(onClick = { showStorageInfo = false }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        if (storageSel == 1) Button(
                            onClick = { showStorageInfo = false; if (lowSpaceAfterDl) showLowSpaceWarn = true else if (!notEnoughSpace) onDownload() },
                            enabled = !notEnoughSpace, shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                        } else OutlinedButton(
                            onClick = { showStorageInfo = false; if (lowSpaceAfterDl) showLowSpaceWarn = true else if (!notEnoughSpace) onDownload() },
                            enabled = !notEnoughSpace, shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Download")
                        }
                    }
                }
            }
        }
    }

    // ── Main dialog ───────────────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) {
            dialogWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.setDimAmount(0.95f)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(12.dp))
                .focusRequester(dialogFocus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> {
                            if (showTrailer) return@onKeyEvent true
                            val c = if (isEffectivelyRestricted) 2 else buttonCount; selectedButton = (selectedButton - 1 + c) % c; true
                        }
                        Key.DirectionRight -> {
                            if (showTrailer) return@onKeyEvent true
                            val c = if (isEffectivelyRestricted) 2 else buttonCount; selectedButton = (selectedButton + 1) % c; true
                        }
                        Key.DirectionDown -> false
                        Key.DirectionUp -> false
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (showTrailer) { showTrailer = false; return@onKeyEvent true }
                            val btn = selectedButton
                            pressedButton = btn
                            scope.launch {
                                delay(120)
                                pressedButton = null
                                when {
                                    btn == 0 -> onDismiss()
                                    btn == 1 -> onToggleWatchlist()
                                    btn == 2 && !isEffectivelyRestricted -> { movieSizeBytes = null; availableBytes = 0L; showStorageInfo = true }
                                    btn == 3 && removeRecentBtnCount > 0 -> { onRemoveFromRecent?.invoke(); onDismiss() }
                                    btn == 2 && isEffectivelyRestricted && removeRecentBtnCount > 0 -> { onRemoveFromRecent?.invoke(); onDismiss() }
                                    btn == trailerBtnIdx && trailerBtnIdx >= 0 && !trailerUrl.isNullOrBlank() ->
                                        showTrailer = !showTrailer
                                    btn == 3 + removeRecentBtnCount && !isEffectivelyRestricted -> onPlay(0)
                                    btn == 4 + removeRecentBtnCount && !isEffectivelyRestricted -> onPlay(resumePosition)
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
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
                        targetValue    = if (trailerStarted) 0f else 1f,
                        animationSpec  = tween(400),
                        label          = "trailerOverlay"
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
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(movie.backdropUrl ?: movie.posterUrl)
                        .crossfade(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
                            0.00f to Color.Black.copy(alpha = 0.30f),
                            0.40f to Color.Black.copy(alpha = 0.52f),
                            0.72f to Color.Black.copy(alpha = 0.82f),
                            1.00f to Color.Black.copy(alpha = 0.97f),
                        )
                    )
                )
            }

            // ── Close pill (floating, top-right) ─────────────────────────────
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                MovieDialogPill(
                    label      = "Close",
                    icon       = Icons.Default.Close,
                    isSelected = selectedButton == 0,
                    isPressed  = pressedButton == 0,
                    accent     = accent,
                    onClick    = onDismiss,
                )
            }

            // ── Bottom content area ───────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Title, meta, scores, plot, cast — fade out during trailer
                AnimatedVisibility(
                    visible = !showTrailer,
                    enter   = fadeIn(tween(400)),
                    exit    = fadeOut(tween(400)),
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Title
                Text(
                    text       = movie.name,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )

                // Meta row
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
                    if (!movie.rating.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                            Text(movie.rating!!.toDoubleOrNull()?.let { "%.1f".format(it) } ?: movie.rating!!, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                    val isNrRating = displayedCert.isNullOrBlank() ||
                        displayedCert == "NR" || displayedCert == "Not Rated"
                    if (!displayedCert.isNullOrEmpty()) {
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
                    if (!movie.releaseDate.isNullOrEmpty()) Text(movie.releaseDate!!, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                    if (!movie.duration.isNullOrEmpty())    Text(movie.duration!!,    fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                }

                // Genre
                if (!movie.genre.isNullOrEmpty()) {
                    Text(movie.genre!!, fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium)
                }
                if (!playlistName.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PlaylistPlay, null, modifier = Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.45f))
                        Text(playlistName, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
                    }
                }

                // Ratings row — RT Critics + Metascore (audience/consensus kept for legacy data)
                if (rtCriticsScore != null || rtAudienceScore != null || metascore != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (rtCriticsScore != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .background(if (rtCriticsScore!! >= 60) Color(0xFF3CB371) else Color(0xFFCC3333))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("RT", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Text(
                                    text       = "${rtCriticsScore}%",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (rtCriticsScore!! >= 60) Color(0xFF3CB371) else Color(0xFFCC3333),
                                )
                                Text("Critics", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                        if (rtAudienceScore != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .background(if (rtAudienceScore!! >= 60) Color(0xFF3CB371) else Color(0xFFCC3333))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("A", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Text(
                                    text       = "${rtAudienceScore}%",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (rtAudienceScore!! >= 60) Color(0xFF3CB371) else Color(0xFFCC3333),
                                )
                                Text("Audience", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                        if (metascore != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .background(when {
                                            metascore!! >= 61 -> Color(0xFF3CB371)
                                            metascore!! >= 40 -> Color(0xFFD4A017)
                                            else              -> Color(0xFFCC3333)
                                        })
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("MC", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Text(
                                    text       = "$metascore",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = when {
                                        metascore!! >= 61 -> Color(0xFF3CB371)
                                        metascore!! >= 40 -> Color(0xFFD4A017)
                                        else              -> Color(0xFFCC3333)
                                    },
                                )
                                Text("Metascore", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                    }
                }

                // RT critics consensus
                if (!rtConsensus.isNullOrBlank()) {
                    Text(
                        text     = "“${rtConsensus}”",
                        fontSize = 12.sp,
                        color    = Color.White.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Plot
                if (!movie.plot.isNullOrEmpty()) {
                    Text(
                        text     = movie.plot!!,
                        fontSize = 13.sp,
                        color    = Color.White.copy(alpha = 0.80f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Cast / Director (single dimmed lines)
                if (!movie.director.isNullOrEmpty()) {
                    Text("Director: ${movie.director}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.50f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!movie.cast.isNullOrEmpty()) {
                    Text("Cast: ${movie.cast}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.50f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(6.dp))
                } // end inner Column
                } // end AnimatedVisibility

                // ── Action area ───────────────────────────────────────────────
                if (isEffectivelyRestricted) {
                    AnimatedVisibility(visible = !showTrailer, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Restricted message
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.60f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(26.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text       = "Content Restricted",
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text     = "This title's age rating has been updated and can no longer be viewed with your current profile settings.",
                                        fontSize = 12.sp,
                                        color    = Color.White.copy(alpha = 0.65f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            // My List still accessible
                            MovieDialogPill(
                                label      = if (isBookmarked) "Remove" else "My List",
                                icon       = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                isSelected = selectedButton == 1,
                                isPressed  = pressedButton == 1,
                                accent     = accent,
                                onClick    = onToggleWatchlist,
                            )
                            if (onRemoveFromRecent != null) {
                                MovieDialogPill(
                                    label      = "Remove from Recent",
                                    icon       = Icons.Default.Delete,
                                    isSelected = selectedButton == 2,
                                    isPressed  = pressedButton == 2,
                                    accent     = accent,
                                    onClick    = { onRemoveFromRecent(); onDismiss() },
                                )
                            }
                        }
                    }
                } else {
                    // Normal action row: non-trailer buttons fade out, trailer toggle stays
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(visible = !showTrailer, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MovieDialogPill(
                                    label      = if (isBookmarked) "Remove" else "My List",
                                    icon       = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    isSelected = selectedButton == 1,
                                    isPressed  = pressedButton == 1,
                                    accent     = accent,
                                    onClick    = onToggleWatchlist,
                                )
                                MovieDialogPill(
                                    label      = "Download",
                                    icon       = Icons.Default.Download,
                                    isSelected = selectedButton == 2,
                                    isPressed  = pressedButton == 2,
                                    accent     = accent,
                                    onClick    = { movieSizeBytes = null; availableBytes = 0L; showStorageInfo = true },
                                )
                                if (onRemoveFromRecent != null) {
                                    MovieDialogPill(
                                        label      = "Remove from Recent",
                                        icon       = Icons.Default.Delete,
                                        isSelected = selectedButton == 3,
                                        isPressed  = pressedButton == 3,
                                        accent     = accent,
                                        onClick    = { onRemoveFromRecent(); onDismiss() },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(visible = !showTrailer, enter = fadeIn(tween(400)), exit = fadeOut(tween(400))) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hasProgress) {
                                    MovieDialogPill(
                                        label      = "Start Over",
                                        icon       = Icons.Default.Refresh,
                                        isSelected = selectedButton == 3 + removeRecentBtnCount,
                                        isPressed  = pressedButton == 3 + removeRecentBtnCount,
                                        accent     = accent,
                                        onClick    = { onPlay(0) },
                                    )
                                    MovieDialogPill(
                                        label      = "Resume",
                                        icon       = Icons.Default.PlayArrow,
                                        isSelected = selectedButton == 4 + removeRecentBtnCount,
                                        isPressed  = pressedButton == 4 + removeRecentBtnCount,
                                        accent     = accent,
                                        onClick    = { onPlay(resumePosition) },
                                    )
                                } else {
                                    MovieDialogPill(
                                        label      = "Play Movie",
                                        icon       = Icons.Default.PlayArrow,
                                        isSelected = selectedButton == 3 + removeRecentBtnCount,
                                        isPressed  = pressedButton == 3 + removeRecentBtnCount,
                                        accent     = accent,
                                        onClick    = { onPlay(0) },
                                    )
                                }
                            }
                        }
                        // Trailer toggle — always visible
                        if (hasTrailer) {
                            MovieDialogPill(
                                label      = if (showTrailer) "Stop Trailer" else "Trailer",
                                icon       = if (showTrailer) Icons.Default.Stop else Icons.Default.Movie,
                                isSelected = selectedButton == trailerBtnIdx,
                                isPressed  = pressedButton == trailerBtnIdx,
                                accent     = accent,
                                onClick    = { if (!trailerUrl.isNullOrBlank()) showTrailer = !showTrailer },
                            )
                        }
                    }
                }
            }
        }
        } // end outer scrim Box
    }

}

private fun isAllowedByAgeRatingMD(cert: String?, maxAge: String?, allowNr: Boolean): Boolean {
    if (cert == "NR" || cert == null) return allowNr
    if (maxAge == null) return true
    val order = mapOf("U" to 0, "G" to 0, "PG" to 1, "12" to 2, "12A" to 2, "PG-13" to 2, "15" to 3, "R" to 3, "18" to 4, "R18" to 4, "NC-17" to 4)
    val certOrder = order[cert] ?: return true
    val maxOrder  = order[maxAge] ?: return true
    return certOrder <= maxOrder
}

@Composable
private fun RatingPickerDialogMD(
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

// ── Action pill composable ─────────────────────────────────────────────────────

@Composable
private fun MovieDialogPill(
    label:      String,
    icon:       ImageVector?,
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
