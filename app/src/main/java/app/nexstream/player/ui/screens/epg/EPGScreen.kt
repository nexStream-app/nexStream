package app.nexstream.player.ui.screens.epg

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.getEpgMiniPlayerFlow
import app.nexstream.player.ui.theme.getEpgTimeOffsetFlow
import app.nexstream.player.ui.theme.getExtPlayerLiveTvFlow
import app.nexstream.player.ui.screens.player.ExternalPlayerManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ChannelGroupDao
import app.nexstream.player.data.local.dao.TmdbPosterDao
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.TmdbPosterEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import app.nexstream.player.ui.screens.main.MainScreenViewModel
import app.nexstream.player.ui.screens.player.PlayerScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.screens.epg.ReminderViewModel
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import androidx.compose.ui.res.stringResource
import app.nexstream.player.R
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Composable
fun EPGScreen(
    selectedCategory: String?,
    onBack: () -> Unit = {},
    isContentFocused: Boolean = true,
    onPlayerVisibilityChanged: ((visible: Boolean) -> Unit)? = null,
    onReady: () -> Unit = {},
    onGridFocusRequesterReady: ((requestFocus: () -> Unit) -> Unit)? = null,
    refocusTick: Int = 0,
    isParentPlayerVisible: Boolean = false,
    mainViewModel: MainScreenViewModel = hiltViewModel(),
    viewModel: EPGViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel(),
    reminderViewModel: ReminderViewModel = hiltViewModel(),
    watchlistIds: Set<String> = emptySet(),
    onToggleChannelWatchlist: ((channelId: String, channelName: String, logoUrl: String?, streamUrl: String?) -> Unit)? = null,
    onDialogOpen: ((Boolean) -> Unit)? = null,
    pendingChannelName: String? = null,
    onPendingChannelConsumed: () -> Unit = {},
    onFocusUp: () -> Unit = {},
    panelExpanded: Boolean = true,
) {
    val playlists    by mainViewModel.playlists.collectAsState()
    val allChannels  by mainViewModel.allChannels.collectAsState()
    val activeProfile by viewModel.profileManager.activeProfile.collectAsState()

    when {
        playlists.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.epg_no_playlists_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.epg_no_playlists_message), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        allChannels.isEmpty() || activeProfile == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        else -> {
            // Resolve group channel IDs when category is a group
            var groupChannelIds by remember { mutableStateOf<Set<String>?>(null) }
            LaunchedEffect(selectedCategory) {
                if (selectedCategory?.startsWith("__grp_") == true) {
                    val groupId = selectedCategory.removePrefix("__grp_")
                    groupChannelIds = viewModel.getGroupChannelIds(groupId).toSet()
                } else {
                    groupChannelIds = null
                }
            }
            EPGContent(
                selectedCategory = selectedCategory,
                groupChannelIds = groupChannelIds,
                onBack = onBack,
                isContentFocused = isContentFocused,
                onPlayerVisibilityChanged = onPlayerVisibilityChanged,
                onReady = onReady,
                onGridFocusRequesterReady = onGridFocusRequesterReady,
                refocusTick = refocusTick,
                isParentPlayerVisible = isParentPlayerVisible,
                mainViewModel = mainViewModel,
                viewModel = viewModel,
                watchlistViewModel = watchlistViewModel,
                reminderViewModel = reminderViewModel,
                watchlistIds = watchlistIds,
                onDialogOpen = onDialogOpen,
                onToggleChannelWatchlist = onToggleChannelWatchlist,
                pendingChannelName = pendingChannelName,
                onPendingChannelConsumed = onPendingChannelConsumed,
                onFocusUp = onFocusUp,
                panelExpanded = panelExpanded,
            )
        }
    }
}

