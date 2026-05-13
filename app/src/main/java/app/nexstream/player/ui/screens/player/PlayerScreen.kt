package app.nexstream.player.ui.screens.player

import android.content.Intent
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.license.AppAccessState
import app.nexstream.player.service.NexStreamPlaybackService
import app.nexstream.player.subtitle.SubtitleLanguage
import app.nexstream.player.subtitle.SubtitleResult
import app.nexstream.player.subtitle.SubtitleSheetContent
import app.nexstream.player.subtitle.SubtitleSheetState
import app.nexstream.player.ui.screens.series.SeriesViewModel
import app.nexstream.player.ui.screens.trial.TrialExpiredScreen
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.getAspectRatioFlow
import app.nexstream.player.ui.theme.getAutoFrameRateFlow
import app.nexstream.player.ui.theme.getSmartBufferFlow
import app.nexstream.player.ui.theme.saveAspectRatio
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// D-pad zones: CONTROLS = bottom playback row, ICONS = subtitle/aspect row, SLIDER = progress bar
private enum class DpadZone { CONTROLS, ICONS, SLIDER }
private enum class ErrorButton { GO_BACK, RETRY }

// ICONS zone sub-positions
private const val ICON_BACK      = 0
private const val ICON_SUBTITLES = 1
private const val ICON_ASPECT    = 2

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    channelUrl: String,
    onBack: () -> Unit,
    movieId: String? = null,
    episodeId: String? = null,
    seriesId: String? = null,
    startPosition: Long = 0,
    onPlayNextEpisode: ((episode: EpisodeEntity) -> Unit)? = null,
    nowPlayingTitle: String? = null,
    nowPlayingSubtitle: String? = null,
    nowPlayingDescription: String? = null,
    catchupDuration: Long = 0L,
    handleBackInternally: Boolean = true,
    profileId: String = "default",
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val nsTheme = LocalNexStreamTheme.current

    // ── Player prefs ──────────────────────────────────────────────────────────
    val autoFrameRate by context.getAutoFrameRateFlow().collectAsState(initial = true)
    val smartBuffer   by context.getSmartBufferFlow().collectAsState(initial = true)

    var showMediaSheet      by remember { mutableStateOf(false) }
    var showControls        by remember { mutableStateOf(true) }
    var isTrialExpired      by remember { mutableStateOf(false) }
    var subtitleSheetState  by remember { mutableStateOf<SubtitleSheetState>(SubtitleSheetState.Loading) }
    var availableSubtitles  by remember { mutableStateOf<List<SubtitleLanguage>>(emptyList()) }

    // D-pad: two zones — ICONS row and CONTROLS row (below), then SLIDER
    var dpadZone     by remember { mutableStateOf(DpadZone.CONTROLS) }
    var iconIndex    by remember { mutableStateOf(ICON_BACK) }
    var centreIndex  by remember { mutableStateOf(0) }

    val isAndroidTV  = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val showAspectRatioButton = !isAndroidTV

    val aspectRatioType = when {
        episodeId != null -> AspectRatioType.SERIES
        movieId != null   -> AspectRatioType.MOVIE
        else              -> AspectRatioType.TV
    }
    val savedAspectRatio by context.getAspectRatioFlow(aspectRatioType)
        .collectAsState(initial = when (aspectRatioType) {
            AspectRatioType.TV -> AspectRatio.FILL
            else               -> AspectRatio.FIT
        })

    var resizeMode              by remember { mutableStateOf(savedAspectRatio.exoPlayerValue) }
    var aspectRatioInitialised  by remember { mutableStateOf(false) }
    LaunchedEffect(savedAspectRatio) {
        if (!aspectRatioInitialised) { resizeMode = savedAspectRatio.exoPlayerValue; aspectRatioInitialised = true }
    }

    // Extra movie details for player overlay
    var moviePlot      by remember { mutableStateOf<String?>(null) }
    var movieCast      by remember { mutableStateOf<String?>(null) }
    var movieGenre     by remember { mutableStateOf<String?>(null) }
    var movieDirector  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(movieId, episodeId) {
        moviePlot = null; movieCast = null; movieGenre = null; movieDirector = null
        if (movieId != null && movieId != "catchup") {
            val movieBase = viewModel.getMovieById(movieId)
            if (movieBase != null) {
                // If plot missing, fetch enriched details from API
                val movie = if (movieBase.plot.isNullOrBlank()) {
                    val rawId = movieId.removePrefix("${movieBase.playlistId}-")
                    runCatching { viewModel.getMovieDetails(movieBase.playlistId, rawId) }.getOrNull()
                        ?: movieBase
                } else movieBase
                // Capture details for overlay
                moviePlot     = movie.plot
                movieCast     = movie.cast
                movieGenre    = movie.genre
                movieDirector = movie.director
                val year    = movie.releaseDate?.take(4)
                val results = viewModel.subtitleManager.searchMovieSubtitles(movie.name, year)
                availableSubtitles = results
                subtitleSheetState = if (results.isEmpty()) SubtitleSheetState.Loading else SubtitleSheetState.Languages(results)
            }
        } else if (episodeId != null && seriesId != null) {
            val episode = viewModel.getEpisodeById(episodeId)
            val series  = viewModel.getSeriesById(seriesId)
            if (episode != null && series != null) {
                val results = viewModel.subtitleManager.searchEpisodeSubtitles(series.name, episode.seasonNum, episode.episodeNum)
                availableSubtitles = results
                subtitleSheetState = if (results.isEmpty()) SubtitleSheetState.Loading else SubtitleSheetState.Languages(results)
            }
        }
    }

    var currentProgramme            by remember { mutableStateOf<String?>(null) }
    var currentProgrammeTime        by remember { mutableStateOf<String?>(null) }
    var currentProgrammeDescription by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channelUrl) {
        if (movieId == null && episodeId == null) {
            viewModel.getCurrentProgrammeForUrl(channelUrl).collectLatest { programme ->
                if (programme != null) {
                    currentProgramme = programme.title
                    currentProgrammeDescription = programme.description
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    currentProgrammeTime = "${timeFormat.format(Date(programme.startTime))} – ${timeFormat.format(Date(programme.endTime))}"
                } else {
                    currentProgramme = null; currentProgrammeTime = null; currentProgrammeDescription = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val window     = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { }
    }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var player by remember { mutableStateOf<Player?>(null) }
    val stopPlayback = {
        player?.stop()
        if (!isAndroidTV) context.stopService(Intent(context, NexStreamPlaybackService::class.java))
    }

    BackHandler(enabled = handleBackInternally) { stopPlayback(); onBack() }

    // ── Content type flags ────────────────────────────────────────────────────
    val isCatchup = movieId == "catchup" || catchupDuration > 0L
    val isVod     = movieId != null && movieId != "catchup"

    // ── LoadControl builder ───────────────────────────────────────────────────
    fun buildLoadControl(): DefaultLoadControl =
        if (smartBuffer && (isCatchup || isVod)) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // minBuffer  — keep 30s buffered ahead
                    120_000,  // maxBuffer  — up to 2 mins for catchup/VOD
                    1_500,    // bufferForPlayback — start fast
                    5_000     // bufferAfterRebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30_000, true) // 30s back-seek without re-fetch
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 20_000, 1_000, 3_000) // lean for live TV
                .build()
        }

    // ── Android TV: build ExoPlayer directly ──────────────────────────────────
    if (isAndroidTV) {
        DisposableEffect(channelUrl, autoFrameRate, smartBuffer) {
            android.util.Log.d("NexPlayer", "isAndroidTV=true, building ExoPlayer directly")
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    if (mimeType == "audio/eac3" || mimeType == "audio/ac3") emptyList()
                    else androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                        mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                    )
                }
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
                }
            )
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").also {
                it.init(null, trustAllCerts, java.security.SecureRandom())
            }
            val trustAllOkHttp = okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
            val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(trustAllOkHttp)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

            val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(buildLoadControl())
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSeekBackIncrementMs(60_000L)
                .setSeekForwardIncrementMs(60_000L)
                .setTrackSelector(
                    androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
                        setParameters(
                            buildUponParameters()
                                .setTunnelingEnabled(true)
                                .build()
                        )
                    }
                )
                .build()

            // ── Auto frame rate (Android 11+, compatible displays) ────────────
            if (autoFrameRate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                exoPlayer.setVideoFrameMetadataListener { _, _, _, _ -> }
            }

            // Disable subtitles by default
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()

            // Auto-select first supported audio track on STATE_READY
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d("nexPlayer", "ExoPlayer direct listener state: $playbackState")
                    if (playbackState == Player.STATE_READY) {
                        val tracks = exoPlayer.currentTracks
                        for (i in 0 until tracks.groups.size) {
                            val group = tracks.groups[i]
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (j in 0 until group.length) {
                                    val format = group.getTrackFormat(j)
                                    android.util.Log.d("nexTrack",
                                        "Track $i/$j: mime=${format.sampleMimeType} " +
                                                "ch=${format.channelCount} sr=${format.sampleRate} " +
                                                "supported=${group.isTrackSupported(j)} " +
                                                "selected=${group.isTrackSelected(j)}")
                                }
                            }
                        }
                        val anyAudioSelected = (0 until tracks.groups.size).any { i ->
                            val g = tracks.groups[i]
                            g.type == C.TRACK_TYPE_AUDIO && (0 until g.length).any { j -> g.isTrackSelected(j) }
                        }
                        if (!anyAudioSelected) {
                            for (i in 0 until tracks.groups.size) {
                                val group = tracks.groups[i]
                                if (group.type == C.TRACK_TYPE_AUDIO) {
                                    for (j in 0 until group.length) {
                                        if (group.isTrackSupported(j)) {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, j))
                                                .build()
                                            android.util.Log.d("nexTrack", "Auto-selected track $j in group $i")
                                            return
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            })

            val mediaItem = MediaItem.Builder().setUri(channelUrl)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(nowPlayingTitle)
                        .setSubtitle(nowPlayingSubtitle)
                        .build()
                ).build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (startPosition > 0) exoPlayer.seekTo(startPosition)
            exoPlayer.playWhenReady = true
            player = exoPlayer

            onDispose {
                if (movieId != null && movieId != "catchup") {
                    val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                    if (dur > 0 && pos < dur - 120_000) viewModel.savePlaybackPositionSync(movieId, pos, dur, profileId)
                }
                if (episodeId != null) {
                    val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                    if (dur > 0 && pos < dur - 120_000) viewModel.saveEpisodePositionSync(episodeId, pos, dur, seriesId, profileId)
                }
                exoPlayer.release(); player = null
            }
        }
    } else {
        // ── Mobile: MediaController via NexStreamPlaybackService ─────────────
        DisposableEffect(channelUrl) {
            val intent = Intent(context, NexStreamPlaybackService::class.java).apply {
                putExtra(NexStreamPlaybackService.EXTRA_URL, channelUrl)
                putExtra(NexStreamPlaybackService.EXTRA_TITLE, nowPlayingTitle)
                putExtra(NexStreamPlaybackService.EXTRA_SUBTITLE, nowPlayingSubtitle)
                putExtra(NexStreamPlaybackService.EXTRA_POSITION, startPosition)
                putExtra("EXTRA_AUTO_FRAME_RATE", autoFrameRate)
                putExtra("EXTRA_SMART_BUFFER", smartBuffer)
                putExtra("EXTRA_IS_CATCHUP", isCatchup)
                putExtra("EXTRA_IS_VOD", isVod)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            val sessionToken     = SessionToken(context, android.content.ComponentName(context, NexStreamPlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                try { player = controllerFuture.get() }
                catch (e: Exception) { android.util.Log.e("PlayerScreen", "Controller connection failed", e) }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
            onDispose {
                val ctrl = player
                if (ctrl != null) {
                    if (movieId != null && movieId != "catchup") { val pos = ctrl.currentPosition; val dur = ctrl.duration; if (dur > 0 && pos < dur - 120_000) viewModel.savePlaybackPositionSync(movieId, pos, dur, profileId) }
                    if (episodeId != null) { val pos = ctrl.currentPosition; val dur = ctrl.duration; if (dur > 0 && pos < dur - 120_000) viewModel.saveEpisodePositionSync(episodeId, pos, dur, seriesId, profileId) }
                }
                MediaController.releaseFuture(controllerFuture); player = null
                context.stopService(Intent(context, NexStreamPlaybackService::class.java))
            }
        }
    }

    // ── Playback state ────────────────────────────────────────────────────────
    var isPlaying          by remember { mutableStateOf(true) }
    var isBuffering        by remember { mutableStateOf(false) }
    var hasError           by remember { mutableStateOf(false) }
    var errorMessage       by remember { mutableStateOf("") }
    var errorFocusedButton by remember { mutableStateOf(ErrorButton.RETRY) }
    var autoRetryCount     by remember { mutableStateOf(0) }
    val maxAutoRetries = 3
    var showNextEpisodePrompt by remember { mutableStateOf(false) }
    var nextEpisodeAvailable     by remember { mutableStateOf<EpisodeEntity?>(null) }
    var previousEpisodeAvailable by remember { mutableStateOf<EpisodeEntity?>(null) }
    var shouldAutoPlayNext    by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) { hasError = false; autoRetryCount = 0 }
                if (playbackState == Player.STATE_ENDED) {
                    if (movieId != null && movieId != "catchup") viewModel.clearPlaybackPosition(movieId, profileId)
                    if (episodeId != null) { viewModel.clearEpisodePosition(episodeId, profileId); if (nextEpisodeAvailable != null) shouldAutoPlayNext = true }
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("nexPlayer", "Player error: ${error.message} cause=${error.cause?.message}")

                // BehindLiveWindowException: re-prepare from current position
                if (error.cause?.javaClass?.simpleName == "BehindLiveWindowException") {
                    android.util.Log.d("nexPlayer", "BehindLiveWindowException — re-preparing")
                    val position = p.currentPosition
                    p.prepare()
                    p.seekTo(position)
                    return
                }

                if (autoRetryCount < maxAutoRetries) {
                    autoRetryCount++
                    android.util.Log.d("PlayerScreen", "Auto-retry $autoRetryCount/$maxAutoRetries after error: ${error.message}")
                    scope.launch {
                        kotlinx.coroutines.delay(1500L * autoRetryCount)
                        p.prepare(); p.play()
                    }
                } else {
                    hasError = true; errorFocusedButton = ErrorButton.RETRY
                    errorMessage = error.message ?: "Playback error occurred"
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    // ── Series / next episode ─────────────────────────────────────────────────
    val seriesViewModel: SeriesViewModel = hiltViewModel()
    val nextEpisodeRef  = remember { mutableStateOf<EpisodeEntity?>(null) }
    val onPlayNextRef   = remember { mutableStateOf<((EpisodeEntity) -> Unit)?>(null) }
    LaunchedEffect(nextEpisodeAvailable) { nextEpisodeRef.value = nextEpisodeAvailable }
    LaunchedEffect(onPlayNextEpisode) { onPlayNextRef.value = onPlayNextEpisode }

    val forwardingPlayer = remember(player) {
        val p = player ?: return@remember null
        object : androidx.media3.common.ForwardingPlayer(p) {
            override fun hasNextMediaItem() = nextEpisodeRef.value != null
            override fun seekToNextMediaItem() { nextEpisodeRef.value?.let { onPlayNextRef.value?.invoke(it) } }
            override fun seekToNext() { nextEpisodeRef.value?.let { onPlayNextRef.value?.invoke(it) } }
        }
    }

    // ── Auto-save position ────────────────────────────────────────────────────
    LaunchedEffect(movieId, player) {
        if (movieId != null && movieId != "catchup" && player != null) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (!hasError) { val pos = player?.currentPosition ?: 0L; val dur = player?.duration ?: 0L; if (dur > 0 && pos > 0 && pos < dur - 120_000) viewModel.savePlaybackPosition(movieId, pos, profileId, dur) }
            }
        }
    }
    LaunchedEffect(episodeId, player) {
        if (episodeId != null && player != null) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (!hasError) { val pos = player?.currentPosition ?: 0L; val dur = player?.duration ?: 0L; if (dur > 0 && pos > 0 && pos < dur - 120_000) viewModel.saveEpisodePosition(episodeId, pos, profileId, seriesId, dur) }
            }
        }
    }
    LaunchedEffect(shouldAutoPlayNext) {
        if (shouldAutoPlayNext && nextEpisodeAvailable != null) { shouldAutoPlayNext = false; showNextEpisodePrompt = false; onPlayNextEpisode?.invoke(nextEpisodeAvailable!!) }
    }
    LaunchedEffect(episodeId, seriesId, player) {
        if (episodeId != null && seriesId != null && player != null) {
            val allEpisodes    = seriesViewModel.repository.getEpisodesForSeries(seriesId).first().sortedWith(compareBy({ it.seasonNum }, { it.episodeNum }))
            val currentIndex   = allEpisodes.indexOfFirst { it.id == episodeId }
            nextEpisodeAvailable     = if (currentIndex >= 0 && currentIndex < allEpisodes.size - 1) allEpisodes[currentIndex + 1] else null
            previousEpisodeAvailable = if (currentIndex > 0) allEpisodes[currentIndex - 1] else null
            while (true) {
                kotlinx.coroutines.delay(5000)
                val dur = player?.duration ?: 0L; val pos = player?.currentPosition ?: 0L
                if (dur > 0 && (dur - pos) <= 120_000 && !showNextEpisodePrompt && nextEpisodeAvailable != null) showNextEpisodePrompt = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val state = viewModel.trialManager.checkAccessState(viewModel.trialManager.getDeviceId())
        if (state == AppAccessState.TRIAL_EXPIRED) { player?.pause(); isTrialExpired = true }
    }

    // ── D-pad & controls state ────────────────────────────────────────────────
    LaunchedEffect(showControls) {
        if (showControls) { dpadZone = DpadZone.CONTROLS; centreIndex = 1 } // default focus: play button
    }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) { kotlinx.coroutines.delay(5000); showControls = false }
    }

    val hasScrubbing = (movieId != null) || episodeId != null
    val hasNext by remember { derivedStateOf { episodeId != null && nextEpisodeAvailable != null } }
    val hasPrev by remember { derivedStateOf { episodeId != null && previousEpisodeAvailable != null } }

    // Centre control button list (rewind, play, forward, next)
    val centreButtons by remember { derivedStateOf {
        buildList {
            if (hasPrev) add("prev")
            if (hasScrubbing) add("rewind")
            add("playpause")
            if (hasScrubbing) add("forward")
            if (hasNext) add("next")
            // Subtitles always last in controls row for VOD/series
            val isLiveTVBtns = movieId == null && episodeId == null
            val isCatchUpBtns = movieId == "catchup"
            if (!isLiveTVBtns && !isCatchUpBtns) add("subtitles")
        }
    }}
    LaunchedEffect(centreButtons) { centreIndex = centreIndex.coerceIn(0, (centreButtons.size - 1).coerceAtLeast(0)) }

    // Icon row: back is always 0; subtitles is 1 if not live; aspect is last if phone
    val iconButtons by remember(showAspectRatioButton, movieId, episodeId) {
        derivedStateOf {
            buildList {
                add("back")
                val isLiveTV  = movieId == null && episodeId == null
                val isCatchUp = movieId == "catchup"
                if (!isLiveTV && !isCatchUp) add("subtitles")
                if (showAspectRatioButton) add("aspect")
            }
        }
    }
    val iconMax by remember { derivedStateOf { (iconButtons.size - 1).coerceAtLeast(0) } }
    LaunchedEffect(iconButtons) { iconIndex = iconIndex.coerceIn(0, iconMax) }

    // Slider
    var sliderPosition    by remember { mutableStateOf(0f) }
    var isDragging        by remember { mutableStateOf(false) }
    val sliderFocusRequester = remember { FocusRequester() }

    // Stable refs for key handler
    val currentDpadZone      by rememberUpdatedState(dpadZone)
    val currentIconIndex     by rememberUpdatedState(iconIndex)
    val currentCentreIndex   by rememberUpdatedState(centreIndex)
    val currentCentreButtons by rememberUpdatedState(centreButtons)
    val currentIconButtons   by rememberUpdatedState(iconButtons)
    val currentNextEpisode   by rememberUpdatedState(nextEpisodeAvailable)
    val currentResizeMode    by rememberUpdatedState(resizeMode)

    // ── Activate focused control ──────────────────────────────────────────────
    val activateFocusedButtonRef = remember { mutableStateOf<() -> Unit>({}) }
    activateFocusedButtonRef.value = {
        when (currentDpadZone) {
            DpadZone.ICONS -> when (currentIconButtons.getOrNull(currentIconIndex)) {
                "back"      -> { stopPlayback(); onBack() }
                "subtitles" -> showMediaSheet = true
                "aspect"    -> {
                    val newResizeMode = when (currentResizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioFrameLayout.RESIZE_MODE_FIT  -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else                                    -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    resizeMode = newResizeMode
                    val newAspectRatio = when (newResizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT  -> AspectRatio.FIT
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatio.FILL
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatio.ZOOM
                        else                                    -> AspectRatio.FIT
                    }
                    scope.launch { context.saveAspectRatio(aspectRatioType, newAspectRatio) }
                }
                else -> Unit
            }
            DpadZone.CONTROLS -> when (currentCentreButtons.getOrNull(currentCentreIndex)) {
                "prev"      -> { previousEpisodeAvailable?.let { onPlayNextEpisode?.invoke(it) } }
                "rewind"    -> player?.seekBack()
                "playpause" -> if (player?.isPlaying == true) player?.pause() else player?.play()
                "forward"   -> player?.seekForward()
                "next"      -> { showNextEpisodePrompt = false; currentNextEpisode?.let { onPlayNextEpisode?.invoke(it) } }
                "subtitles" -> showMediaSheet = true
                else        -> Unit
            }
            DpadZone.SLIDER -> { /* seek already applied on L/R */ }
        }
    }

    // ── D-pad key handler ─────────────────────────────────────────────────────
    val keyHandlerRef = remember { mutableStateOf<(Int, android.view.KeyEvent) -> Boolean>({ _, _ -> false }) }
    keyHandlerRef.value = { keyCode, event ->
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when {
                hasError -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT  -> { errorFocusedButton = ErrorButton.GO_BACK; true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { errorFocusedButton = ErrorButton.RETRY; true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        when (errorFocusedButton) {
                            ErrorButton.GO_BACK -> { stopPlayback(); onBack() }
                            ErrorButton.RETRY   -> { hasError = false; autoRetryCount = 0; player?.prepare(); player?.play() }
                        }; true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> { if (handleBackInternally) { stopPlayback(); onBack() }; true }
                    else -> false
                }
                !showControls -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { showControls = true; true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player?.isPlaying == true) player?.pause() else player?.play(); true }
                    android.view.KeyEvent.KEYCODE_BACK -> { if (handleBackInternally) { stopPlayback(); onBack() }; true }
                    else -> false
                }
                else -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER ->
                    { activateFocusedButtonRef.value(); true }

                    android.view.KeyEvent.KEYCODE_DPAD_UP -> when (currentDpadZone) {
                        DpadZone.CONTROLS -> { if (hasScrubbing) dpadZone = DpadZone.SLIDER else dpadZone = DpadZone.ICONS; true }
                        DpadZone.SLIDER   -> { dpadZone = DpadZone.ICONS; true }
                        DpadZone.ICONS    -> true // already at top
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> when (currentDpadZone) {
                        DpadZone.ICONS    -> { if (hasScrubbing) dpadZone = DpadZone.SLIDER else dpadZone = DpadZone.CONTROLS; true }
                        DpadZone.SLIDER   -> { dpadZone = DpadZone.CONTROLS; true }
                        DpadZone.CONTROLS -> { showControls = false; true }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> when (currentDpadZone) {
                        DpadZone.ICONS    -> { if (currentIconIndex > 0) iconIndex-- ; true }
                        DpadZone.CONTROLS -> { if (currentCentreIndex > 0) centreIndex--; true }
                        DpadZone.SLIDER   -> {
                            val duration = player?.duration ?: 0L
                            sliderPosition = (sliderPosition - 0.01f).coerceAtLeast(0f)
                            player?.seekTo((sliderPosition * duration).toLong()); true
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> when (currentDpadZone) {
                        DpadZone.ICONS    -> { if (currentIconIndex < iconMax) iconIndex++; true }
                        DpadZone.CONTROLS -> { if (currentCentreIndex < currentCentreButtons.size - 1) centreIndex++; true }
                        DpadZone.SLIDER   -> {
                            val duration = player?.duration ?: 0L
                            sliderPosition = (sliderPosition + 0.01f).coerceAtMost(1f)
                            player?.seekTo((sliderPosition * duration).toLong()); true
                        }
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                    { if (player?.isPlaying == true) player?.pause() else player?.play(); true }
                    android.view.KeyEvent.KEYCODE_BACK ->
                    { if (handleBackInternally) { stopPlayback(); onBack() }; true }
                    else -> false
                }
            }
        } else false
    }

    // ── Shared button style helpers ───────────────────────────────────────────
    // Player overlay is always rendered on top of dark video — always use player.textPrimary (white)
    val controlBg    = Color.Black.copy(alpha = 0.50f)
    val controlText  = nsTheme.player.textPrimary
    val focusBorder  = nsTheme.player.textPrimary
    val focusBgTint  = nsTheme.player.textPrimary.copy(alpha = 0.18f)

    // ── Root layout ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (player == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = controlText)

        val fp = forwardingPlayer
        if (fp != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = fp; useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        isFocusable = true; isFocusableInTouchMode = true; requestFocus()
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setOnKeyListener { _, keyCode, event -> keyHandlerRef.value(keyCode, event) }
                    }
                },
                update = { pv -> pv.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize().clickable { showControls = !showControls }
            )

            // ── Controls overlay ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = showControls || !isPlaying,
                enter   = fadeIn(animationSpec = tween(300)),
                exit    = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // Subtle full-screen dark scrim (lighter than before — controls carry their own bg)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

                    // ── Bottom control panel ──────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    // Gradient starts transparent, ends solid black 50%
                                    // Height is generous to cover title + controls comfortably
                                    colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.95f)),
                                    startY = 0f
                                )
                            )
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp, top = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {

                        // ── Programme info ────────────────────────────────────
                        if (nowPlayingTitle != null) {
                            Text(
                                text       = nowPlayingTitle,
                                style      = MaterialTheme.typography.headlineMedium,
                                color      = controlText,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                        val subtitle = nowPlayingSubtitle ?: currentProgramme
                        if (subtitle != null) {
                            Text(
                                text       = subtitle,
                                style      = MaterialTheme.typography.titleLarge,
                                color      = controlText.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (nowPlayingSubtitle == null && currentProgrammeTime != null) {
                            Text(
                                text     = currentProgrammeTime!!,
                                style    = MaterialTheme.typography.titleSmall,
                                color    = controlText.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        // Genre pill row (movies)
                        if (movieId != null && movieId != "catchup" && !movieGenre.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text     = movieGenre!!,
                                        style    = MaterialTheme.typography.labelMedium,
                                        color    = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Description / Plot
                        val description = nowPlayingDescription ?: currentProgrammeDescription
                        val plotText = if (movieId != null && movieId != "catchup") moviePlot else description
                        if (!plotText.isNullOrBlank()) {
                            Text(
                                text     = plotText,
                                style    = MaterialTheme.typography.bodyLarge,
                                color    = controlText.copy(alpha = 0.85f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else if (!description.isNullOrBlank()) {
                            Text(
                                text     = description,
                                style    = MaterialTheme.typography.bodyLarge,
                                color    = controlText.copy(alpha = 0.85f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Cast row (movies only)
                        if (movieId != null && movieId != "catchup" && !movieDirector.isNullOrBlank()) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Text(
                                    text  = "Director",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text     = movieDirector!!,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = controlText.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (movieId != null && movieId != "catchup" && !movieCast.isNullOrBlank()) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Text(
                                    text  = "Cast",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text     = movieCast!!,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = controlText.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── ICONS ROW: back │ ... │ subtitles │ aspect ─────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back button (left side)
                            val backFocused = showControls && currentDpadZone == DpadZone.ICONS &&
                                    currentIconButtons.getOrNull(currentIconIndex) == "back"
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(controlBg)
                                    .then(
                                        if (backFocused)
                                            Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                        else Modifier
                                    )
                            ) {
                                IconButton(onClick = { stopPlayback(); onBack() }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = controlText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Right-side icons (aspect ratio only — subtitles moved to controls row)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showAspectRatioButton) {
                                    val aspectFocused = showControls && currentDpadZone == DpadZone.ICONS &&
                                            currentIconButtons.getOrNull(currentIconIndex) == "aspect"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (aspectFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(onClick = {
                                            val newResizeMode = when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT  -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            }
                                            resizeMode = newResizeMode
                                            val newAspectRatio = when (newResizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT  -> AspectRatio.FIT
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatio.FILL
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatio.ZOOM
                                                else -> AspectRatio.FIT
                                            }
                                            scope.launch { context.saveAspectRatio(aspectRatioType, newAspectRatio) }
                                        }) {
                                            Icon(
                                                imageVector = when (resizeMode) {
                                                    AspectRatioFrameLayout.RESIZE_MODE_FIT  -> Icons.Default.FitScreen
                                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                                                    else                                    -> Icons.Default.ZoomOutMap
                                                },
                                                contentDescription = "Aspect Ratio",
                                                tint = controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── SLIDER + timestamps ──────────────────────────────
                        if (movieId != null || episodeId != null || catchupDuration > 0L) {
                            LaunchedEffect(isPlaying, isDragging) {
                                while (!isDragging) {
                                    val duration = player?.duration ?: 0L
                                    if (duration > 0) sliderPosition = (player?.currentPosition ?: 0L).toFloat() / duration.toFloat()
                                    kotlinx.coroutines.delay(500)
                                }
                            }
                            val sliderFocused = currentDpadZone == DpadZone.SLIDER && showControls
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = if (sliderFocused) 2.dp else 0.dp)) {
                                Slider(
                                    value               = sliderPosition,
                                    onValueChange       = { isDragging = true; sliderPosition = it },
                                    onValueChangeFinished = {
                                        isDragging = false
                                        player?.seekTo(((sliderPosition * (player?.duration ?: 0L)).toLong()))
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor         = if (sliderFocused) MaterialTheme.colorScheme.primary else controlText.copy(alpha = 0.8f),
                                        activeTrackColor   = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = controlText.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(sliderFocusRequester)
                                        .focusable()
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text  = formatDuration(player?.currentPosition ?: 0L),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = controlText.copy(alpha = 0.7f)
                                )
                                Text(
                                    text  = formatDuration((player?.duration ?: 0L).coerceAtLeast(0)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = controlText.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // ── CONTROLS ROW: rewind │ play/pause │ forward │ next │ subtitles ─
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasScrubbing) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "rewind"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (focused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { player?.seekBack() },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.FastRewind,
                                                contentDescription = "Rewind 60s",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

                                // Play / Pause — slightly larger
                                val playFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                        currentCentreButtons.getOrNull(currentCentreIndex) == "playpause"
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(controlBg)
                                        .then(
                                            if (playFocused)
                                                Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                            else Modifier
                                        )
                                ) {
                                    IconButton(
                                        onClick  = { if (player?.isPlaying == true) player?.pause() else player?.play() },
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = controlText,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                if (hasScrubbing) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "forward"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (focused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { player?.seekForward() },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.FastForward,
                                                contentDescription = "Forward 60s",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

                                if (hasPrev) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "prev"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (focused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { previousEpisodeAvailable?.let { onPlayNextEpisode?.invoke(it) } },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious,
                                                contentDescription = "Previous Episode",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                if (hasNext) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "next"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (focused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { showNextEpisodePrompt = false; onPlayNextEpisode?.invoke(nextEpisodeAvailable!!) },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "Next Episode",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                // Subtitles button — inline with controls
                                val isLiveTV2  = movieId == null && episodeId == null
                                val isCatchUp2 = movieId == "catchup"
                                if (!isLiveTV2 && !isCatchUp2) {
                                    val subFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "subtitles"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (subFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(onClick = { showMediaSheet = true }, modifier = Modifier.size(52.dp)) {
                                            Icon(
                                                Icons.Default.Subtitles,
                                                contentDescription = "Audio & Subtitles",
                                                tint = controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // end Column (bottom panel)
                } // end Box (controls overlay)
            } // end AnimatedVisibility
        } // end if fp != null

        // ── Media sheet ───────────────────────────────────────────────────────
        val currentPlayer = player
        if (showMediaSheet && currentPlayer != null) {
            val sheetState         = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val isMovieOrEpisode   = (movieId != null && movieId != "catchup") || episodeId != null
            MediaSheet(
                player              = currentPlayer,
                isMovieOrEpisode    = isMovieOrEpisode,
                movieId             = movieId,
                episodeId           = episodeId,
                seriesId            = seriesId,
                channelUrl          = channelUrl,
                subtitleSheetState  = subtitleSheetState,
                onSubtitleStateChange = { subtitleSheetState = it },
                sheetState          = sheetState,
                viewModel           = viewModel,
                scope               = scope,
                onDismiss           = { showMediaSheet = false }
            )
        }

        // ── Error card ────────────────────────────────────────────────────────
        if (hasError) {
            Box(
                modifier        = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp).widthIn(max = 480.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier              = Modifier.padding(28.dp),
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Playback Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (errorFocusedButton == ErrorButton.GO_BACK)
                                Button(onClick = { stopPlayback(); onBack() }) { Text("Go Back") }
                            else
                                OutlinedButton(onClick = { stopPlayback(); onBack() }) { Text("Go Back") }
                            if (errorFocusedButton == ErrorButton.RETRY)
                                Button(onClick = { hasError = false; autoRetryCount = 0; player?.prepare(); player?.play() }) { Text("Retry") }
                            else
                                OutlinedButton(onClick = { hasError = false; autoRetryCount = 0; player?.prepare(); player?.play() }) { Text("Retry") }
                        }
                        Text("← → to switch   OK to confirm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // ── Next episode prompt ───────────────────────────────────────────────
        if (showNextEpisodePrompt && nextEpisodeAvailable != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Up Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text       = "S${nextEpisodeAvailable!!.seasonNum}E${nextEpisodeAvailable!!.episodeNum} · ${nextEpisodeAvailable!!.name}",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1
                        )
                    }
                    Button(onClick = { showNextEpisodePrompt = false; onPlayNextEpisode?.invoke(nextEpisodeAvailable!!) }) { Text("Play Now") }
                    TextButton(onClick = { showNextEpisodePrompt = false }) { Text("Dismiss") }
                }
            }
        }

        // ── Trial expired ─────────────────────────────────────────────────────
        if (isTrialExpired) TrialExpiredScreen(onLicenceActivated = { isTrialExpired = false; player?.play() })

    } // end root Box
}

// ── TrackItem ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackItem(track: Track, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color    = if (track.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text       = track.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (track.language != track.label && track.language != "Off") {
                    Text(text = track.language, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── MediaSheet ────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MediaSheet(
    player: Player, isMovieOrEpisode: Boolean, movieId: String?, episodeId: String?, seriesId: String?,
    channelUrl: String, subtitleSheetState: SubtitleSheetState, onSubtitleStateChange: (SubtitleSheetState) -> Unit,
    sheetState: SheetState, viewModel: PlayerViewModel, scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var selectedTab by remember { mutableStateOf(0) }
        val tracks                  = player.currentTracks
        val trackSelectionParameters = player.trackSelectionParameters

        val audioTracks = remember(tracks) {
            buildList {
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == C.TRACK_TYPE_AUDIO) for (j in 0 until group.length) {
                        val format = group.getTrackFormat(j)
                        add(Track(TrackType.AUDIO, i, j, format.language ?: "Unknown", format.label ?: format.language ?: "Audio Track ${j + 1}", group.isTrackSelected(j)))
                    }
                }
            }
        }
        val subtitleTracks = remember(tracks) {
            buildList {
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == C.TRACK_TYPE_TEXT) for (j in 0 until group.length) {
                        val format = group.getTrackFormat(j)
                        add(Track(TrackType.SUBTITLE, i, j, format.language ?: "Unknown", format.label ?: format.language ?: "Subtitle ${j + 1}", group.isTrackSelected(j)))
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Audio & Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Audio (${audioTracks.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Embedded (${subtitleTracks.size})") })
                if (isMovieOrEpisode) Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Download") })
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (selectedTab) {
                0 -> {
                    if (audioTracks.isEmpty())
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No audio tracks available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    else Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        audioTracks.forEach { track ->
                            TrackItem(track = track, onClick = {
                                val builder = trackSelectionParameters.buildUpon()
                                builder.setOverrideForType(TrackSelectionOverride(tracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                                player.trackSelectionParameters = builder.build(); onDismiss()
                            })
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        TrackItem(
                            track   = Track(TrackType.SUBTITLE, -1, -1, "Off", "Off", subtitleTracks.none { it.isSelected }),
                            onClick = {
                                val builder = trackSelectionParameters.buildUpon()
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                player.trackSelectionParameters = builder.build(); onDismiss()
                            }
                        )
                        if (subtitleTracks.isEmpty())
                            Text("No embedded subtitles available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        else subtitleTracks.forEach { track ->
                            TrackItem(track = track, onClick = {
                                val builder = trackSelectionParameters.buildUpon()
                                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                builder.setOverrideForType(TrackSelectionOverride(tracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                                player.trackSelectionParameters = builder.build(); onDismiss()
                            })
                        }
                    }
                }
                2 -> {
                    val availableLanguages = remember { listOf("en" to "English", "fr" to "French", "de" to "German", "es" to "Spanish", "it" to "Italian", "ar" to "Arabic", "nl" to "Dutch", "pt" to "Portuguese", "pl" to "Polish", "sv" to "Swedish") }
                    val savedLanguages     by viewModel.subtitlePreferences.preferredLanguages.collectAsState(initial = setOf("en"))
                    var selectedLanguages  by remember(savedLanguages) { mutableStateOf(savedLanguages) }
                    var searchState        by remember { mutableStateOf<SubtitleSheetState>(SubtitleSheetState.Loading) }
                    var lastSearchedLanguages by remember { mutableStateOf<Set<String>?>(null) }

                    LaunchedEffect(selectedLanguages) {
                        if (selectedLanguages != lastSearchedLanguages) {
                            lastSearchedLanguages = selectedLanguages; searchState = SubtitleSheetState.Loading
                            scope.launch { viewModel.subtitlePreferences.savePreferredLanguages(selectedLanguages) }
                            try {
                                val langString = selectedLanguages.joinToString(",")
                                val results: List<SubtitleLanguage> = kotlinx.coroutines.withTimeout(15_000L) {
                                    when {
                                        movieId != null   -> { val movie = viewModel.getMovieById(movieId); viewModel.subtitleManager.searchMovieSubtitles(movie?.name ?: "", movie?.releaseDate?.take(4), langString) }
                                        episodeId != null -> { val episode = viewModel.getEpisodeById(episodeId); val series = seriesId?.let { viewModel.getSeriesById(it) }; if (episode != null && series != null) viewModel.subtitleManager.searchEpisodeSubtitles(series.name, episode.seasonNum, episode.episodeNum, langString) else emptyList() }
                                        else              -> emptyList()
                                    }
                                }
                                searchState = if (results.isEmpty()) SubtitleSheetState.Error("No subtitles found") else SubtitleSheetState.Languages(results)
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { searchState = SubtitleSheetState.Error("Search timed out") }
                            catch (e: Exception) { searchState = SubtitleSheetState.Error(e.message ?: "Search failed") }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Languages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableLanguages.forEach { (code, name) ->
                                FilterChip(
                                    selected = code in selectedLanguages,
                                    onClick  = {
                                        selectedLanguages = if (code in selectedLanguages) {
                                            if (selectedLanguages.size > 1) selectedLanguages.minus(code) else selectedLanguages
                                        } else {
                                            if (selectedLanguages.size < 6) selectedLanguages.plus(code) else selectedLanguages
                                        }
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                        HorizontalDivider()
                        SubtitleSheetContent(
                            subtitleState      = searchState,
                            onLanguageSelected = { language ->
                                searchState = SubtitleSheetState.Downloading
                                scope.launch {
                                    val cacheKey = movieId ?: episodeId ?: "unknown"
                                    val result   = viewModel.subtitleManager.downloadSubtitle(language, cacheKey)
                                    when (result) {
                                        is SubtitleResult.Success -> {
                                            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(result.file))
                                                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                                                .setLanguage(language.code)
                                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .build()
                                            val currentPosition = player.currentPosition
                                            val newMediaItem    = MediaItem.Builder().setUri(channelUrl).setSubtitleConfigurations(listOf(subtitleConfig)).build()
                                            player.setMediaItem(newMediaItem); player.prepare(); player.seekTo(currentPosition); player.play(); onDismiss()
                                        }
                                        is SubtitleResult.Error -> searchState = SubtitleSheetState.Error(result.message)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours        = totalSeconds / 3600
    val minutes      = (totalSeconds % 3600) / 60
    val seconds      = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

private data class Track(val type: TrackType, val groupIndex: Int, val trackIndex: Int, val language: String, val label: String, val isSelected: Boolean)
private enum class TrackType { AUDIO, SUBTITLE }