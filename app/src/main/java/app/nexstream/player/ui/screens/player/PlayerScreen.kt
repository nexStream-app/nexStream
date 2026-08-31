package app.nexstream.player.ui.screens.player

import android.app.Activity
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.nexstream.player.cast.CastDeviceSheet
import app.nexstream.player.cast.CastManager
import app.nexstream.player.cast.CastState
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.license.AppAccessState
import app.nexstream.player.service.NexStreamPlaybackService
import app.nexstream.player.subtitle.SubtitleLanguage
import app.nexstream.player.subtitle.SubtitleResult
import app.nexstream.player.subtitle.WhisperSubtitleManager
import app.nexstream.player.subtitle.WhisperTapProcessor
import app.nexstream.player.ui.screens.series.SeriesViewModel
import app.nexstream.player.ui.screens.trial.TrialExpiredScreen
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.getAspectRatioFlow
import app.nexstream.player.ui.theme.getAutoFrameRateFlow
import app.nexstream.player.ui.theme.getSmartBufferFlow
import app.nexstream.player.ui.theme.getWhisperSubtitlesFlow
import app.nexstream.player.ui.theme.getWhisperTranslateToFlow
import app.nexstream.player.ui.theme.getWhisperAutostartLiveFlow
import app.nexstream.player.ui.theme.getAutoLangDetectFlow
import app.nexstream.player.ui.theme.getProxyModeFlow
import app.nexstream.player.ui.theme.getProxyHostFlow
import app.nexstream.player.ui.theme.getProxyPortFlow
import app.nexstream.player.ui.theme.getProxyTypeFlow
import app.nexstream.player.ui.theme.getProxyUsernameFlow
import app.nexstream.player.ui.theme.getProxyPasswordFlow
import app.nexstream.player.ui.theme.saveWhisperTranslateTo
import app.nexstream.player.ui.theme.saveAspectRatio
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onPreviousChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    onOpenMultiScreen: (() -> Unit)? = null,
    profileId: String = "default",
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val isTv    = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) }
    val nsTheme = LocalNexStreamTheme.current

    // ── PiP lifecycle ─────────────────────────────────────────────────────────
    val isInPipMode by app.nexstream.player.MainActivity.isInPipMode
    DisposableEffect(Unit) {
        app.nexstream.player.MainActivity.isPlayerActive.value = true
        onDispose { app.nexstream.player.MainActivity.isPlayerActive.value = false }
    }
    // Collected here; LaunchedEffect is below stopPlayback declaration
    val pipStopSignal by app.nexstream.player.MainActivity.stopPipSignal.collectAsState()

    // ── Player prefs ──────────────────────────────────────────────────────────
    val autoFrameRate    by context.getAutoFrameRateFlow().collectAsState(initial = true)
    val smartBuffer      by context.getSmartBufferFlow().collectAsState(initial = true)
    val whisperEnabled       by context.getWhisperSubtitlesFlow().collectAsState(initial = false)
    val whisperTranslateTo   by context.getWhisperTranslateToFlow().collectAsState(initial = false)
    val whisperAutoStartLive by context.getWhisperAutostartLiveFlow().collectAsState(initial = false)
    val autoLangDetect       by context.getAutoLangDetectFlow().collectAsState(initial = false)
    val proxyMode by context.getProxyModeFlow().collectAsState(initial = "OFF")
    val proxyHost by context.getProxyHostFlow().collectAsState(initial = "")
    val proxyPort by context.getProxyPortFlow().collectAsState(initial = 8080)
    val proxyType by context.getProxyTypeFlow().collectAsState(initial = "HTTP")
    val proxyUser by context.getProxyUsernameFlow().collectAsState(initial = "")
    val proxyPass by context.getProxyPasswordFlow().collectAsState(initial = "")
    val licenceKey = remember {
        context.getSharedPreferences("nexstream_licence", android.content.Context.MODE_PRIVATE)
            .getString("licence_key", null)
    }
    val deviceId = remember {
        context.getSharedPreferences("nexstream_licence", android.content.Context.MODE_PRIVATE)
            .getString("stable_device_id", "unknown") ?: "unknown"
    }

    // ── Whisper AI subtitles ──────────────────────────────────────────────────
    val whisperManager = viewModel.whisperSubtitleManager
    val whisperTapProcessor = remember {
        WhisperTapProcessor { shorts, rate, channels ->
            whisperManager.processAudio(shorts, rate, channels)
        }
    }
    val whisperText by whisperManager.currentText.collectAsState()
    var ccActive by remember { mutableStateOf(false) }

    // When feature is turned off in settings, deactivate the session
    LaunchedEffect(whisperEnabled) {
        if (!whisperEnabled) ccActive = false
    }
    // Auto-start on Live TV if the pref is set; or on VOD/Series whenever AI subtitles are enabled
    LaunchedEffect(movieId, episodeId, whisperEnabled, whisperAutoStartLive) {
        when {
            whisperEnabled && whisperAutoStartLive && movieId == null && episodeId == null -> ccActive = true
            whisperEnabled && (movieId != null || episodeId != null) -> ccActive = true
        }
    }
    // Drive whisper manager from the session state.
    // channelUrl is a key so that channel switches re-arm the processor even when ccActive hasn't changed
    // (onDispose calls setEnabled(false)/deactivate(); without channelUrl this LaunchedEffect wouldn't re-fire).
    LaunchedEffect(ccActive, channelUrl) {
        whisperTapProcessor.setEnabled(ccActive)
        if (ccActive) {
            // Ensure translate preference is applied at activation time, not just on pref change.
            // This prevents a race where initial=false fires first and resets translate mode.
            whisperManager.setTranslateToEnglish(whisperTranslateTo)
            whisperManager.activate(movieId == null && episodeId == null)
        } else {
            whisperManager.deactivate()
        }
    }

    LaunchedEffect(whisperTranslateTo) {
        whisperManager.setTranslateToEnglish(whisperTranslateTo)
    }

    // ── ExoPlayer subtitle cues → sidebar ─────────────────────────────────────
    var subtitleCueLines by remember { mutableStateOf<List<String>>(emptyList()) }

    // ── Sleep timer ───────────────────────────────────────────────────────────
    var sleepTimerEndsAt      by remember { mutableLongStateOf(0L) }
    var sleepTimerDurationMs  by remember { mutableLongStateOf(0L) }
    var showSleepTimerDialog  by remember { mutableStateOf(false) }

    // ── Playback speed ────────────────────────────────────────────────────────
    var playbackSpeed         by remember { mutableStateOf(1.0f) }
    var showSpeedDialog       by remember { mutableStateOf(false) }

    // ── Subtitle delay ────────────────────────────────────────────────────────
    var subtitleDelayMs          by remember { mutableIntStateOf(0) }
    var showSubtitleDelayDialog  by remember { mutableStateOf(false) }

    // ── Stats overlay ─────────────────────────────────────────────────────────
    var showStats         by remember { mutableStateOf(false) }
    var videoCodecName    by remember { mutableStateOf<String?>(null) }
    var audioCodecName    by remember { mutableStateOf<String?>(null) }
    var bufferHealthMs    by remember { mutableLongStateOf(0L) }

    // ── Series: "Still Watching?" after 3 consecutive episodes ────────────────
    var episodesWatchedInRow    by remember(seriesId) { mutableIntStateOf(0) }
    var showStillWatchingDialog by remember { mutableStateOf(false) }

    var showMediaSheet  by remember { mutableStateOf(false) }
    var showControls    by remember { mutableStateOf(true) }
    var isTrialExpired  by remember { mutableStateOf(false) }
    var subtitleAtTop   by remember { mutableStateOf(false) }

    // Audio language detection (live TV only) — resets on channel change
    var audioLangDetected        by remember(channelUrl) { mutableStateOf<String?>(null) }
    var audioLangPromptDismissed by remember(channelUrl) { mutableStateOf(false) }

    LaunchedEffect(audioLangDetected) {
        whisperManager.setDetectedLanguage(audioLangDetected)
    }

    // D-pad: two zones — ICONS row and CONTROLS row (below), then SLIDER
    var dpadZone     by remember { mutableStateOf(DpadZone.CONTROLS) }
    var iconIndex    by remember { mutableStateOf(ICON_BACK) }
    var centreIndex  by remember { mutableStateOf(0) }

    val isAndroidTV  = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val showAspectRatioButton = !isAndroidTV

    // ── Casting (mobile only) ─────────────────────────────────────────────────
    val castManager = remember { CastManager(context) }
    DisposableEffect(Unit) { onDispose { castManager.destroy() } }
    val castState by castManager.castState.collectAsState()
    val isCasting by remember { derivedStateOf { castState is CastState.Active } }
    var showCastSheet       by remember { mutableStateOf(false) }
    var castIsPlaying       by remember { mutableStateOf(true) }
    var castPositionMs      by remember { mutableStateOf(0L) }
    var castEverConnected   by remember { mutableStateOf(false) }

    LaunchedEffect(showCastSheet) {
        if (showCastSheet) castManager.startDiscovery()
        else castManager.stopDiscovery()
    }

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
                val movie = if (movieBase.plot.isNullOrBlank()) {
                    val rawId = movieId.removePrefix("${movieBase.playlistId}-")
                    runCatching { viewModel.getMovieDetails(movieBase.playlistId, rawId) }.getOrNull()
                        ?: movieBase
                } else movieBase
                moviePlot     = movie.plot
                movieCast     = movie.cast
                movieGenre    = movie.genre
                movieDirector = movie.director
            }
        }
    }

    var currentProgramme            by remember { mutableStateOf<String?>(null) }
    var currentProgrammeTime        by remember { mutableStateOf<String?>(null) }
    var currentProgrammeDescription by remember { mutableStateOf<String?>(null) }
    var nextProgramme               by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(channelUrl) {
        if (movieId == null && episodeId == null) {
            viewModel.getNextProgrammeForUrl(channelUrl).collectLatest { programme ->
                nextProgramme = programme?.title
            }
        }
    }

    // Update Whisper prompt whenever content metadata changes.
    // Live TV/CatchUp use context prompts; movies and episodes use no prompt — passing title/description
    // as a Whisper prompt causes hallucinations (model outputs the prompt text when audio is quiet).
    LaunchedEffect(ccActive, movieId, episodeId, nowPlayingTitle, nowPlayingSubtitle, nowPlayingDescription, currentProgramme, currentProgrammeDescription) {
        if (!ccActive) return@LaunchedEffect
        val isLiveTV = movieId == null && episodeId == null
        when {
            isLiveTV -> whisperManager.setLiveTvContext(currentProgramme, currentProgrammeDescription)
            episodeId != null || (movieId != null && movieId != "catchup") -> whisperManager.setInitialPrompt(null)
            else -> whisperManager.setInitialPrompt(buildWhisperPrompt(
                movieId, episodeId,
                nowPlayingTitle, nowPlayingSubtitle, nowPlayingDescription,
                currentProgramme, currentProgrammeDescription,
            ))
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
        if (isCasting) castManager.disconnect()
        player?.stop()
        if (!isAndroidTV) context.stopService(Intent(context, NexStreamPlaybackService::class.java))
    }

    BackHandler(enabled = handleBackInternally) {
        if (showControls) showControls = false
        else { stopPlayback(); onBack() }
    }

    // Stop playback when PiP stop action fires (X button on API 31+ or Stop button on older)
    LaunchedEffect(pipStopSignal) {
        if (pipStopSignal > 0) { stopPlayback(); onBack() }
    }

    // ── Content type flags ────────────────────────────────────────────────────
    val isCatchup = movieId == "catchup" || catchupDuration > 0L
    val isVod     = movieId != null && movieId != "catchup"

    // Restore last-watched position for catchup once the player is ready
    LaunchedEffect(player, channelUrl, isCatchup) {
        if (!isCatchup || player == null) return@LaunchedEffect
        val pos = viewModel.getCatchupResumePosition(channelUrl, profileId)
        if (pos > 0) player?.seekTo(pos)
    }

    // Resume movie/episode from last saved position when launched without an explicit start position.
    // Handles the case where a theme sync restarts the player with startPosition = 0.
    LaunchedEffect(player, movieId, episodeId) {
        if (player == null || startPosition > 0L) return@LaunchedEffect
        val resumePos = when {
            movieId != null && movieId != "catchup" -> viewModel.getMovieResumePosition(movieId, profileId)
            episodeId != null -> viewModel.getEpisodeResumePosition(episodeId, profileId)
            else -> return@LaunchedEffect
        }
        if (resumePos > 0L) player?.seekTo(resumePos)
    }

    // ── LoadControl builder ───────────────────────────────────────────────────
    fun buildLoadControl(): DefaultLoadControl =
        if (smartBuffer && (isCatchup || isVod)) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,  // minBuffer
                    60_000,  // maxBuffer — reduced from 120s; 2 min was causing memory pressure on low-RAM Fire Sticks
                    1_500,   // bufferForPlayback — start fast
                    5_000    // bufferAfterRebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30_000, true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    10_000,  // minBuffer — increased from 5s; absorbs brief network hiccups on live TV
                    30_000,  // maxBuffer
                    1_500,   // bufferForPlayback
                    5_000    // bufferAfterRebuffer
                )
                .build()
        }

    // ── Android TV: build ExoPlayer directly ──────────────────────────────────
    if (isAndroidTV) {
        DisposableEffect(channelUrl, smartBuffer, proxyMode, proxyHost, proxyPort, proxyType) {
            val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink {
                    // Downmix multichannel PCM to stereo so devices that lack 5.1/7.1
                    // AudioTrack support (e.g. Nokia Streaming Box) don't crash.
                    val downMixer = androidx.media3.common.audio.ChannelMixingAudioProcessor().apply {
                        // ChannelMixingAudioProcessor requires a matrix for EVERY channel count it
                        // encounters — including stereo. Missing matrices throw UnhandledAudioFormatException.
                        // Mono passthrough
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            1, 1, floatArrayOf(1.000f)
                        ))
                        // Stereo passthrough
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            2, 2, floatArrayOf(1.000f, 0.000f, 0.000f, 1.000f)
                        ))
                        // 3ch (L R C) → stereo
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            3, 2, floatArrayOf(1.000f, 0.000f, 0.000f, 1.000f, 0.707f, 0.707f)
                        ))
                        // 4ch (L R Ls Rs) → stereo
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            4, 2, floatArrayOf(1.000f, 0.000f, 0.000f, 1.000f, 0.707f, 0.000f, 0.000f, 0.707f)
                        ))
                        // 5ch (L R C Ls Rs) → stereo
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            5, 2, floatArrayOf(1.000f, 0.000f, 0.000f, 1.000f, 0.707f, 0.707f, 0.707f, 0.000f, 0.000f, 0.707f)
                        ))
                        // 5.1 (FL FR FC LFE BL BR) → stereo using ITU-R BS.775 coefficients
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            6, 2,
                            floatArrayOf(
                                1.000f, 0.000f,  // FL  → L, R
                                0.000f, 1.000f,  // FR  → L, R
                                0.707f, 0.707f,  // FC  → L, R
                                0.000f, 0.000f,  // LFE → L, R
                                0.707f, 0.000f,  // BL  → L, R
                                0.000f, 0.707f   // BR  → L, R
                            )
                        ))
                        // 7ch (L R C LFE Ls Rs Cs) → stereo
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            7, 2,
                            floatArrayOf(1.000f, 0.000f, 0.000f, 1.000f, 0.707f, 0.707f, 0.000f, 0.000f, 0.707f, 0.000f, 0.000f, 0.707f, 0.354f, 0.354f)
                        ))
                        // 7.1 (FL FR FC LFE BL BR SL SR) → stereo
                        putChannelMixingMatrix(androidx.media3.common.audio.ChannelMixingMatrix(
                            8, 2,
                            floatArrayOf(
                                1.000f, 0.000f,  // FL  → L, R
                                0.000f, 1.000f,  // FR  → L, R
                                0.707f, 0.707f,  // FC  → L, R
                                0.000f, 0.000f,  // LFE → L, R
                                0.707f, 0.000f,  // BL  → L, R
                                0.000f, 0.707f,  // BR  → L, R
                                0.707f, 0.000f,  // SL  → L, R
                                0.000f, 0.707f   // SR  → L, R
                            )
                        ))
                    }
                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        // whisperTapProcessor MUST be first — it taps the raw decoded PCM
                        // before ChannelMixingAudioProcessor transforms the buffer. If downMixer
                        // is first, its output ByteBuffer arrives consumed (remaining==0) at
                        // whisperTapProcessor, producing silent 0-sample chunks.
                        .setAudioProcessors(arrayOf(whisperTapProcessor, downMixer))
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
                }
            }
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
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
            val streamProxy: java.net.Proxy = when (proxyMode) {
                "BUILTIN" -> java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress("proxy.nexstream.uk", 3129)
                )
                "CUSTOM"  -> if (proxyHost.isNotBlank()) java.net.Proxy(
                    if (proxyType == "SOCKS5") java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress(proxyHost, proxyPort)
                ) else java.net.Proxy.NO_PROXY
                else      -> java.net.Proxy.NO_PROXY
            }
            val trustAllOkHttp = okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .proxy(streamProxy)
                .apply {
                    val needsAuth = proxyMode == "BUILTIN" || (proxyMode == "CUSTOM" && proxyUser.isNotBlank())
                    if (needsAuth) {
                        val capturedMode     = proxyMode
                        val capturedKey      = licenceKey
                        val capturedUser     = proxyUser
                        val capturedPass     = proxyPass
                        val capturedDeviceId = deviceId
                        proxyAuthenticator(object : okhttp3.Authenticator {
                            override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
                                val creds = when (capturedMode) {
                                    "BUILTIN" -> capturedKey?.let { okhttp3.Credentials.basic(it, capturedDeviceId) }
                                    "CUSTOM"  -> okhttp3.Credentials.basic(capturedUser, capturedPass)
                                    else      -> null
                                } ?: return null
                                return response.request.newBuilder()
                                    .header("Proxy-Authorization", creds)
                                    .build()
                            }
                        })
                    }
                }
                .build()
            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(trustAllOkHttp)
            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
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
                                .setPreferredAudioLanguage("en")
                                .setTunnelingEnabled(false) // tunneling bypasses AudioProcessor chain, breaking Whisper tap
                                .setMaxAudioChannelCount(2)
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
                        } else {
                            // Detect selected audio track language for live TV prompt
                            val selectedLang = (0 until tracks.groups.size)
                                .firstNotNullOfOrNull { i ->
                                    val g = tracks.groups[i]
                                    if (g.type != C.TRACK_TYPE_AUDIO) return@firstNotNullOfOrNull null
                                    (0 until g.length).firstOrNull { j -> g.isTrackSelected(j) }
                                        ?.let { j -> g.getTrackFormat(j).language }
                                }
                            val lc = selectedLang?.lowercase()?.take(2)
                            if (!lc.isNullOrBlank() && lc != "und" && lc != "en") {
                                audioLangDetected = lc
                            }
                        }
                    }
                }
            })

            // Reconnect on audio PTS discontinuity — IPTV streams occasionally reset timestamps
            // by hours. Media3 handles UnexpectedDiscontinuityException non-fatally (logs only),
            // so onPlayerError never fires, the video renderer tries to catch up, and the picture
            // freezes. We intercept via AnalyticsListener and force a clean reconnect.
            if (!isCatchup && !isVod) {
                exoPlayer.addAnalyticsListener(object : AnalyticsListener {
                    override fun onAudioSinkError(
                        eventTime: AnalyticsListener.EventTime,
                        audioSinkError: Exception
                    ) {
                        if (audioSinkError is androidx.media3.exoplayer.audio.AudioSink.UnexpectedDiscontinuityException) {
                            android.util.Log.d("nexPlayer", "PTS discontinuity on live stream — reconnecting")
                            scope.launch {
                                delay(500L)
                                exoPlayer.seekToDefaultPosition()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    }
                })
            }

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
                whisperTapProcessor.setEnabled(false)
                whisperManager.deactivate()
                if (movieId != null && movieId != "catchup") {
                    val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                    if (dur > 0 && pos < dur - 120_000) viewModel.savePlaybackPositionSync(movieId, pos, dur, profileId)
                }
                if (episodeId != null) {
                    val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                    if (dur > 0 && pos < dur - 120_000) viewModel.saveEpisodePositionSync(episodeId, pos, dur, seriesId, profileId)
                }
                if (isCatchup) {
                    val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                    if (dur > 0 && pos < dur - 30_000) viewModel.saveCatchupPositionSync(channelUrl, pos, dur, profileId)
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
                whisperTapProcessor.setEnabled(false)
                whisperManager.deactivate()
                val ctrl = player
                if (ctrl != null) {
                    if (movieId != null && movieId != "catchup") { val pos = ctrl.currentPosition; val dur = ctrl.duration; if (dur > 0 && pos < dur - 120_000) viewModel.savePlaybackPositionSync(movieId, pos, dur, profileId) }
                    if (episodeId != null) { val pos = ctrl.currentPosition; val dur = ctrl.duration; if (dur > 0 && pos < dur - 120_000) viewModel.saveEpisodePositionSync(episodeId, pos, dur, seriesId, profileId) }
                    if (isCatchup) { val pos = ctrl.currentPosition; val dur = ctrl.duration; if (dur > 0 && pos < dur - 30_000) viewModel.saveCatchupPositionSync(channelUrl, pos, dur, profileId) }
                }
                MediaController.releaseFuture(controllerFuture); player = null
                context.stopService(Intent(context, NexStreamPlaybackService::class.java))
            }
        }
    }

    // Stable ref so onCues (inside DisposableEffect) always reads the latest delay value
    val subtitleDelayRef = rememberUpdatedState(subtitleDelayMs)

    // ── Playback state ────────────────────────────────────────────────────────
    var isPlaying          by remember { mutableStateOf(true) }
    var isBuffering        by remember { mutableStateOf(false) }
    var videoWidth         by remember { mutableIntStateOf(0) }
    var videoHeight        by remember { mutableIntStateOf(0) }
    var videoBitrateKbps   by remember { mutableIntStateOf(0) }
    var hasError           by remember { mutableStateOf(false) }
    var errorMessage       by remember { mutableStateOf("") }
    var errorFocusedButton by remember { mutableStateOf(ErrorButton.RETRY) }
    var autoRetryCount     by remember { mutableStateOf(0) }
    val maxAutoRetries = if (isCatchup || isVod) 3 else 8
    var errorPosition      by remember { mutableStateOf(0L) }
    var showNextEpisodePrompt    by remember { mutableStateOf(false) }
    var nextEpisodeDismissed     by remember { mutableStateOf(false) }
    var nextEpisodeAvailable     by remember { mutableStateOf<EpisodeEntity?>(null) }
    var previousEpisodeAvailable by remember { mutableStateOf<EpisodeEntity?>(null) }
    var shouldAutoPlayNext       by remember { mutableStateOf(false) }

    val videoQualityLabel: String? = remember(videoWidth, videoHeight, videoBitrateKbps) {
        if (videoWidth <= 0) return@remember null
        val res = when {
            videoHeight >= 2160 -> "4K"
            videoHeight >= 1080 -> "1080p"
            videoHeight >= 720  -> "720p"
            videoHeight >= 576  -> "576p"
            videoHeight >= 480  -> "480p"
            else -> "${videoHeight}p"
        }
        if (videoBitrateKbps > 0) {
            val mb = videoBitrateKbps / 1000f
            "$res • ${"%.1f".format(mb)} Mbps"
        } else res
    }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) { hasError = false }
                if (playbackState == Player.STATE_ENDED) {
                    if (movieId != null && movieId != "catchup") {
                        viewModel.clearPlaybackPosition(movieId, profileId)
                        scope.launch { delay(800L); onBack() }
                    }
                    if (episodeId != null) {
                        viewModel.clearEpisodePosition(episodeId, profileId)
                        if (nextEpisodeAvailable != null) shouldAutoPlayNext = true
                        else scope.launch { delay(800L); onBack() }
                    }
                    if (isCatchup) viewModel.clearCatchupPosition(channelUrl, profileId)
                    // IPTV server closed the connection — reconnect automatically
                    if (!isCatchup && !isVod) {
                        android.util.Log.d("nexPlayer", "Live stream ended (server EOF) — reconnecting in 2s")
                        scope.launch {
                            delay(2_000L)
                            p.seekToDefaultPosition()
                            p.prepare()
                            p.play()
                        }
                    }
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
                    val savedPos = if (isCatchup || isVod) p.currentPosition.takeIf { it > 0L } else null
                    android.util.Log.d("PlayerScreen", "Auto-retry $autoRetryCount/$maxAutoRetries after error: ${error.message}")
                    scope.launch {
                        val delay = if (autoRetryCount <= 3) 1500L * autoRetryCount else 10_000L
                        kotlinx.coroutines.delay(delay)
                        if (!isCatchup && !isVod) p.seekToDefaultPosition()
                        p.prepare()
                        if (savedPos != null) p.seekTo(savedPos)
                        p.play()
                    }
                } else {
                    if (isCatchup || isVod) errorPosition = p.currentPosition.takeIf { it > 0L } ?: 0L
                    hasError = true; errorFocusedButton = ErrorButton.RETRY
                    errorMessage = error.message ?: "Playback error occurred"
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (playing) autoRetryCount = 0 }
            @androidx.annotation.OptIn(UnstableApi::class)
            override fun onCues(cueGroup: CueGroup) {
                val lines = cueGroup.cues.mapNotNull { it.text?.toString() }.filter { it.isNotBlank() }
                val delayMs = subtitleDelayRef.value.toLong()
                if (delayMs <= 0L) {
                    subtitleCueLines = lines
                } else {
                    scope.launch { delay(delayMs); subtitleCueLines = lines }
                }
            }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0) {
                    videoWidth = size.width
                    videoHeight = size.height
                    val br = (p as? ExoPlayer)?.videoFormat?.bitrate ?: -1
                    if (br > 0) videoBitrateKbps = br / 1000
                }
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    // Poll video format for adaptive bitrate changes (every 4s while playing)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val br = (player as? ExoPlayer)?.videoFormat?.bitrate ?: -1
            if (br > 0) videoBitrateKbps = br / 1000
            kotlinx.coroutines.delay(4_000L)
        }
    }

    // Buffering watchdog: reconnect if stuck — 30s for live TV, 60s for VOD/catchup
    LaunchedEffect(isBuffering) {
        if (!isBuffering || hasError) return@LaunchedEffect
        val timeout = if (isCatchup || isVod) 60_000L else 30_000L
        kotlinx.coroutines.delay(timeout)
        android.util.Log.d("nexPlayer", "Buffering watchdog: reconnecting after ${timeout / 1000}s stall (live=${!isCatchup && !isVod})")
        player?.let { p ->
            val savedPos = if (isCatchup || isVod) p.currentPosition.takeIf { it > 0L } else null
            if (!isCatchup && !isVod) p.seekToDefaultPosition()
            p.prepare()
            if (savedPos != null) p.seekTo(savedPos)
            p.play()
        }
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
    LaunchedEffect(episodeId) { nextEpisodeDismissed = false; showNextEpisodePrompt = false }
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
                if (dur > 0 && (dur - pos) <= 30_000 && !showNextEpisodePrompt && !nextEpisodeDismissed && nextEpisodeAvailable != null) showNextEpisodePrompt = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val state = viewModel.trialManager.checkAccessState(viewModel.trialManager.getDeviceId())
        if (state == AppAccessState.TRIAL_EXPIRED) { player?.pause(); isTrialExpired = true }
    }

    // ── Sleep timer countdown ─────────────────────────────────────────────────
    LaunchedEffect(sleepTimerEndsAt) {
        if (sleepTimerEndsAt <= 0L) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            if (System.currentTimeMillis() >= sleepTimerEndsAt) {
                stopPlayback(); onBack(); break
            }
        }
    }

    // ── Playback speed apply ──────────────────────────────────────────────────
    LaunchedEffect(playbackSpeed, player) {
        player?.setPlaybackParameters(PlaybackParameters(playbackSpeed))
    }

    // ── Series "Still Watching?" after 3 consecutive episodes ─────────────────
    LaunchedEffect(episodeId, seriesId) {
        if (episodeId != null && seriesId != null) {
            episodesWatchedInRow++
            if (episodesWatchedInRow > 3) {
                player?.pause()
                showStillWatchingDialog = true
                episodesWatchedInRow = 0
            }
        }
    }

    // ── Stats overlay polling ─────────────────────────────────────────────────
    LaunchedEffect(showStats) {
        while (showStats) {
            val exo = player as? ExoPlayer
            exo?.videoFormat?.sampleMimeType?.substringAfterLast('/')?.uppercase()?.let { videoCodecName = it }
            exo?.audioFormat?.sampleMimeType?.substringAfterLast('/')?.uppercase()?.let { audioCodecName = it }
            bufferHealthMs = ((player?.bufferedPosition ?: 0L) - (player?.currentPosition ?: 0L)).coerceAtLeast(0L)
            delay(1_000L)
        }
    }

    // ── Cast connect/disconnect side-effects ──────────────────────────────────
    LaunchedEffect(isCasting) {
        if (isCasting) {
            castEverConnected = true
            castPositionMs = player?.currentPosition ?: 0L
            castIsPlaying = true
            player?.pause()
        } else if (castEverConnected) {
            player?.play()
        }
    }
    // Poll Chromecast position when casting
    LaunchedEffect(isCasting) {
        if (!isCasting) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            val remote = castManager.getApproximatePositionMs()
            if (remote > 0L) castPositionMs = remote
            else if (castIsPlaying) castPositionMs += 1000L
        }
    }

    // Effective playing state: uses cast state when casting, local player state otherwise
    val effectiveIsPlaying by remember { derivedStateOf { if (isCasting) castIsPlaying else isPlaying } }

    // ── D-pad & controls state ────────────────────────────────────────────────
    LaunchedEffect(showControls) {
        if (showControls) { dpadZone = DpadZone.CONTROLS }
    }
    LaunchedEffect(showControls, effectiveIsPlaying) {
        if (showControls && effectiveIsPlaying) { kotlinx.coroutines.delay(5000); showControls = false }
    }

    val hasScrubbing = (movieId != null) || episodeId != null
    val hasNext by remember { derivedStateOf { episodeId != null && nextEpisodeAvailable != null } }
    val hasPrev by remember { derivedStateOf { episodeId != null && previousEpisodeAvailable != null } }
    val isLiveTV = movieId == null && episodeId == null
    val hasChannelPrev = isLiveTV && onPreviousChannel != null
    val hasChannelNext = isLiveTV && onNextChannel != null

    // Centre control button list (back, rewind, play, forward, next, …)
    val centreButtons by remember { derivedStateOf {
        buildList {
            add("back")
            if (hasChannelPrev) add("ch_prev")
            if (episodeId != null) add("prev")
            if (hasScrubbing) add("rewind")
            add("playpause")
            if (hasScrubbing) add("forward")
            if (episodeId != null) add("next")
            if (hasChannelNext) add("ch_next")
            // CC toggle when AI subtitles enabled (all content types); VOD also gets subtitles sheet button
            if ((isLiveTV || movieId == "catchup") && whisperEnabled) add("cc")
            if (!(isLiveTV || movieId == "catchup")) {
                add("subtitles")
                if (whisperEnabled) add("cc")
            }
            // Subtitle position toggle — visible whenever subtitles are showing
            if (ccActive || subtitleCueLines.isNotEmpty()) add("sub_pos")
            // Subtitle delay — visible when subtitles are active
            if (ccActive || subtitleCueLines.isNotEmpty()) add("sub_delay")
            // Sleep timer — live TV and catchup only
            if (isLiveTV || isCatchup) add("sleep")
            // Playback speed — VOD and episodes (content with a scrub bar)
            if (hasScrubbing) add("speed")
            // Stats overlay — always
            add("stats")
        }
    }}
    LaunchedEffect(showControls) {
        if (showControls) centreIndex = centreButtons.indexOf("playpause").coerceAtLeast(0)
    }
    LaunchedEffect(centreButtons) {
        centreIndex = centreIndex.coerceIn(0, (centreButtons.size - 1).coerceAtLeast(0))
    }

    // Icon row: aspect ratio, cast (phone-only)
    val iconButtons by remember(showAspectRatioButton, isAndroidTV) {
        derivedStateOf {
            buildList {
                if (showAspectRatioButton) add("aspect")
                if (!isAndroidTV) add("cast")
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
                "subtitles" -> showMediaSheet = true
                "cast"      -> showCastSheet = true
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
                "back"      -> { stopPlayback(); onBack() }
                "ch_prev"   -> { onPreviousChannel?.invoke() }
                "prev"      -> { previousEpisodeAvailable?.let { onPlayNextEpisode?.invoke(it) } }
                "rewind"    -> if (isCasting) {
                    val np = (castPositionMs - 60_000L).coerceAtLeast(0L)
                    castPositionMs = np; castManager.seekTo(np)
                } else player?.seekBack()
                "playpause" -> if (isCasting) {
                    if (castIsPlaying) { castManager.pause(); castIsPlaying = false }
                    else { castManager.play(); castIsPlaying = true }
                } else {
                    if (player?.isPlaying == true) player?.pause() else player?.play()
                }
                "forward"   -> if (isCasting) {
                    val dur = player?.duration ?: Long.MAX_VALUE
                    val np = (castPositionMs + 60_000L).coerceAtMost(dur)
                    castPositionMs = np; castManager.seekTo(np)
                } else player?.seekForward()
                "next"      -> { showNextEpisodePrompt = false; currentNextEpisode?.let { onPlayNextEpisode?.invoke(it) } }
                "ch_next"   -> { onNextChannel?.invoke() }
                "subtitles" -> showMediaSheet = true
                "cc"        -> ccActive = !ccActive
                "sub_pos"   -> subtitleAtTop = !subtitleAtTop
                "sub_delay" -> showSubtitleDelayDialog = true
                "sleep"     -> showSleepTimerDialog = true
                "speed"     -> showSpeedDialog = true
                "stats"     -> showStats = !showStats
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
                            ErrorButton.RETRY   -> { val pos = errorPosition; hasError = false; autoRetryCount = 0; if (!isCatchup && !isVod) player?.seekToDefaultPosition(); player?.prepare(); if ((isCatchup || isVod) && pos > 0L) player?.seekTo(pos); player?.play() }
                        }; true
                    }
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
                    else -> false
                }
                else -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER ->
                    { activateFocusedButtonRef.value(); true }

                    android.view.KeyEvent.KEYCODE_DPAD_UP -> when (currentDpadZone) {
                        DpadZone.CONTROLS -> {
                            if (hasScrubbing) dpadZone = DpadZone.SLIDER
                            else if (currentIconButtons.isNotEmpty()) dpadZone = DpadZone.ICONS
                            true
                        }
                        DpadZone.SLIDER -> {
                            if (currentIconButtons.isNotEmpty()) dpadZone = DpadZone.ICONS
                            true
                        }
                        DpadZone.ICONS -> true // already at top
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
                            val seekPos = (sliderPosition * duration).toLong()
                            if (isCasting) { castPositionMs = seekPos; castManager.seekTo(seekPos) }
                            else player?.seekTo(seekPos)
                            true
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> when (currentDpadZone) {
                        DpadZone.ICONS    -> { if (currentIconIndex < iconMax) iconIndex++; true }
                        DpadZone.CONTROLS -> { if (currentCentreIndex < currentCentreButtons.size - 1) centreIndex++; true }
                        DpadZone.SLIDER   -> {
                            val duration = player?.duration ?: 0L
                            sliderPosition = (sliderPosition + 0.01f).coerceAtMost(1f)
                            val seekPos = (sliderPosition * duration).toLong()
                            if (isCasting) { castPositionMs = seekPos; castManager.seekTo(seekPos) }
                            else player?.seekTo(seekPos)
                            true
                        }
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                    { if (player?.isPlaying == true) player?.pause() else player?.play(); true }
                    else -> false
                }
            }
        } else false
    }

    // ── Shared button style helpers ───────────────────────────────────────────
    val controlText  = if (nsTheme.isDark) nsTheme.player.textPrimary else Color(0xFF1A1A1A)
    val controlBg    = if (nsTheme.isDark) Color.Black.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.60f)
    val focusBorder  = controlText
    val focusBgTint  = controlText.copy(alpha = 0.18f)
    val panelBgStart = if (nsTheme.isDark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.30f)
    val panelBgEnd   = if (nsTheme.isDark) Color.Black.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)

    // ── Root layout ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (player == null) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = controlText)

        val fp = forwardingPlayer
        if (fp != null) {
            if (isCasting) {
                // ── Cast active: show poster/title overlay ────────────────────
                val castDevice = (castState as? CastState.Active)?.device
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                        .clickable { showControls = !showControls },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Cast,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(56.dp)
                        )
                        if (castDevice != null) {
                            Text(
                                text = "Casting to ${castDevice.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        if (nowPlayingTitle != null) {
                            Text(
                                text = nowPlayingTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = fp; useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            isFocusable = true; isFocusableInTouchMode = true; requestFocus()
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setOnKeyListener { _, keyCode, event -> keyHandlerRef.value(keyCode, event) }
                            subtitleView?.visibility = android.view.View.GONE
                        }
                    },
                    update = { pv -> pv.resizeMode = resizeMode },
                    modifier = Modifier.fillMaxSize().clickable { showControls = !showControls }
                )
            }

            // ── Controls overlay (hidden in PiP mode) ─────────────────────────
            AnimatedVisibility(
                visible = !isInPipMode && (showControls || !effectiveIsPlaying),
                enter   = fadeIn(animationSpec = tween(300)),
                exit    = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // Subtle full-screen dark scrim (lighter than before — controls carry their own bg)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

                    // ── Close button (PIP mode only on mobile/tablet) ─────────
                    if (!isTv && isInPipMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                    stopPlayback()
                                    onBack()
                                }
                                .size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // ── Bottom control panel ──────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(colors = listOf(panelBgStart, panelBgEnd), startY = 0f))
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
                        if (nowPlayingSubtitle == null && nextProgramme != null) {
                            Row(
                                modifier          = Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = controlText.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text     = "Up Next",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = controlText.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text     = nextProgramme!!,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = controlText.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

                        // Stream quality badge
                        if (videoQualityLabel != null && !isCasting) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Hd,
                                            contentDescription = null,
                                            tint = controlText.copy(alpha = 0.75f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text  = videoQualityLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = controlText.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }

                        // ── ICONS ROW: aspect ratio + cast (phone-only, hidden on TV) ────
                        if (iconButtons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                                // Cast button (mobile only)
                                if (!isAndroidTV) {
                                    val castFocused = showControls && currentDpadZone == DpadZone.ICONS &&
                                            currentIconButtons.getOrNull(currentIconIndex) == "cast"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isCasting) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                else controlBg
                                            )
                                            .then(
                                                if (castFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(onClick = { showCastSheet = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Cast,
                                                contentDescription = "Cast",
                                                tint = if (isCasting) MaterialTheme.colorScheme.primary else controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                            }
                        }
                        } // end if (iconButtons.isNotEmpty())

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── SLIDER + timestamps ──────────────────────────────
                        val isLocalFile = channelUrl.startsWith("/") || channelUrl.startsWith("file://")
                        if (movieId != null || episodeId != null || catchupDuration > 0L || isLocalFile) {
                            LaunchedEffect(effectiveIsPlaying, isDragging, isCasting) {
                                while (!isDragging) {
                                    val duration = player?.duration ?: 0L
                                    if (duration > 0) {
                                        val pos = if (isCasting) castPositionMs else player?.currentPosition ?: 0L
                                        sliderPosition = pos.toFloat() / duration.toFloat()
                                    }
                                    kotlinx.coroutines.delay(500)
                                }
                            }
                            val sliderFocused = currentDpadZone == DpadZone.SLIDER && showControls
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value               = sliderPosition,
                                    onValueChange       = { isDragging = true; sliderPosition = it },
                                    onValueChangeFinished = {
                                        isDragging = false
                                        val seekPos = (sliderPosition * (player?.duration ?: 0L)).toLong()
                                        if (isCasting) { castPositionMs = seekPos; castManager.seekTo(seekPos) }
                                        else player?.seekTo(seekPos)
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
                                    text  = formatDuration(if (isCasting) castPositionMs else player?.currentPosition ?: 0L),
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

                        // ── CONTROLS ROW: back │ rewind │ play/pause │ forward │ next │ subtitles ─
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back button (always first)
                                val backCtrlFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                        currentCentreButtons.getOrNull(currentCentreIndex) == "back"
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(controlBg)
                                        .then(
                                            if (backCtrlFocused)
                                                Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                            else Modifier
                                        )
                                ) {
                                    IconButton(
                                        onClick  = { stopPlayback(); onBack() },
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = controlText,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                if (hasChannelPrev) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "ch_prev"
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
                                            onClick  = { onPreviousChannel?.invoke() },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Previous Channel",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

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
                                        onClick  = {
                                            if (isCasting) {
                                                if (castIsPlaying) { castManager.pause(); castIsPlaying = false }
                                                else { castManager.play(); castIsPlaying = true }
                                            } else {
                                                if (player?.isPlaying == true) player?.pause() else player?.play()
                                            }
                                        },
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (effectiveIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (effectiveIsPlaying) "Pause" else "Play",
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

                                if (episodeId != null) {
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
                                            enabled  = hasPrev,
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious,
                                                contentDescription = "Previous Episode",
                                                tint = if (hasPrev) controlText else controlText.copy(alpha = 0.35f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                if (episodeId != null) {
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
                                            onClick  = { if (hasNext) { showNextEpisodePrompt = false; onPlayNextEpisode?.invoke(nextEpisodeAvailable!!) } },
                                            enabled  = hasNext,
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "Next Episode",
                                                tint = if (hasNext) controlText else controlText.copy(alpha = 0.35f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                // Subtitles button — VOD only (live TV uses CC button below)
                                if (!isLiveTV && movieId != "catchup") {
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
                                if (hasChannelNext) {
                                    val focused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "ch_next"
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
                                            onClick  = { onNextChannel?.invoke() },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Next Channel",
                                                tint = controlText,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }


                                // CC button — all content types when AI subtitles enabled in settings
                                if (whisperEnabled) {
                                    val ccFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "cc"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (ccActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else controlBg)
                                            .then(
                                                if (ccFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { ccActive = !ccActive },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ClosedCaption,
                                                contentDescription = "Toggle AI Captions",
                                                tint = if (ccActive) MaterialTheme.colorScheme.primary else controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Subtitle position toggle — visible when any subtitles are showing
                                if (ccActive || subtitleCueLines.isNotEmpty()) {
                                    val subPosFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "sub_pos"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(controlBg)
                                            .then(
                                                if (subPosFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { subtitleAtTop = !subtitleAtTop },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (subtitleAtTop) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
                                                contentDescription = if (subtitleAtTop) "Move subtitles to bottom" else "Move subtitles to top",
                                                tint = controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Subtitle delay button — visible when subtitles are active
                                if (ccActive || subtitleCueLines.isNotEmpty()) {
                                    val subDelayFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "sub_delay"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (subtitleDelayMs > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else controlBg)
                                            .then(
                                                if (subDelayFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { showSubtitleDelayDialog = true },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Timer,
                                                contentDescription = "Subtitle Delay",
                                                tint = if (subtitleDelayMs > 0) MaterialTheme.colorScheme.primary else controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Sleep timer button — live TV and catchup
                                if (isLiveTV || isCatchup) {
                                    val sleepFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "sleep"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (sleepTimerEndsAt > 0L) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else controlBg)
                                            .then(
                                                if (sleepFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { showSleepTimerDialog = true },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Snooze,
                                                contentDescription = "Sleep Timer",
                                                tint = if (sleepTimerEndsAt > 0L) MaterialTheme.colorScheme.primary else controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Playback speed button — VOD and episodes
                                if (hasScrubbing) {
                                    val speedFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                            currentCentreButtons.getOrNull(currentCentreIndex) == "speed"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else controlBg)
                                            .then(
                                                if (speedFocused)
                                                    Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                                else Modifier
                                            )
                                    ) {
                                        IconButton(
                                            onClick  = { showSpeedDialog = true },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Speed,
                                                contentDescription = "Playback Speed",
                                                tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else controlText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                // Stats overlay toggle — always visible
                                val statsFocused = showControls && currentDpadZone == DpadZone.CONTROLS &&
                                        currentCentreButtons.getOrNull(currentCentreIndex) == "stats"
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (showStats) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else controlBg)
                                        .then(
                                            if (statsFocused)
                                                Modifier.border(2.dp, focusBorder, CircleShape).background(focusBgTint)
                                            else Modifier
                                        )
                                ) {
                                    IconButton(
                                        onClick  = { showStats = !showStats },
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BarChart,
                                            contentDescription = "Stats",
                                            tint = if (showStats) MaterialTheme.colorScheme.primary else controlText,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } // end Column (bottom panel)
                } // end Box (controls overlay)
            } // end AnimatedVisibility
        } // end if fp != null

        // ── Subtitle sidebar ──────────────────────────────────────────────────
        val subtitleDisplayLines = if (subtitleCueLines.isNotEmpty()) subtitleCueLines
            else if (ccActive && whisperText.isNotBlank()) listOf(whisperText)
            else emptyList()
        SubtitleSideBar(
            lines     = subtitleDisplayLines,
            isWhisper = subtitleCueLines.isEmpty() && ccActive,
            modifier  = if (subtitleAtTop)
                Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            else
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        )

        // ── Non-English audio prompt ──────────────────────────────────────────
        val audioLangName = remember(audioLangDetected) {
            audioLangDetected?.let { Locale(it).getDisplayLanguage(Locale.ENGLISH).ifBlank { it.uppercase() } } ?: ""
        }
        if (autoLangDetect && !isCatchup && movieId == null && episodeId == null &&
                audioLangDetected != null && !audioLangPromptDismissed && !ccActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "This programme appears to be in $audioLangName",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { ccActive = true; audioLangPromptDismissed = true }) {
                            Text("Add Subtitles")
                        }
                        OutlinedButton(onClick = { audioLangPromptDismissed = true }) {
                            Text("No Thanks")
                        }
                    }
                }
            }
        }

        // ── Cast device sheet ─────────────────────────────────────────────────
        if (showCastSheet) {
            CastDeviceSheet(
                castManager      = castManager,
                currentUrl       = channelUrl,
                title            = nowPlayingTitle,
                isLive           = movieId == null && episodeId == null && !isCatchup,
                currentPositionMs = if (isCasting) castPositionMs else player?.currentPosition ?: 0L,
                onDismiss        = { showCastSheet = false }
            )
        }

        // ── Media sheet ───────────────────────────────────────────────────────
        val currentPlayer = player
        if (showMediaSheet && currentPlayer != null) {
            val isMovieOrEpisode   = (movieId != null && movieId != "catchup") || episodeId != null
            MediaSheet(
                player           = currentPlayer,
                isMovieOrEpisode = isMovieOrEpisode,
                movieId          = movieId,
                episodeId        = episodeId,
                seriesId         = seriesId,
                channelUrl       = channelUrl,
                viewModel        = viewModel,
                scope            = scope,
                whisperEnabled   = whisperEnabled,
                whisperManager   = whisperManager,
                onDismiss        = { showMediaSheet = false }
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
                                Button(onClick = { val pos = errorPosition; hasError = false; autoRetryCount = 0; if (!isCatchup && !isVod) player?.seekToDefaultPosition(); player?.prepare(); if ((isCatchup || isVod) && pos > 0L) player?.seekTo(pos); player?.play() }) { Text("Retry") }
                            else
                                OutlinedButton(onClick = { val pos = errorPosition; hasError = false; autoRetryCount = 0; if (!isCatchup && !isVod) player?.seekToDefaultPosition(); player?.prepare(); if ((isCatchup || isVod) && pos > 0L) player?.seekTo(pos); player?.play() }) { Text("Retry") }
                        }
                        Text("← → to switch   OK to confirm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // ── Next episode prompt ───────────────────────────────────────────────
        if (showNextEpisodePrompt && nextEpisodeAvailable != null) {
            UpNextOverlay(
                episode    = nextEpisodeAvailable!!,
                seriesName = nowPlayingTitle,
                onPlayNow  = { showNextEpisodePrompt = false; onPlayNextEpisode?.invoke(nextEpisodeAvailable!!) },
                onDismiss  = { showNextEpisodePrompt = false; nextEpisodeDismissed = true }
            )
        }

        // ── Trial expired ─────────────────────────────────────────────────────
        if (isTrialExpired) TrialExpiredScreen(onLicenceActivated = { isTrialExpired = false; player?.play() })

        // ── Stats overlay (top-left, always on top) ───────────────────────────
        AnimatedVisibility(
            visible  = showStats,
            enter    = fadeIn(animationSpec = tween(300)),
            exit     = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable fun StatsRow(label: String, value: String) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Text(
                                text  = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.width(72.dp)
                            )
                            androidx.compose.material3.Text(
                                text  = value,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    StatsRow("Video", videoCodecName ?: "—")
                    StatsRow("Resolution", if (videoWidth > 0) "${videoWidth}×${videoHeight}" else "—")
                    StatsRow("Bitrate", if (videoBitrateKbps > 0) "${videoBitrateKbps} kbps" else "—")
                    StatsRow("Audio", audioCodecName ?: "—")
                    StatsRow("Buffer", if (bufferHealthMs > 0L) "${bufferHealthMs / 1000}s" else "—")
                }
            }
        }

        // ── Sleep timer dialog ────────────────────────────────────────────────
        if (showSleepTimerDialog) {
            Dialog(onDismissRequest = { showSleepTimerDialog = false }) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(
                            0L            to "Off",
                            30 * 60_000L  to "30 minutes",
                            60 * 60_000L  to "60 minutes",
                            90 * 60_000L  to "90 minutes"
                        ).forEach { (ms, label) ->
                            val isSelected = sleepTimerDurationMs == ms
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                    sleepTimerDurationMs = ms
                                    sleepTimerEndsAt     = if (ms > 0L) System.currentTimeMillis() + ms else 0L
                                    showSleepTimerDialog = false
                                },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    if (isSelected) Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Playback speed dialog ─────────────────────────────────────────────
        if (showSpeedDialog) {
            Dialog(onDismissRequest = { showSpeedDialog = false }) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Playback Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(0.5f to "0.5×", 0.75f to "0.75×", 1.0f to "1× (Normal)", 1.25f to "1.25×", 1.5f to "1.5×", 2.0f to "2×").forEach { (speed, label) ->
                            val isSelected = playbackSpeed == speed
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                    playbackSpeed = speed; showSpeedDialog = false
                                },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    if (isSelected) Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Subtitle delay dialog ─────────────────────────────────────────────
        if (showSubtitleDelayDialog) {
            Dialog(onDismissRequest = { showSubtitleDelayDialog = false }) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Subtitle Delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Delay subtitle display to fix sync. Increase if subtitles appear too early.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = { subtitleDelayMs = (subtitleDelayMs - 100).coerceAtLeast(0) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                text  = if (subtitleDelayMs == 0) "Off" else "+${subtitleDelayMs}ms",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.width(90.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = { subtitleDelayMs = (subtitleDelayMs + 100).coerceAtMost(5000) }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { subtitleDelayMs = 0 }) { Text("Reset") }
                            Button(onClick = { showSubtitleDelayDialog = false }) { Text("Done") }
                        }
                    }
                }
            }
        }

        // ── Still watching? (series: after 3 consecutive episodes) ────────────
        if (showStillWatchingDialog) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 360.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.LiveTv, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Text("Still watching?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("You've watched 3 episodes in a row.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showStillWatchingDialog = false; stopPlayback(); onBack() }) {
                                Text("Stop")
                            }
                            Button(onClick = { showStillWatchingDialog = false; player?.play() }) {
                                Text("Keep Watching")
                            }
                        }
                    }
                }
            }
        }

    } // end root Box
}

// ── SubtitleSideBar ───────────────────────────────────────────────────────────

@Composable
private fun SubtitleSideBar(
    lines: List<String>,
    isWhisper: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible  = lines.isNotEmpty(),
        enter    = fadeIn(tween(300)),
        exit     = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            lines.forEach { line ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text       = line,
                        color      = Color.White,
                        fontSize   = 22.sp,
                        fontStyle  = if (isWhisper) FontStyle.Italic else FontStyle.Normal,
                        textAlign  = TextAlign.Center,
                        lineHeight = 28.sp,
                    )
                }
            }
            if (isWhisper && lines.isNotEmpty()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(5.dp).background(Color.Red, CircleShape))
                    Text("AI", color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
                }
            }
        }
    }
}

// ── UpNextOverlay ─────────────────────────────────────────────────────────────

@Composable
private fun UpNextOverlay(
    episode: EpisodeEntity,
    seriesName: String?,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val autoPlaySeconds = 15
    var secondsLeft by remember { mutableStateOf(autoPlaySeconds) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            secondsLeft--
        }
        onPlayNow()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100L)
        try { playFocus.requestFocus() } catch (_: Exception) {}
    }

    val progress = secondsLeft.toFloat() / autoPlaySeconds.toFloat()
    val episodeLabel = "S${episode.seasonNum}E${episode.episodeNum} · ${episode.name}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            modifier  = Modifier.padding(32.dp).width(420.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text     = "UP NEXT",
                        style    = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Episode info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text       = episodeLabel,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (!seriesName.isNullOrEmpty()) {
                        Text(
                            text  = seriesName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Countdown bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress    = { progress },
                        modifier    = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color       = MaterialTheme.colorScheme.primary,
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text  = "Auto-playing in ${secondsLeft}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss", fontSize = 12.sp)
                    }
                    Button(
                        onClick  = onPlayNow,
                        modifier = Modifier.weight(1f).focusRequester(playFocus),
                        shape    = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play Now", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── TrackItem ─────────────────────────────────────────────────────────────────

@Composable
private fun WhisperModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected  -> accent.copy(alpha = 0.30f)
                    isFocused -> Color.White.copy(alpha = 0.15f)
                    else      -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(
                width = if (selected || isFocused) 1.5.dp else 1.dp,
                color = when {
                    selected  -> accent
                    isFocused -> Color.White.copy(alpha = 0.6f)
                    else      -> Color.White.copy(alpha = 0.20f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else Color.White.copy(alpha = if (isFocused) 1f else 0.75f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackItem(track: Track, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick      = onClick,
        modifier     = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) accent else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ),
        color        = when {
            isFocused        -> accent.copy(alpha = 0.45f)
            track.isSelected -> accent.copy(alpha = 0.25f)
            else             -> Color.Transparent
        },
        contentColor = Color.White
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = track.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (track.isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                    color      = if (track.isSelected || isFocused) accent else Color.White
                )
                if (track.language != track.label && track.language != "Off") {
                    Text(
                        text  = track.language,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            if (track.isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent)
            else if (isFocused) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accent)
        }
    }
}

// ── OnlineSearchState ─────────────────────────────────────────────────────────

private sealed interface OnlineSearchState {
    object Idle        : OnlineSearchState
    object Loading     : OnlineSearchState
    object Downloading : OnlineSearchState
    object NoResults   : OnlineSearchState
    data class Results(val results: List<SubtitleLanguage>) : OnlineSearchState
    data class Error(val message: String) : OnlineSearchState
}

// ── SubtitleResultRow ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleResultRow(subtitle: SubtitleLanguage, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick      = onClick,
        modifier     = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) accent else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ),
        color        = if (isFocused) accent.copy(alpha = 0.45f) else Color.Transparent,
        contentColor = Color.White
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "${subtitle.code.uppercase()} — ${subtitle.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                if (!subtitle.releaseName.isNullOrBlank()) {
                    Text(
                        text     = subtitle.releaseName!!,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.Download, contentDescription = null, tint = accent)
        }
    }
}

// ── MediaSheet ────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun MediaSheet(
    player: Player, isMovieOrEpisode: Boolean, movieId: String?, episodeId: String?, seriesId: String?,
    channelUrl: String, viewModel: PlayerViewModel,
    scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit,
    whisperEnabled: Boolean = false, whisperManager: WhisperSubtitleManager? = null
) {
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try { panelFocus.requestFocus() } catch (_: Exception) {}
    }
    var searchState by remember { mutableStateOf<OnlineSearchState>(OnlineSearchState.Idle) }
    var searchJob   by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Re-request panel focus when search state changes — prevents D-pad focus trap on TV
    LaunchedEffect(searchState) {
        if (searchState !is OnlineSearchState.Idle) {
            delay(100)
            try { panelFocus.requestFocus() } catch (_: Exception) {}
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onDismiss)
            )
        val scrollState = rememberScrollState()
        Surface(
            modifier     = Modifier
                .fillMaxHeight()
                .width(360.dp),
            color        = Color(0x80141414),
            contentColor = Color.White
        ) {
        val tracks                   = player.currentTracks
        val trackSelectionParameters = player.trackSelectionParameters
        val textDisabled             = trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        val audioTracks = remember(tracks) {
            buildList {
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == C.TRACK_TYPE_AUDIO) for (j in 0 until group.length) {
                        val format = group.getTrackFormat(j)
                        add(Track(TrackType.AUDIO, i, j, format.language ?: "Unknown", format.label ?: format.language ?: "Audio ${j + 1}", group.isTrackSelected(j)))
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

        val availableLanguages = remember { listOf("en" to "English", "fr" to "French", "de" to "German", "es" to "Spanish", "it" to "Italian", "ar" to "Arabic", "nl" to "Dutch", "pt" to "Portuguese", "pl" to "Polish", "sv" to "Swedish") }
        val savedLanguage     by viewModel.subtitlePreferences.preferredLanguage.collectAsState(initial = "en")
        var selectedLanguage  by remember(savedLanguage) { mutableStateOf(savedLanguage) }
        var searchExpanded    by remember { mutableStateOf(false) }
        var contentOriginalLanguage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(movieId, episodeId, seriesId) {
            contentOriginalLanguage = when {
                movieId != null && movieId != "catchup" -> viewModel.getMovieById(movieId)?.originalLanguage
                episodeId != null -> seriesId?.let { viewModel.getSeriesById(it)?.originalLanguage }
                else -> null
            }
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            Box(modifier = Modifier.size(1.dp).focusRequester(panelFocus).focusable())
            Text(
                text       = "Audio & Subtitles",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── AUDIO ──────────────────────────────────────────────────────────
            Text(
                text     = "AUDIO",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (audioTracks.isEmpty()) {
                Text(
                    text     = "No audio tracks available",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                audioTracks.forEach { track ->
                    TrackItem(track = track, onClick = {
                        player.trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(tracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                            .build()
                        onDismiss()
                    })
                }
            }

            // ── SUBTITLES ─────────────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text     = "SUBTITLES",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            TrackItem(
                track   = Track(TrackType.SUBTITLE, -1, -1, "Off", "Off", textDisabled || subtitleTracks.none { it.isSelected }),
                onClick = {
                    player.trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    onDismiss()
                }
            )
            subtitleTracks.forEach { track ->
                TrackItem(track = track, onClick = {
                    player.trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(tracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                        .build()
                    onDismiss()
                })
            }

            // ── SEARCH ONLINE (VOD / series only) ────────────────────────────
            if (isMovieOrEpisode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchState = OnlineSearchState.Idle
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Search online subtitles", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Icon(
                        imageVector        = if (searchExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                if (searchExpanded) {
                    Text(
                        text     = "Language",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier              = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableLanguages.forEach { (code, name) ->
                            FilterChip(
                                selected = code == selectedLanguage,
                                onClick  = { selectedLanguage = code },
                                label    = { Text(name) }
                            )
                        }
                    }
                    Button(
                        onClick  = {
                            if (searchState is OnlineSearchState.Loading) {
                                // Cancel in-flight search
                                searchJob?.cancel(); searchJob = null
                                searchState = OnlineSearchState.Idle
                                return@Button
                            }
                            searchJob = scope.launch {
                                viewModel.subtitlePreferences.savePreferredLanguage(selectedLanguage)
                                searchState = OnlineSearchState.Loading
                                try {
                                    val results: List<SubtitleLanguage> = kotlinx.coroutines.withTimeout(15_000L) {
                                        when {
                                            movieId != null   -> {
                                                val movie = viewModel.getMovieById(movieId)
                                                viewModel.subtitleManager.searchMovieSubtitles(movie?.name ?: "", movie?.releaseDate?.take(4), selectedLanguage)
                                            }
                                            episodeId != null -> {
                                                val episode = viewModel.getEpisodeById(episodeId)
                                                val series  = seriesId?.let { viewModel.getSeriesById(it) }
                                                if (episode != null && series != null)
                                                    viewModel.subtitleManager.searchEpisodeSubtitles(series.name, episode.seasonNum, episode.episodeNum, selectedLanguage)
                                                else emptyList()
                                            }
                                            else -> emptyList()
                                        }
                                    }
                                    searchState = if (results.isEmpty()) OnlineSearchState.NoResults else OnlineSearchState.Results(results)
                                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                    searchState = OnlineSearchState.Error("Search timed out")
                                } catch (e: Exception) {
                                    if (e !is kotlinx.coroutines.CancellationException) {
                                        searchState = OnlineSearchState.Error(e.message ?: "Search failed")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        enabled  = searchState !is OnlineSearchState.Downloading
                    ) { Text(if (searchState is OnlineSearchState.Loading) "Cancel" else "Search") }

                    when (val state = searchState) {
                        is OnlineSearchState.Idle       -> {}
                        is OnlineSearchState.Loading    -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        is OnlineSearchState.Downloading -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading…")
                        }
                        is OnlineSearchState.NoResults  -> Text("No subtitles found", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(16.dp))
                        is OnlineSearchState.Error      -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        is OnlineSearchState.Results    -> state.results.forEach { sub ->
                            SubtitleResultRow(subtitle = sub, onClick = {
                                searchState = OnlineSearchState.Downloading
                                scope.launch {
                                    val cacheKey = movieId ?: episodeId ?: "unknown"
                                    when (val result = viewModel.subtitleManager.downloadSubtitle(sub, cacheKey)) {
                                        is SubtitleResult.Success -> {
                                            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(result.file))
                                                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                                                .setLanguage(sub.code)
                                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .build()
                                            val currentPosition = player.currentPosition
                                            val newMediaItem    = MediaItem.Builder().setUri(channelUrl).setSubtitleConfigurations(listOf(subtitleConfig)).build()
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                                            player.setMediaItem(newMediaItem, currentPosition)
                                            player.prepare()
                                            player.play()
                                            onDismiss()
                                        }
                                        is SubtitleResult.Error -> searchState = OnlineSearchState.Error(result.message)
                                    }
                                }
                            })
                        }
                    }
                }
            }

            // ── AI SUBTITLES (WHISPER) ─────────────────────────────────────────
            if (whisperEnabled && whisperManager != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text     = "AI SUBTITLES",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                val whisperIsLoadingSheet by whisperManager.isLoading.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (whisperIsLoadingSheet) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Transcribing…", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.70f))
                    } else {
                        Text("Server-based · live and on-demand", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                    }
                }

                val translateCtx = LocalContext.current
                val translateToEn by whisperManager.translateToEnglish.collectAsState()
                Text(
                    text     = "MODE",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WhisperModeChip(
                        label    = "Transcribe",
                        selected = !translateToEn,
                        onClick  = {
                            whisperManager.setTranslateToEnglish(false)
                            scope.launch { translateCtx.saveWhisperTranslateTo(false) }
                        }
                    )
                    WhisperModeChip(
                        label    = "Translate → EN",
                        selected = translateToEn,
                        onClick  = {
                            whisperManager.setTranslateToEnglish(true)
                            scope.launch { translateCtx.saveWhisperTranslateTo(true) }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        } // end Surface
        } // end Row
    } // end Dialog
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

private fun buildWhisperPrompt(
    movieId: String?,
    episodeId: String?,
    nowPlayingTitle: String?,
    nowPlayingSubtitle: String?,
    nowPlayingDescription: String?,
    epgProgramme: String?,
    epgDescription: String?,
): String {
    val raw = when {
        episodeId != null -> {
            val title = nowPlayingSubtitle ?: nowPlayingTitle ?: ""
            val desc  = nowPlayingDescription ?: ""
            "TV episode. $title. $desc"
        }
        movieId != null && movieId != "catchup" -> {
            val title    = nowPlayingTitle ?: ""
            val overview = nowPlayingDescription ?: ""
            "Movie. $title. $overview"
        }
        movieId == "catchup" -> {
            val title = nowPlayingTitle ?: ""
            val desc  = nowPlayingDescription ?: ""
            if (title.isBlank()) "Live TV broadcast. Clear English speech."
            else "Live TV broadcast. Clear English speech. Programme: $title. $desc"
        }
        else -> {
            val programme = epgProgramme ?: ""
            val desc      = epgDescription ?: ""
            if (programme.isBlank()) "Video content. Clear English speech."
            else "Live TV broadcast. Clear English speech. Programme: $programme. $desc"
        }
    }
    return raw.trim().take(896)
}