@Composable
private fun EPGContent(
    selectedCategory: String?,
    groupChannelIds: Set<String>? = null,
    onBack: () -> Unit,
    isContentFocused: Boolean,
    watchlistIds: Set<String> = emptySet(),
    onToggleChannelWatchlist: ((channelId: String, channelName: String, logoUrl: String?, streamUrl: String?) -> Unit)? = null,
    onDialogOpen: ((Boolean) -> Unit)? = null,
    onPlayerVisibilityChanged: ((visible: Boolean) -> Unit)?,
    onReady: () -> Unit,
    onGridFocusRequesterReady: ((requestFocus: () -> Unit) -> Unit)?,
    refocusTick: Int = 0,
    isParentPlayerVisible: Boolean = false,
    mainViewModel: MainScreenViewModel,
    viewModel: EPGViewModel,
    watchlistViewModel: WatchlistViewModel,
    reminderViewModel: ReminderViewModel,
    pendingChannelName: String? = null,
    onPendingChannelConsumed: () -> Unit = {},
    onFocusUp: () -> Unit = {},
    panelExpanded: Boolean = true,
) {
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()

    var showPlayer             by remember { mutableStateOf(false) }
    var showMiniPlayer         by remember { mutableStateOf(false) }
    var showMultiScreen        by remember { mutableStateOf(false) }
    var playerChannel          by remember { mutableStateOf<ChannelEntity?>(null) }
    var playerCatchupDuration  by remember { mutableStateOf(0L) }
    var playerCatchupProgramme by remember { mutableStateOf<app.nexstream.player.data.local.entity.ProgramEntity?>(null) }

    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedProgram by remember { mutableStateOf<ProgramEntity?>(null) }

    var dialogChannel       by remember { mutableStateOf<ChannelEntity?>(null) }
    var dialogProgram       by remember { mutableStateOf<ProgramEntity?>(null) }
    var dialogIsPlaceholder by remember { mutableStateOf(false) }

    // Recording confirmation
    var pendingRecordChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var pendingRecordProgram by remember { mutableStateOf<ProgramEntity?>(null) }
    var pendingRecordUrl     by remember { mutableStateOf<String?>(null) }
    var showRecordConfirm    by remember { mutableStateOf(false) }

    val programsMap        by viewModel.programsMap.collectAsState()
    val isLoadingPrograms  by viewModel.isLoadingPrograms.collectAsState()
    val allChannels        by mainViewModel.allChannels.collectAsState()
    val reminderIds        by reminderViewModel.reminderIds.collectAsState()
    val context = LocalContext.current
    val isTV = context.packageManager.hasSystemFeature("android.software.leanback")

    val epgMiniPlayerEnabled by context.getEpgMiniPlayerFlow().collectAsState(initial = true)
    val epgTimeOffsetHours   by context.getEpgTimeOffsetFlow().collectAsState(initial = 0)
    val extPlayerLiveTv by context.getExtPlayerLiveTvFlow().collectAsState(initial = "nexstream")

    val miniExoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { miniExoPlayer.release() }
    }
    LaunchedEffect(playerChannel?.streamUrl, showMiniPlayer, showPlayer, epgMiniPlayerEnabled) {
        if (showMiniPlayer && !showPlayer && epgMiniPlayerEnabled) {
            val url = playerChannel?.streamUrl
            if (url != null) {
                miniExoPlayer.stop()
                miniExoPlayer.setMediaItem(MediaItem.fromUri(url))
                miniExoPlayer.prepare()
                miniExoPlayer.play()
            }
        } else {
            miniExoPlayer.stop()
        }
    }

    val activeRecordingUrls by app.nexstream.player.recording.NexStreamRecordingManager
        .observeActiveUrls(context).collectAsState(initial = emptySet())

    val playlists           by mainViewModel.playlists.collectAsState()
    val activeProfile by viewModel.profileManager.activeProfile.collectAsState()
    val blockedTvCategories by viewModel.profileManager.blockedTvCategories.collectAsState()

    val isContentFocusedRef = remember { mutableStateOf(isContentFocused) }
    LaunchedEffect(isContentFocused) { isContentFocusedRef.value = isContentFocused }

    val showPlayerRef = remember { mutableStateOf(false) }
    LaunchedEffect(showPlayer) { showPlayerRef.value = showPlayer }

    val showMiniPlayerRef = remember { mutableStateOf(false) }
    LaunchedEffect(showMiniPlayer) { showMiniPlayerRef.value = showMiniPlayer }

    val playerChannelRef = remember { mutableStateOf<ChannelEntity?>(null) }
    LaunchedEffect(playerChannel) { playerChannelRef.value = playerChannel }

    val channels = remember(allChannels, selectedCategory, groupChannelIds, blockedTvCategories, watchlistIds) {
        val trimmedBlocked = blockedTvCategories.map { it.trim() }.toSet()
        val filtered = if (trimmedBlocked.isEmpty()) allChannels
        else allChannels.filter { ch -> ch.groupTitle == null || ch.groupTitle.trim() !in trimmedBlocked }
        when {
            selectedCategory == null             -> filtered
            selectedCategory == "__favourites__" -> {
                android.util.Log.d("EPGFav", "watchlistIds=${watchlistIds.take(5)}, total=${watchlistIds.size}")
                android.util.Log.d("EPGFav", "sample ch.ids=${filtered.take(5).map { it.id }}")
                filtered.filter { ch -> ch.id in watchlistIds }
            }
            selectedCategory.startsWith("__grp_") -> {
                val ids = groupChannelIds ?: emptySet()
                filtered.filter { ch -> ch.id in ids }
            }
            else -> filtered.filter { ch -> ch.groupTitle?.trim() == selectedCategory.trim() }
        }
    }

    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val epgColors = nsTheme.epg
    val typography = nsTheme.typography

    // Empty favourites state — shown as overlay, no early return (illegal in Compose)
    val showEmptyFavourites = selectedCategory == "__favourites__" && channels.isEmpty()

    // Incrementally wrap ProgramEntity→EPGProgram — only rewrap channels that are new or changed.
    // Avoids rebuilding the entire map (and all wrapper objects) on every Room emission.
    val epgProgramsMapState = remember { mutableStateOf<Map<String, List<EPGProgram>>>(emptyMap()) }
    LaunchedEffect(System.identityHashCode(programsMap)) {
        val prev = epgProgramsMapState.value
        epgProgramsMapState.value = withContext(Dispatchers.Default) {
            val result = prev.toMutableMap()
            // Remove channels evicted from the ViewModel map
            result.keys.toList().forEach { k -> if (k !in programsMap) result.remove(k) }
            // Only wrap channels that are new or whose program count changed
            programsMap.forEach { (key, list) ->
                if (result[key]?.size != list.size) {
                    result[key] = list.map { p ->
                        EPGProgram(
                            entity = if (p.title.isBlank()) p.copy(title = "No Information Provided") else p,
                            isPlaceholder = p.id.startsWith("placeholder-") || p.title.isBlank()
                        )
                    }
                }
            }
            result.toMap()
        }
    }
    val epgProgramsMap = epgProgramsMapState.value

    var gridView by remember { mutableStateOf<EPGGridView?>(null) }

    LaunchedEffect(refocusTick) {
        if (refocusTick > 0) {
            kotlinx.coroutines.delay(150)
            gridView?.requestGridFocus()
        }
    }

    LaunchedEffect(selectedCategory) {
        viewModel.resetLoadedChannels()
        gridView?.scrollToTop()
        gridView?.jumpToNow()
        gridView?.invalidateFilledCache()
        focusedChannel = null
        focusedProgram = null
        if (!isTV) {
            showMiniPlayer = false
            miniExoPlayer.stop()
            dialogChannel = null
            dialogProgram = null
        }
        // Seed the strip with the first channel in the new category, then explicitly
        // trigger programme loading so the description appears without waiting for the grid.
        // For channel groups, groupChannelIds resolves asynchronously — skip seeding here
        // and let the LaunchedEffect(groupChannelIds) below handle it once IDs are ready.
        if (selectedCategory?.startsWith("__grp_") == true) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val first = channels.firstOrNull()
        if (first != null) {
            focusedChannel = first
            // Programs are keyed by epgChannelId in the database (mirrors grid behaviour)
            val epgId = first.epgChannelId?.takeIf { it.isNotEmpty() } ?: first.id
            viewModel.loadProgramsForVisibleChannels(listOf(epgId))
        }
    }

    // Channel groups: groupChannelIds resolves asynchronously after selectedCategory changes,
    // so seed the first channel here once the IDs are available.
    LaunchedEffect(groupChannelIds) {
        groupChannelIds ?: return@LaunchedEffect
        if (selectedCategory?.startsWith("__grp_") != true) return@LaunchedEffect
        if (focusedChannel != null) return@LaunchedEffect
        kotlinx.coroutines.delay(50)
        val first = channels.firstOrNull()
        if (first != null) {
            focusedChannel = first
            val epgId = first.epgChannelId?.takeIf { it.isNotEmpty() } ?: first.id
            viewModel.loadProgramsForVisibleChannels(listOf(epgId))
        }
    }

    LaunchedEffect(pendingChannelName, gridView) {
        val name = pendingChannelName ?: return@LaunchedEffect
        val grid = gridView ?: return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        grid.scrollToChannelByName(name)
        // Update the info panel to show the scrolled-to channel's name and programme.
        // scrollToChannelByName only moves the grid; it doesn't fire onFocusedProgramChanged.
        val ch = channels.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (ch != null) {
            focusedChannel = ch
            val now = System.currentTimeMillis()
            focusedProgram = (programsMap[ch.epgChannelId?.takeIf { it.isNotEmpty() }]
                ?: programsMap[ch.id])
                ?.firstOrNull { now in it.startTime..it.endTime }
        }
        grid.requestGridFocus()
        onPendingChannelConsumed()
    }

    LaunchedEffect(playerChannel) {
        val ch = playerChannel ?: return@LaunchedEffect
        gridView?.syncFocusedChannelByUrl(ch.streamUrl)
    }

    // Notify strip of initial focused channel+programme once EPG data first arrives
    var hasNotifiedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(epgProgramsMap.keys.size, gridView) {
        if (!hasNotifiedInitialFocus && epgProgramsMap.isNotEmpty() && channels.isNotEmpty() && gridView != null) {
            hasNotifiedInitialFocus = true
            kotlinx.coroutines.delay(300)
            gridView?.notifyInitialFocus()
        }
    }

    // When focusedChannel is seeded but programmes haven't loaded yet, fill focusedProgram.
    // Keyed on .size so it re-fires each time new channel batches arrive.
    // Programs are keyed by epgChannelId in the DB, not channel entity id.
    LaunchedEffect(focusedChannel?.id, programsMap.size) {
        val ch = focusedChannel ?: return@LaunchedEffect
        if (focusedProgram == null) {
            val now = System.currentTimeMillis()
            val progs = programsMap[ch.epgChannelId?.takeIf { it.isNotEmpty() }]
                ?: programsMap[ch.id]
            focusedProgram = progs?.firstOrNull { now in it.startTime..it.endTime }
        }
    }

    // Periodic refresh: update strip when clock crosses into the next programme
    LaunchedEffect(focusedChannel?.id) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            val ch = focusedChannel ?: continue
            val fp = focusedProgram ?: continue
            val now2 = System.currentTimeMillis()
            if (now2 > fp.endTime) {
                val progs = programsMap[ch.epgChannelId?.takeIf { it.isNotEmpty() }]
                    ?: programsMap[ch.id]
                focusedProgram = progs?.firstOrNull { now2 in it.startTime..it.endTime }
            }
        }
    }

    // Stop mini player and dismiss action bar when focus leaves content zone
    LaunchedEffect(isContentFocused) {
        if (!isContentFocused && !showPlayer) {
            showMiniPlayer = false
            miniExoPlayer.stop()
            dialogChannel = null
            dialogProgram = null
        }
    }

    // On mobile: stop mini player when panel collapses back to rail
    LaunchedEffect(panelExpanded) {
        if (!isTV && !panelExpanded && !showPlayer) {
            showMiniPlayer = false
            miniExoPlayer.stop()
            dialogChannel = null
            dialogProgram = null
        }
    }

    // When dialog opens for a live programme, start mini player on that channel in the strip
    LaunchedEffect(dialogChannel, dialogProgram) {
        val dc = dialogChannel ?: return@LaunchedEffect
        val dp = dialogProgram ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now in dp.startTime..dp.endTime && epgMiniPlayerEnabled) {
            playerChannel  = dc
            showMiniPlayer = true
        }
    }

    BackHandler(enabled = isContentFocused && !showPlayer && !isParentPlayerVisible) {
        showMiniPlayer = false
        miniExoPlayer.stop()
        gridView?.jumpToNow()
        onBack()
    }

    Column(Modifier.fillMaxSize()) {
        // ── Mini player / info strip ──────────────────────────────────────────
        if (epgMiniPlayerEnabled) {
            EpgInfoPlayerStrip(
                focusedChannel  = focusedChannel,
                focusedProgram  = focusedProgram,
                playingChannel  = if (showMiniPlayer) playerChannel else null,
                miniExoPlayer   = miniExoPlayer,
                isTV            = isTV,
                onFullScreen    = { showPlayer = true },
                onCloseMini     = { showMiniPlayer = false; miniExoPlayer.stop() },
            )
        }

        Box(Modifier.weight(1f)) {
        // Programs loading overlay — shown while first batch of EPG data arrives
        if (isLoadingPrograms) {
            Box(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.epg_loading_guide),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sTheme.categoryText.copy(alpha = 0.7f))
                }
            }
        }

        // Empty favourites overlay
        if (showEmptyFavourites) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.epg_no_favourites_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Text(stringResource(R.string.epg_no_favourites_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f))
                }
            }
        }

        // Show header for special categories (Classic mode only — Modern uses ModernCategoryStrip)
        if (selectedCategory == "__favourites__") {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(sTheme.panelBackground.copy(alpha = 0.95f))
                    .align(Alignment.TopStart)
                    .zIndex(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Favourites",
                    style = MaterialTheme.typography.titleMedium,
                    color = sTheme.categoryText,
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        AndroidView(
            factory = { ctx ->
                EPGGridView(ctx).also { view ->
                    gridView = view
                    onGridFocusRequesterReady?.invoke { view.requestGridFocus() }
                    // Do not auto-refocus — let user navigate deliberately
                    // Focus is only restored via refocusTick (after player close)
                    // or onRequestContentFocus (D-pad right from sidebar)
                    view.setOnFocusChangeListener { _, _ -> }
                    view.callbacks = object : EPGGridCallbacks {
                        override fun onProgramSelected(channel: ChannelEntity, program: ProgramEntity, isPlaceholder: Boolean) {
                            val isLive = System.currentTimeMillis() in program.startTime..program.endTime
                            val sameChannelPlaying = showMiniPlayerRef.value && playerChannelRef.value?.streamUrl == channel.streamUrl
                            if (sameChannelPlaying) {
                                // Second press on the same live channel — show action bar
                                dialogChannel       = channel
                                dialogProgram       = program
                                dialogIsPlaceholder = isPlaceholder
                            } else if (isLive) {
                                // First press on a live programme — start mini player only
                                playerChannel  = channel
                                showMiniPlayer = true
                            } else {
                                // Non-live programme — open action bar directly
                                dialogChannel       = channel
                                dialogProgram       = program
                                dialogIsPlaceholder = isPlaceholder
                            }
                        }
                        override fun onChannelClicked(channel: ChannelEntity) {
                            // Channel logo tap → launch full player
                            if (extPlayerLiveTv != "nexstream" && extPlayerLiveTv.isNotEmpty() &&
                                ExternalPlayerManager.launch(context, extPlayerLiveTv, channel.streamUrl, channel.name)) {
                                scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(channel) }
                            } else {
                                playerChannel = channel
                                showPlayer = true
                                showMiniPlayer = false
                                onPlayerVisibilityChanged?.invoke(true)
                                scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(channel) }
                            }
                        }
                        override fun onFocusedProgramChanged(channel: ChannelEntity, program: ProgramEntity?) {
                            focusedChannel = channel
                            focusedProgram = program
                        }
                        override fun onBack() { onBack() }
                        override fun onReady() { onReady() }
                        override fun onFocusUp() { onFocusUp() }
                        override fun onVisibleChannelIdsChanged(ids: List<String>) {
                            viewModel.loadProgramsForVisibleChannels(ids)
                        }
                    }
                }
            },
            update = { view ->
                view.nexTheme              = epgColors
                view.textScale             = typography.scale
                view.textBold              = typography.weight != "normal"
                view.channels              = channels
                view.programsMap           = epgProgramsMap
                view.epgOffsetMs           = epgTimeOffsetHours * 3_600_000L
                view.reminderIds           = reminderIds
                view.playerVisible         = showPlayer
                view.recordingChannelUrls  = activeRecordingUrls
                view.isTV                  = isTV
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isTV) {
            FloatingActionButton(
                onClick        = { gridView?.jumpToNow() },
                modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = "Jump to now", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        app.nexstream.player.ui.components.NexStreamBanner(
            message   = bannerMessage,
            onDismiss = { bannerMessage = null }
        )

        // ── Programme action bar (replaces full-screen dialog) ────────────────
        val _dp = dialogProgram
        val _dc = dialogChannel
        if (_dp != null && _dc != null) {
            // Mobile: transparent full-screen tap catcher to dismiss action bar on tap-outside
            if (!isTV) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9f)
                        .clickable { dialogProgram = null; dialogChannel = null }
                )
            }
            ProgramActionBar(
                program    = _dp,
                channel    = _dc,
                isBookmarked   = _dc.id in watchlistIds,
                isPlaceholder  = dialogIsPlaceholder,
                isRecording    = _dc.streamUrl in activeRecordingUrls,
                hasReminder    = reminderViewModel.reminderId(_dc.id, _dp.startTime) in reminderIds,
                onDismiss      = { dialogProgram = null; dialogChannel = null },
                onToggleWatchlist = { onToggleChannelWatchlist?.invoke(_dc.id, _dc.name, _dc.logoUrl, _dc.streamUrl) },
                onWatch = {
                    val now2   = System.currentTimeMillis()
                    val isPast = _dp.startTime < now2 && _dp.endTime < now2
                    if (isPast && _dc.tvArchive != 0) {
                        scope.launch {
                            val url = viewModel.buildTimeshiftUrl(_dc, _dp)
                            if (url != null) {
                                playerChannel          = _dc.copy(streamUrl = url)
                                playerCatchupDuration  = _dp.endTime - _dp.startTime
                                playerCatchupProgramme = _dp
                                showPlayer             = true
                                showMiniPlayer         = false
                                onPlayerVisibilityChanged?.invoke(true)
                            } else {
                                bannerMessage = "Catch up unavailable for this programme"
                            }
                        }
                    } else {
                        if (extPlayerLiveTv != "nexstream" && extPlayerLiveTv.isNotEmpty() &&
                            ExternalPlayerManager.launch(context, extPlayerLiveTv, _dc.streamUrl, _dc.name)) {
                            scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(_dc) }
                        } else {
                            playerChannel  = _dc
                            showPlayer     = true
                            showMiniPlayer = false
                            onPlayerVisibilityChanged?.invoke(true)
                            scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(_dc) }
                        }
                    }
                    dialogProgram = null; dialogChannel = null
                },
                onRecord = if (_dc.streamUrl !in activeRecordingUrls && !dialogIsPlaceholder) ({
                    pendingRecordChannel = _dc
                    pendingRecordProgram = _dp
                    pendingRecordUrl = null
                    showRecordConfirm = true
                    dialogProgram = null; dialogChannel = null
                }) else null,
                onStopRecording = if (_dc.streamUrl in activeRecordingUrls) ({
                    val recId = app.nexstream.player.recording.NexStreamRecordingManager.findIdByUrl(context, _dc.streamUrl)
                    if (recId != null) app.nexstream.player.recording.RecordingService.stop(context, recId)
                    dialogProgram = null; dialogChannel = null
                }) else null,
                onRemind = {
                    val rid = reminderViewModel.reminderId(_dc.id, _dp.startTime)
                    if (rid in reminderIds) {
                        reminderViewModel.cancelReminder(_dc.id, _dp.startTime)
                    } else {
                        reminderViewModel.setReminder(_dc.id, _dc.name, _dc.streamUrl, _dp.title, _dp.startTime)
                    }
                    dialogProgram = null; dialogChannel = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f)
            )
        }
        } // end inner Box
    } // end Column

    // ── Channel prev/next for the embedded live-TV player ────────────────────
    val epgChannelIndex = if (playerChannel != null && playerCatchupDuration == 0L)
        channels.indexOfFirst { it.streamUrl == playerChannel!!.streamUrl } else -1
    val onEpgPrevChannel: (() -> Unit)? = if (epgChannelIndex > 0) ({
        scope.launch { playerChannel?.let { mainViewModel.repository.recordRecentlyWatchedChannel(it) } }
        playerCatchupProgramme = null
        playerChannel = channels[epgChannelIndex - 1]
    }) else null
    val onEpgNextChannel: (() -> Unit)? = if (epgChannelIndex in 0 until channels.lastIndex) ({
        scope.launch { playerChannel?.let { mainViewModel.repository.recordRecentlyWatchedChannel(it) } }
        playerCatchupProgramme = null
        playerChannel = channels[epgChannelIndex + 1]
    }) else null

    if (showPlayer && playerChannel != null && !showMultiScreen) {
        key(playerChannel!!.streamUrl) {
            PlayerScreen(
                channelUrl            = playerChannel!!.streamUrl,
                movieId               = if (playerCatchupDuration > 0L) "catchup" else null,
                episodeId             = null,
                seriesId              = null,
                startPosition         = 0L,
                catchupDuration       = playerCatchupDuration,
                nowPlayingTitle       = if (playerCatchupProgramme != null) playerCatchupProgramme!!.title else playerChannel!!.name,
                nowPlayingSubtitle    = if (playerCatchupProgramme != null) {
                    val prog = playerCatchupProgramme!!
                    val tf   = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val mins = ((prog.endTime - prog.startTime) / 60_000L).toInt()
                    val dur  = if (mins / 60 > 0 && mins % 60 > 0) "${mins / 60}h ${mins % 60}m" else if (mins / 60 > 0) "${mins / 60}h" else "${mins}m"
                    "${tf.format(java.util.Date(prog.startTime))} · $dur · ${playerChannel!!.name}"
                } else null,
                nowPlayingDescription = playerCatchupProgramme?.description,
                onPlayNextEpisode     = {},
                onPreviousChannel     = onEpgPrevChannel,
                onNextChannel         = onEpgNextChannel,
                onOpenMultiScreen     = if (playerCatchupDuration == 0L) ({ showMultiScreen = true }) else null,
                onBack = {
                    scope.launch { playerChannel?.let { mainViewModel.repository.recordRecentlyWatchedChannel(it) } }
                    showPlayer = false
                    if (epgMiniPlayerEnabled && playerCatchupDuration == 0L) {
                        showMiniPlayer = true  // continue in mini player
                    } else {
                        showMiniPlayer = false
                        playerChannel = null
                    }
                    playerCatchupDuration  = 0L
                    playerCatchupProgramme = null
                    onPlayerVisibilityChanged?.invoke(false)
                    scope.launch {
                        kotlinx.coroutines.delay(300)
                        gridView?.requestGridFocus()
                    }
                }
            )
        }
    }

    if (showMultiScreen && playerChannel != null) {
        app.nexstream.player.ui.screens.player.MultiScreenPlayerScreen(
            initialChannelUrl  = playerChannel!!.streamUrl,
            initialChannelName = playerChannel!!.name,
            onBack             = { showMultiScreen = false },
        )
    }

    // EPG action bar is a composable overlay (not a Dialog), so it doesn't dim the screen.
    // Its own BackHandler takes LIFO priority over MainScreen's handlers — no suppression needed.

    var dialogPosterUrl   by remember { mutableStateOf<String?>(null) }
    var dialogTmdbDesc    by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dialogProgram?.title) {
        val title = dialogProgram?.title
        if (title == null) { dialogPosterUrl = null; dialogTmdbDesc = null; return@LaunchedEffect }
        val needDesc = dialogProgram?.description.isNullOrEmpty()
        val data = viewModel.getProgramTmdbData(title, needDesc)
        dialogPosterUrl = data.posterUrl
        dialogTmdbDesc  = if (needDesc) data.description else null
    }

    // Start recording immediately when triggered (no confirmation dialog)
    LaunchedEffect(showRecordConfirm) {
        if (!showRecordConfirm) return@LaunchedEffect
        val rc = pendingRecordChannel ?: run { showRecordConfirm = false; return@LaunchedEffect }
        val rp = pendingRecordProgram ?: run { showRecordConfirm = false; return@LaunchedEffect }
        val profileId = activeProfile?.id ?: "default"
        val now = System.currentTimeMillis()
        val isFuture = rp.startTime > now
        if (!app.nexstream.player.downloads.NexStreamDownloadManager.hasEnoughSpace(context)) {
            val freeMb = app.nexstream.player.downloads.NexStreamDownloadManager.getFreeBytes(context) / (1024 * 1024)
            bannerMessage = "Not enough storage space (${freeMb}MB free, need 250MB)"
            pendingRecordChannel = null; pendingRecordProgram = null
            pendingRecordUrl = null; showRecordConfirm = false
            return@LaunchedEffect
        }
        if (isFuture) {
            val delay = rp.startTime - now
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<app.nexstream.player.worker.RecordingWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(
                    androidx.work.workDataOf(
                        app.nexstream.player.worker.RecordingWorker.KEY_STREAM_URL to rc.streamUrl,
                        app.nexstream.player.worker.RecordingWorker.KEY_TITLE to rp.title,
                        app.nexstream.player.worker.RecordingWorker.KEY_PROFILE_ID to profileId,
                        app.nexstream.player.worker.RecordingWorker.KEY_DESCRIPTION to rp.description,
                        app.nexstream.player.worker.RecordingWorker.KEY_END_TIME to rp.endTime
                    )
                )
                .addTag("recording_${rc.id}_${rp.startTime}")
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
            bannerMessage = "Recording of \"${rp.title}\" scheduled"
        } else {
            val url = pendingRecordUrl ?: rc.streamUrl
            app.nexstream.player.recording.RecordingService.start(
                context, url, rp.title, profileId,
                posterUrl = rp.icon, channelLogoUrl = rc.logoUrl,
                description = rp.description, endTimeMs = rp.endTime
            )
            bannerMessage = "Recording started"
        }
        pendingRecordChannel = null
        pendingRecordProgram = null
        pendingRecordUrl = null
        showRecordConfirm = false
    }

}

