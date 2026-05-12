package app.nexstream.player.ui.screens.epg

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import androidx.compose.foundation.focusable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.ui.screens.main.MainScreenViewModel
import app.nexstream.player.ui.screens.player.PlayerScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.screens.epg.ReminderViewModel
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
    onDialogOpen: ((Boolean) -> Unit)? = null
) {
    val playlists    by mainViewModel.playlists.collectAsState()
    val allChannels  by mainViewModel.allChannels.collectAsState()
    val activeProfile by viewModel.profileManager.activeProfile.collectAsState()

    when {
        playlists.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("No playlists found", style = MaterialTheme.typography.headlineMedium)
                    Text("Add a playlist in Settings to get started", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        allChannels.isEmpty() || activeProfile == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        else -> EPGContent(
            selectedCategory = selectedCategory,
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
            onToggleChannelWatchlist = onToggleChannelWatchlist
        )
    }
}

@Composable
private fun EPGContent(
    selectedCategory: String?,
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
    reminderViewModel: ReminderViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showPlayer             by remember { mutableStateOf(false) }
    var playerChannel          by remember { mutableStateOf<ChannelEntity?>(null) }
    var playerCatchupDuration  by remember { mutableStateOf(0L) }
    var playerCatchupProgramme by remember { mutableStateOf<app.nexstream.player.data.local.entity.ProgramEntity?>(null) }

    var dialogChannel       by remember { mutableStateOf<ChannelEntity?>(null) }
    var dialogProgram       by remember { mutableStateOf<ProgramEntity?>(null) }
    var dialogIsPlaceholder by remember { mutableStateOf(false) }

    val programsMap by viewModel.programsMap.collectAsState()
    val allChannels by mainViewModel.allChannels.collectAsState()
    val reminderIds by reminderViewModel.reminderIds.collectAsState()
    val isTV = LocalContext.current.packageManager.hasSystemFeature("android.software.leanback")

    val activeProfile by viewModel.profileManager.activeProfile.collectAsState()
    val blockedTvCategories by viewModel.profileManager.blockedTvCategories.collectAsState()

    val isContentFocusedRef = remember { mutableStateOf(isContentFocused) }
    LaunchedEffect(isContentFocused) { isContentFocusedRef.value = isContentFocused }

    val showPlayerRef = remember { mutableStateOf(false) }
    LaunchedEffect(showPlayer) { showPlayerRef.value = showPlayer }

    val channels = remember(allChannels, selectedCategory, blockedTvCategories, watchlistIds) {
        val trimmedBlocked = blockedTvCategories.map { it.trim() }.toSet()
        val filtered = if (trimmedBlocked.isEmpty()) allChannels
        else allChannels.filter { ch -> ch.groupTitle == null || ch.groupTitle.trim() !in trimmedBlocked }
        when (selectedCategory) {
            null              -> filtered
            "__favourites__"  -> {
                android.util.Log.d("EPGFav", "watchlistIds=${watchlistIds.take(5)}, total=${watchlistIds.size}")
                android.util.Log.d("EPGFav", "sample ch.ids=${filtered.take(5).map { it.id }}")
                filtered.filter { ch -> ch.id in watchlistIds }
            }
            else              -> filtered.filter { ch -> ch.groupTitle?.trim() == selectedCategory.trim() }
        }
    }

    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val epgColors = nsTheme.epg
    val typography = nsTheme.typography

    // Empty favourites state — shown as overlay, no early return (illegal in Compose)
    val showEmptyFavourites = selectedCategory == "__favourites__" && channels.isEmpty()

    val epgProgramsMap = remember(programsMap) {
        programsMap.mapValues { (_, list) ->
            list.map { p ->
                EPGProgram(
                    entity = if (p.title.isBlank()) p.copy(title = "No Information Provided") else p,
                    isPlaceholder = p.id.startsWith("placeholder-") || p.title.isBlank()
                )
            }
        }
    }

    var gridView by remember { mutableStateOf<EPGGridView?>(null) }

    LaunchedEffect(refocusTick) {
        if (refocusTick > 0) {
            kotlinx.coroutines.delay(150)
            gridView?.requestGridFocus()
        }
    }

    LaunchedEffect(selectedCategory) {
        gridView?.scrollToTop()
        gridView?.invalidateFilledCache()
    }

    BackHandler(enabled = isContentFocused && !showPlayer && !isParentPlayerVisible) {
        gridView?.jumpToNow()
        onBack()
    }

    Box(Modifier.fillMaxSize()) {
        // Empty favourites overlay
        if (showEmptyFavourites) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No channels in your favourites yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f))
                    Text("Long press a channel in the guide to add it",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f))
                }
            }
        }

        // Show header for special categories
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
                            dialogChannel = channel
                            dialogProgram = program
                            dialogIsPlaceholder = isPlaceholder
                        }
                        override fun onChannelClicked(channel: ChannelEntity) {
                            playerChannel = channel
                            showPlayer = true
                            onPlayerVisibilityChanged?.invoke(true)
                            scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(channel) }
                        }
                        override fun onBack() { onBack() }
                        override fun onReady() { onReady() }
                        override fun onVisibleChannelIdsChanged(ids: List<String>) {
                            viewModel.loadProgramsForVisibleChannels(ids)
                        }
                    }
                }
            },
            update = { view ->
                view.nexTheme      = epgColors
                view.textScale     = typography.scale
                view.textBold      = typography.bold
                view.channels      = channels
                view.programsMap   = epgProgramsMap
                view.reminderIds   = reminderIds
                view.playerVisible = showPlayer
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor   = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor    = MaterialTheme.colorScheme.primary,
                shape          = RoundedCornerShape(8.dp)
            )
        }
    }

    if (showPlayer && playerChannel != null) {
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
                onBack = {
                    scope.launch { playerChannel?.let { mainViewModel.repository.recordRecentlyWatchedChannel(it) } }
                    showPlayer             = false
                    playerChannel          = null
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

    LaunchedEffect(dialogProgram) { onDialogOpen?.invoke(dialogProgram != null) }

    val dp = dialogProgram
    val dc = dialogChannel
    if (dp != null && dc != null) {
        val remId       = reminderViewModel.reminderId(dc.id, dp.startTime)
        val hasReminder = remId in reminderIds
        ProgramDetailsDialog(
            program        = dp,
            channel        = dc,
            channelName    = dc.name,
            channelLogoUrl = dc.logoUrl,
            hasReminder    = hasReminder,
            isPlaceholder  = dialogIsPlaceholder,
            onDismiss      = { dialogProgram = null; dialogChannel = null },
            isBookmarked   = dc.id in watchlistIds,
            onToggleWatchlist = { onToggleChannelWatchlist?.invoke(dc.id, dc.name, dc.logoUrl, dc.streamUrl) },
            onWatch = {
                val now    = System.currentTimeMillis()
                val isPast = dp.startTime < now && dp.endTime < now
                if (isPast && dc.tvArchive != 0) {
                    scope.launch {
                        val url = viewModel.buildTimeshiftUrl(dc, dp)
                        if (url != null) {
                            playerChannel          = dc.copy(streamUrl = url)
                            playerCatchupDuration  = dp.endTime - dp.startTime
                            playerCatchupProgramme = dp
                            showPlayer             = true
                            onPlayerVisibilityChanged?.invoke(true)
                        } else {
                            snackbarHostState.showSnackbar("Catch up unavailable for this programme")
                        }
                    }
                } else {
                    playerChannel = dc
                    showPlayer    = true
                    onPlayerVisibilityChanged?.invoke(true)
                    scope.launch { mainViewModel.repository.recordRecentlyWatchedChannel(dc) }
                }
                dialogProgram = null; dialogChannel = null
            },
            onRemind = {
                if (hasReminder) {
                    reminderViewModel.cancelReminder(dc.id, dp.startTime)
                    scope.launch { snackbarHostState.showSnackbar("Reminder cancelled for ${dp.title}") }
                } else {
                    reminderViewModel.setReminder(
                        channelId    = dc.id,
                        channelName  = dc.name,
                        streamUrl    = dc.streamUrl,
                        programTitle = dp.title,
                        startTime    = dp.startTime
                    )
                    scope.launch { snackbarHostState.showSnackbar("Reminder set for ${dp.title}") }
                }
                dialogProgram = null; dialogChannel = null
            },
            onJumpToNow = {
                gridView?.jumpToNow()
                // Update dialog to current programme — don't close it
                val now = System.currentTimeMillis()
                val currentProg = (epgProgramsMap[dc.epgChannelId?.takeIf { it.isNotEmpty() }]
                    ?: epgProgramsMap[dc.id])?.firstOrNull { now in it.entity.startTime..it.entity.endTime }
                if (currentProg != null) {
                    dialogProgram = currentProg.entity
                    dialogIsPlaceholder = currentProg.isPlaceholder
                } else {
                    dialogProgram = null; dialogChannel = null
                }
            }
        )
    }
}

@Composable
private fun ProgramDetailsDialog(
    program: ProgramEntity,
    channel: ChannelEntity,
    channelName: String,
    channelLogoUrl: String?,
    hasReminder: Boolean,
    isPlaceholder: Boolean,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onRemind: () -> Unit,
    onJumpToNow: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleWatchlist: () -> Unit = {}
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now        = System.currentTimeMillis()
    val isNow      = now in program.startTime..program.endTime
    val isFuture   = program.startTime > now
    val isPast     = !isNow && !isFuture
    val hasCatchUp = channel.tvArchive != 0

    val timeLabel = when {
        isPlaceholder -> null
        isNow    -> "Now - ${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}"
        isFuture -> "Upcoming - ${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}"
        else     -> "${timeFormat.format(Date(program.startTime))} - ${timeFormat.format(Date(program.endTime))}"
    }

    val buttonCount = when {
        isFuture && !isPlaceholder             -> 4  // Close MyList RemindMe JumpToNow
        isPast && hasCatchUp && !isPlaceholder -> 4  // Close MyList JumpToNow WatchCatchUp
        else                                   -> 3  // Close MyList WatchNow/JumpToNow
    }
    // Default is always the rightmost button
    val defaultButton = buttonCount - 1
    var selectedButton by remember(program.startTime) { mutableStateOf(defaultButton) }

    val isTV     = LocalContext.current.packageManager.hasSystemFeature("android.software.leanback")
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val screenH  = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val wFrac    = if (isTV || isTablet) 0.85f else 0.95f
    val hFrac    = if (isTV || isTablet) 0.85f else 0.75f
    val topH     = if (isTV || isTablet) (screenH * 0.30f).coerceAtMost(280.dp) else (screenH * 0.22f).coerceAtMost(180.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(wFrac).fillMaxHeight(hFrac),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            val dialogFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { dialogFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            Column(
                Modifier.fillMaxSize()
                    .focusRequester(dialogFocusRequester)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + buttonCount) % buttonCount; true }
                            Key.DirectionRight -> { selectedButton = (selectedButton + 1) % buttonCount; true }
                            Key.Enter, Key.DirectionCenter -> {
                                when (selectedButton) {
                                    0 -> onDismiss()
                                    1 -> onToggleWatchlist()
                                    else -> when {
                                        isFuture && !isPlaceholder -> when (selectedButton) { 2 -> onRemind(); 3 -> onJumpToNow() }
                                        isNow && !isPlaceholder -> when (selectedButton) { 2 -> onWatch() }
                                        isPast && hasCatchUp && !isPlaceholder -> when (selectedButton) { 2 -> onJumpToNow(); 3 -> onWatch() }
                                        isPast && !hasCatchUp && !isPlaceholder -> when (selectedButton) { 2 -> onJumpToNow() }
                                        isPlaceholder -> when (selectedButton) { 2 -> onWatch() }
                                        else -> onWatch()
                                    }
                                }
                                true
                            }
                            else -> false
                        } else false
                    }
            ) {
                Box(Modifier.fillMaxWidth().height(topH).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (!channelLogoUrl.isNullOrEmpty()) {
                        AsyncImage(model = channelLogoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isPlaceholder) channelName else program.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        if (timeLabel != null) Text(timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isPlaceholder && !program.description.isNullOrEmpty())
                        Text(program.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (isPlaceholder)
                        Text("No programme information available for this channel.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ── Action bar: all right-aligned ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    // 0 = Close (leftmost of right-aligned group)
                    if (selectedButton == 0) Button(onClick = onDismiss, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Close")
                    } else OutlinedButton(onClick = onDismiss, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Close")
                    }
                    // 1 = My List
                    if (selectedButton == 1) Button(onClick = onToggleWatchlist, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isBookmarked) "Remove from My List" else "Add to My List")
                    } else OutlinedButton(onClick = onToggleWatchlist, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isBookmarked) "Remove from My List" else "Add to My List")
                    }
                    // Action buttons — unique index per button, no sharing
                    when {
                        isPlaceholder || isNow -> {
                            // 2 = Watch Now (default, rightmost)
                            if (selectedButton == 2) Button(onClick = onWatch, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Watch Now")
                            } else OutlinedButton(onClick = onWatch, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Watch Now")
                            }
                        }
                        isFuture -> {
                            // 2 = Remind Me, 3 = Jump to Now (rightmost)
                            if (selectedButton == 2) Button(onClick = onRemind, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text(if (hasReminder) "Remove Reminder" else "Remind Me")
                            } else OutlinedButton(onClick = onRemind, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text(if (hasReminder) "Remove Reminder" else "Remind Me")
                            }
                            if (selectedButton == 3) Button(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            } else OutlinedButton(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            }
                        }
                        isPast && !hasCatchUp -> {
                            // 2 = Jump to Now
                            if (selectedButton == 2) Button(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            } else OutlinedButton(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            }
                        }
                        isPast && hasCatchUp -> {
                            // 2 = Jump to Now, 3 = Watch on Catch Up (default, rightmost)
                            if (selectedButton == 2) Button(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            } else OutlinedButton(onClick = onJumpToNow, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jump to Now")
                            }
                            if (selectedButton == 3) Button(onClick = onWatch, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Watch on Catch Up")
                            } else OutlinedButton(onClick = onWatch, modifier = Modifier.focusProperties { canFocus = false }, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Watch on Catch Up")
                            }
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class EPGViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    val profileManager: ProfileManager
) : ViewModel() {
    private val _programsMap = MutableStateFlow<Map<String, List<ProgramEntity>>>(emptyMap())
    val programsMap: StateFlow<Map<String, List<ProgramEntity>>> = _programsMap.asStateFlow()

    private val startOfToday     = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val startOfWindow    = startOfToday - 24 * 60 * 60 * 1000L
    private val endOfWindow      = startOfToday + 3 * 24 * 60 * 60 * 1000L
    private val loadedChannelIds = mutableSetOf<String>()

    fun loadProgramsForVisibleChannels(channelIds: List<String>) {
        val newIds = channelIds.filter { it.isNotEmpty() && !loadedChannelIds.contains(it) }
        if (newIds.isEmpty()) return
        loadedChannelIds.addAll(newIds)
        viewModelScope.launch {
            repository.getProgramsForChannelsInRange(newIds, startOfWindow, endOfWindow)
                .collect { programs ->
                    _programsMap.update { current ->
                        val grouped = programs.groupBy { it.channelId }
                        val seed = newIds.filter { it !in grouped }.associateWith { id -> current[id] ?: emptyList() }
                        current + seed + grouped
                    }
                }
        }
    }

    fun resetLoadedChannels() { loadedChannelIds.clear(); _programsMap.update { emptyMap() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCategories(): Flow<List<String>> = profileManager.blockedTvCategories
        .flatMapLatest { blocked ->
            repository.getAllPlaylists().flatMapLatest { playlists ->
                if (playlists.isEmpty()) flowOf(emptyList())
                else combine(playlists.map { p -> repository.getChannelsByPlaylist(p.id) }) { arrays ->
                    val seen = mutableSetOf<String>()
                    arrays.flatMap { it }
                        .mapNotNull { ch -> ch.groupTitle?.trim() }
                        .filter { cat -> cat.isNotBlank() && seen.add(cat) && cat !in blocked.map { it.trim() }.toSet() }
                }
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