// ── Programme action bar ──────────────────────────────────────────────────────

@Composable
private fun ProgramActionBar(
    program: ProgramEntity,
    channel: ChannelEntity,
    isBookmarked: Boolean,
    isPlaceholder: Boolean,
    isRecording: Boolean,
    hasReminder: Boolean = false,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onWatch: () -> Unit,
    onRecord: (() -> Unit)?,
    onStopRecording: (() -> Unit)?,
    onRemind: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNsAccent.current
    val background = LocalNsBackground.current
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isNow = now in program.startTime..program.endTime
    val isFuture = program.startTime > now
    val isPast = !isNow && !isFuture
    val hasCatchUp = channel.tvArchive != 0

    data class Action(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val onClick: () -> Unit)
    val actions = buildList {
        add(Action(
            icon   = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            label  = if (isBookmarked) "Remove" else "My List",
            onClick = onToggleWatchlist
        ))
        if (!isFuture || isPlaceholder) {
            val watchLabel = if (isPast && hasCatchUp && !isPlaceholder) "Watch on Catch Up" else "Watch Now"
            add(Action(Icons.Default.PlayArrow, watchLabel, onWatch))
        }
        if (isRecording && onStopRecording != null) {
            add(Action(Icons.Default.Stop, "Stop Recording", onStopRecording))
        } else if (!isRecording && onRecord != null && !isPlaceholder && (isNow || isFuture)) {
            add(Action(Icons.Default.FiberManualRecord, "Record", onRecord))
        }
        if (isFuture && !isPlaceholder && onRemind != null) {
            add(Action(
                icon   = if (hasReminder) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                label  = if (hasReminder) "Cancel Reminder" else "Remind Me",
                onClick = onRemind
            ))
        }
    }

    // Default to Watch (index 1, after My List)
    var selectedIndex by remember { mutableIntStateOf(1) }
    val barFR = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { barFR.requestFocus() } catch (_: Exception) {}
    }

    BackHandler { onDismiss() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusRequester(barFR)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft  -> { selectedIndex = (selectedIndex - 1 + actions.size) % actions.size; true }
                    Key.DirectionRight -> { selectedIndex = (selectedIndex + 1) % actions.size; true }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        actions.getOrNull(selectedIndex)?.onClick?.invoke(); true
                    }
                    Key.Back -> { onDismiss(); true }
                    else -> false
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text       = if (isPlaceholder) channel.name else program.title,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isNow) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE53935))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                    if (!isPlaceholder) {
                        Text(
                            text  = "${timeFormat.format(java.util.Date(program.startTime))}–${timeFormat.format(java.util.Date(program.endTime))}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                        Text("·", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
                    }
                    Text(channel.name, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                actions.forEachIndexed { idx, action ->
                    EpgDialogPill(
                        icon       = action.icon,
                        label      = action.label,
                        isSelected = selectedIndex == idx,
                        accent     = accent,
                        background = background,
                        onClick    = action.onClick,
                    )
                }
            }
        }
    }
}

// ── EPG mini player / info strip ──────────────────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun EpgInfoPlayerStrip(
    focusedChannel: ChannelEntity?,
    focusedProgram: ProgramEntity?,
    playingChannel: ChannelEntity?,
    miniExoPlayer: ExoPlayer,
    isTV: Boolean = false,
    onFullScreen: () -> Unit,
    onCloseMini: () -> Unit,
) {
    val accent      = LocalNsAccent.current
    val nsTheme     = LocalNexStreamTheme.current
    val sTheme      = nsTheme.sidebar
    val textScale   = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    // Match the sidebar header height so the divider sits at the same Y level as the panel divider
    val channelNameHeight = (56f * textScale).dp
    val timeFormat  = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val now = System.currentTimeMillis()

    val displayChannel = focusedChannel
    val displayProgram = focusedProgram

    val timeLabel = remember(displayProgram) {
        displayProgram?.let { prog ->
            "${timeFormat.format(java.util.Date(prog.startTime))}–${timeFormat.format(java.util.Date(prog.endTime))}"
        }
    }

    // Space above + below strip; single Row so mini player spans full height; no horizontal padding
    // on Row so HorizontalDivider inside left Column reaches the left edge of the screen.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sTheme.panelBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(channelNameHeight + 120.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Left: channel name → full-width divider → programme info ─────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier.height(channelNameHeight).fillMaxWidth().padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = displayChannel?.name ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Divider: no horizontal padding so it reaches the left screen edge
                HorizontalDivider(color = sTheme.divider)
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            if (displayProgram != null && !displayProgram.title.startsWith("No Info")) {
                Text(
                    text = displayProgram.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = sTheme.categoryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timeLabel != null) {
                    val isLive = displayProgram.startTime <= now && now <= displayProgram.endTime
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFE53935))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Text(timeLabel, fontSize = 11.sp, color = sTheme.categoryText.copy(alpha = 0.60f))
                    }
                }
                if (!displayProgram.description.isNullOrBlank()) {
                    Text(
                        text = displayProgram.description!!,
                        fontSize = 11.sp,
                        color = sTheme.categoryText.copy(alpha = 0.55f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else if (displayChannel != null) {
                Text(
                    text = "Loading programme info…",
                    fontSize = 12.sp,
                    color = sTheme.categoryText.copy(alpha = 0.40f),
                )
            } else {
                Text(
                    text = "Browse the guide to see programme information",
                    fontSize = 12.sp,
                    color = sTheme.categoryText.copy(alpha = 0.40f),
                )
            }
                } // end programme info Column
            } // end left Column (weight 1f)

            // ── Right: mini player spans full Row height ───────────────────────
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (playingChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = miniExoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!isTV) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable { onCloseMini() }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.60f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(playingChannel.name, fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        text = "Select a programme\nto view",
                        fontSize = 11.sp,
                        color = sTheme.categoryText.copy(alpha = 0.45f),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } // end mini player Box
        } // end Row
        Spacer(Modifier.height(6.dp))
    } // end outer Column
}

@Suppress("unused")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ProgramDetailsDialog(
    program: ProgramEntity,
    channel: ChannelEntity,
    channelName: String,
    channelLogoUrl: String?,
    programPosterUrl: String? = null,
    tmdbDescription: String? = null,
    hasReminder: Boolean,
    isPlaceholder: Boolean,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onRemind: () -> Unit,
    onJumpToNow: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleWatchlist: () -> Unit = {},
    onRecord: (() -> Unit)? = null,
    onStopRecording: (() -> Unit)? = null,
    playlistName: String? = null,
) {
    val accent     = LocalNsAccent.current
    val background = LocalNsBackground.current
    val scope      = rememberCoroutineScope()

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now        = System.currentTimeMillis()
    val isNow      = now in program.startTime..program.endTime
    val isFuture   = program.startTime > now
    val isPast     = !isNow && !isFuture
    val hasCatchUp = channel.tvArchive != 0
    val canRecord  = onRecord != null && !isPlaceholder && (isNow || isFuture)
    val canStop    = onStopRecording != null && isNow && !isPlaceholder

    val timeLabel = when {
        isPlaceholder -> null
        isNow    -> "Now · ${timeFormat.format(Date(program.startTime))}–${timeFormat.format(Date(program.endTime))}"
        isFuture -> "Upcoming · ${timeFormat.format(Date(program.startTime))}–${timeFormat.format(Date(program.endTime))}"
        else     -> "${timeFormat.format(Date(program.startTime))}–${timeFormat.format(Date(program.endTime))}"
    }

    // Button count: 0=Close (floating top-right), 1=MyList, then context buttons
    val buttonCount = when {
        canStop                                -> 4  // Close, MyList, Watch, StopRecording
        canRecord && isFuture                  -> 5  // Close, MyList, RemindMe, JumpToNow, Record
        canRecord && isNow                     -> 4  // Close, MyList, Watch, Record
        isFuture && !isPlaceholder             -> 4  // Close, MyList, RemindMe, JumpToNow
        isPast && hasCatchUp && !isPlaceholder -> 4  // Close, MyList, JumpToNow, WatchOnCatchUp
        else                                   -> 3  // Close, MyList, Watch/JumpToNow
    }
    val defaultButton = when {
        canStop               -> 2  // Watch Now, not Stop Recording
        canRecord && isFuture -> 3  // Jump to Now, not Record
        canRecord && isNow    -> 2  // Watch Now, not Record
        else                  -> buttonCount - 1
    }
    var selectedButton by remember(program.startTime) { mutableStateOf(defaultButton) }
    var pressedButton  by remember { mutableStateOf<Int?>(null) }

    // Prefer TMDB backdrop; fall back to program EPG icon, then channel logo
    val backdropUrl = programPosterUrl?.takeIf { it.isNotEmpty() }

    val dialogFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    val descriptionText = when {
        isPlaceholder                        -> "No programme information available for this channel."
        !program.description.isNullOrEmpty() -> program.description!!
        !tmdbDescription.isNullOrEmpty()     -> tmdbDescription!!
        else                                 -> "No description available."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + buttonCount) % buttonCount; true }
                            Key.DirectionRight -> { selectedButton = (selectedButton + 1) % buttonCount; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                val btn = selectedButton
                                pressedButton = btn
                                scope.launch {
                                    kotlinx.coroutines.delay(120)
                                    pressedButton = null
                                    when (btn) {
                                        0 -> onDismiss()
                                        1 -> onToggleWatchlist()
                                        else -> when {
                                            canStop                -> when (btn) { 2 -> onWatch(); 3 -> onStopRecording?.invoke() }
                                            canRecord && isFuture -> when (btn) { 2 -> onRemind(); 3 -> onJumpToNow(); 4 -> onRecord?.invoke() }
                                            canRecord && isNow    -> when (btn) { 2 -> onWatch(); 3 -> onRecord?.invoke() }
                                            isFuture && !isPlaceholder -> when (btn) { 2 -> onRemind(); 3 -> onJumpToNow() }
                                            isNow && !isPlaceholder    -> if (btn == 2) onWatch()
                                            isPast && hasCatchUp && !isPlaceholder -> when (btn) { 2 -> onJumpToNow(); 3 -> onWatch() }
                                            isPast && !hasCatchUp && !isPlaceholder -> if (btn == 2) onJumpToNow()
                                            isPlaceholder -> if (btn == 2) onWatch()
                                            else -> onWatch()
                                        }
                                    }
                                }
                                true
                            }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
            ) {
                // ── Image / live mini player fills from very top ──────────────
                when {
                    !backdropUrl.isNullOrEmpty() -> AsyncImage(
                        model              = backdropUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                        alignment          = Alignment.TopCenter,
                    )
                    !channelLogoUrl.isNullOrEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model              = channelLogoUrl,
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize().padding(24.dp),
                            contentScale       = ContentScale.Fit,
                        )
                    }
                    else -> Box(modifier = Modifier.fillMaxSize().background(background))
                }

                // ── Gradient overlay ──────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f  to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.50f),
                                0.80f to Color.Black.copy(alpha = 0.88f),
                                1.0f  to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    )
                )

                // ── Floating Close at top-right ───────────────────────────────
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    EpgDialogPill(
                        label      = "Close",
                        icon       = Icons.Default.Close,
                        isSelected = selectedButton == 0,
                        isPressed  = pressedButton == 0,
                        accent     = accent,
                        background = background,
                        onClick    = onDismiss,
                    )
                }

                // ── Content at bottom ─────────────────────────────────────────
                Column(
                    modifier            = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text       = if (isPlaceholder) channelName else program.title,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (timeLabel != null) {
                        Text(text = timeLabel, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                    if (!playlistName.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PlaylistPlay, null, modifier = Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.45f))
                            Text(playlistName, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (!channelLogoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model              = channelLogoUrl,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp),
                                contentScale       = ContentScale.Fit
                            )
                        }
                        Text(text = channelName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.60f))
                        if (!program.category.isNullOrEmpty()) {
                            Text("·", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
                            Text(program.category!!, fontSize = 12.sp, color = Color.White.copy(alpha = 0.60f))
                        }
                    }
                    Text(
                        text     = descriptionText,
                        fontSize = 14.sp,
                        color    = Color.White.copy(alpha = 0.75f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    // ── Action buttons ────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        EpgDialogPill(
                            icon       = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label      = if (isBookmarked) "Remove" else "My List",
                            isSelected = selectedButton == 1,
                            isPressed  = pressedButton == 1,
                            accent     = accent,
                            background = background,
                            onClick    = onToggleWatchlist,
                        )
                        when {
                            canStop -> {
                                EpgDialogPill(Icons.Default.PlayArrow, "Watch Now", selectedButton == 2, pressedButton == 2, accent, background, onWatch)
                                EpgDialogPill(Icons.Default.Stop, "Stop Recording", selectedButton == 3, pressedButton == 3, accent, background, { onStopRecording?.invoke() })
                            }
                            isPlaceholder || isNow -> {
                                EpgDialogPill(Icons.Default.PlayArrow, "Watch Now", selectedButton == 2, pressedButton == 2, accent, background, onWatch)
                                if (canRecord && isNow) {
                                    EpgDialogPill(Icons.Default.FiberManualRecord, "Record", selectedButton == 3, pressedButton == 3, accent, background, { onRecord?.invoke() })
                                }
                            }
                            isFuture -> {
                                EpgDialogPill(
                                    icon       = if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                    label      = if (hasReminder) "Remove Reminder" else "Remind Me",
                                    isSelected = selectedButton == 2,
                                    isPressed  = pressedButton == 2,
                                    accent     = accent,
                                    background = background,
                                    onClick    = onRemind,
                                )
                                EpgDialogPill(Icons.Default.AccessTime, "Jump to Now", selectedButton == 3, pressedButton == 3, accent, background, onJumpToNow)
                                if (canRecord) {
                                    EpgDialogPill(Icons.Default.FiberManualRecord, "Record", selectedButton == 4, pressedButton == 4, accent, background, { onRecord?.invoke() })
                                }
                            }
                            isPast && !hasCatchUp ->
                                EpgDialogPill(Icons.Default.AccessTime, "Jump to Now", selectedButton == 2, pressedButton == 2, accent, background, onJumpToNow)
                            isPast && hasCatchUp -> {
                                EpgDialogPill(Icons.Default.AccessTime, "Jump to Now", selectedButton == 2, pressedButton == 2, accent, background, onJumpToNow)
                                EpgDialogPill(Icons.Default.PlayArrow, "Watch on Catch Up", selectedButton == 3, pressedButton == 3, accent, background, onWatch)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgDialogPill(
    icon:       androidx.compose.ui.graphics.vector.ImageVector?,
    label:      String,
    isSelected: Boolean,
    isPressed:  Boolean = false,
    accent:     Color,
    background: Color,
    onClick:    () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) accent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isSelected) Color.Transparent else accent.copy(alpha = 0.40f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = if (isSelected) background else Color.White,
                    modifier           = Modifier.size(16.dp),
                )
            }
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isSelected) background else Color.White,
            )
        }
    }
}

@HiltViewModel
class EPGViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    val profileManager: ProfileManager,
    private val tmdbPosterDao: TmdbPosterDao,
    private val channelGroupDao: ChannelGroupDao
) : ViewModel() {

    private val MAX_PROGRAMS_CHANNELS = 300

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelGroups: StateFlow<List<ChannelGroupEntity>> = profileManager.activeProfile
        .filterNotNull()
        .flatMapLatest { profile -> channelGroupDao.getGroupsForProfile(profile.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun getGroupChannelIds(groupId: String): List<String> =
        channelGroupDao.getChannelIdsForGroup(groupId)

    private val httpClient = OkHttpClient()

    data class ProgramTmdbData(val posterUrl: String?, val description: String?)

    suspend fun getProgramTmdbData(title: String, needDescription: Boolean): ProgramTmdbData = withContext(Dispatchers.IO) {
        val cached = tmdbPosterDao.getByTitle(title)
        if (cached != null && !needDescription) return@withContext ProgramTmdbData(cached.posterUrl, null)
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://nexstream.uk/api/tmdb.php?title=$enc&type=auto")
                .get()
                .addHeader("accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string(); response.close()
            if (response.isSuccessful && body != null) {
                val json = org.json.JSONObject(body)
                if (!json.has("error")) {
                    val overview    = json.optString("overview").takeIf { it.isNotEmpty() }
                    val backdropUrl = json.optString("backdrop_url").takeIf { it.isNotEmpty() }
                    val posterUrl   = json.optString("poster_url").takeIf   { it.isNotEmpty() }
                    val imageUrl    = backdropUrl ?: posterUrl
                    val finalUrl = if (imageUrl != null) {
                        tmdbPosterDao.upsert(TmdbPosterEntity(title, imageUrl))
                        imageUrl
                    } else cached?.posterUrl
                    return@withContext ProgramTmdbData(finalUrl, overview)
                }
            }
        } catch (_: Exception) {}
        ProgramTmdbData(cached?.posterUrl, null)
    }
    private val _programsMap = MutableStateFlow<Map<String, List<ProgramEntity>>>(emptyMap())
    val programsMap: StateFlow<Map<String, List<ProgramEntity>>> = _programsMap.asStateFlow()

    private val _isLoadingPrograms = MutableStateFlow(false)
    val isLoadingPrograms: StateFlow<Boolean> = _isLoadingPrograms.asStateFlow()

    private val startOfToday     = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val startOfWindow    = startOfToday - 24 * 60 * 60 * 1000L
    private val endOfWindow      = startOfToday + 3 * 24 * 60 * 60 * 1000L
    private val loadedChannelIds = mutableSetOf<String>()
    private var firstBatchReceived = false

    fun loadProgramsForVisibleChannels(channelIds: List<String>) {
        val newIds = channelIds.filter { it.isNotEmpty() && !loadedChannelIds.contains(it) }
        if (newIds.isEmpty()) return
        if (!firstBatchReceived) _isLoadingPrograms.value = true
        loadedChannelIds.addAll(newIds)
        viewModelScope.launch {
            repository.getProgramsForChannelsInRange(newIds, startOfWindow, endOfWindow)
                .collect { programs ->
                    val newEntries = withContext(Dispatchers.Default) {
                        val grouped = programs.groupBy { it.channelId }
                        val seed = newIds.filter { it !in grouped }.associateWith { id ->
                            _programsMap.value[id] ?: emptyList()
                        }
                        seed + grouped
                    }
                    _programsMap.update { current ->
                        val merged = current + newEntries
                        if (merged.size <= MAX_PROGRAMS_CHANNELS) return@update merged
                        // Evict oldest-inserted channels and allow them to reload when visible again
                        val toEvict = merged.size - MAX_PROGRAMS_CHANNELS
                        val evictedKeys = merged.keys.take(toEvict).toSet()
                        loadedChannelIds -= evictedKeys
                        merged.filterKeys { it !in evictedKeys }
                    }
                    if (!firstBatchReceived) {
                        firstBatchReceived = true
                        _isLoadingPrograms.value = false
                    }
                }
        }
    }

    fun resetLoadedChannels() {
        loadedChannelIds.clear()
        firstBatchReceived = false
        _isLoadingPrograms.value = false
        _programsMap.update { emptyMap() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategories(): Flow<List<String>> = profileManager.blockedTvCategories
        .flatMapLatest { blocked ->
            val trimmedBlocked = blocked.map { it.trim() }.toSet()
            repository.getChannelCategories().map { cats ->
                if (trimmedBlocked.isEmpty()) cats
                else cats.filter { it !in trimmedBlocked }
            }
        }

    suspend fun buildTimeshiftUrl(channel: ChannelEntity, program: ProgramEntity): String? {
        val playlist = repository.getAllPlaylists().first().firstOrNull { it.type == "XTREAM" } ?: return null
        val host = playlist.xtreamHost?.trimEnd('/') ?: return null
        val user = playlist.xtreamUsername ?: return null
        val pass = playlist.xtreamPassword ?: return null
        val streamId     = channel.streamId ?: extractStreamId(channel.streamUrl)
        val durationMins = ((program.endTime - program.startTime) / 60_000L).coerceAtLeast(1L)
        // Use LOCAL time — provider expects local broadcast time, not UTC
        val cal = Calendar.getInstance().apply { timeInMillis = program.startTime }
        val year   = cal.get(Calendar.YEAR)
        val month  = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val day    = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val hour   = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", cal.get(Calendar.MINUTE))
        return "$host/timeshift/$user/$pass/$durationMins/$year-$month-$day:$hour-$minute/$streamId.ts"
    }

    private fun extractStreamId(streamUrl: String): String =
        streamUrl.substringAfterLast("/").substringBefore(".")
}