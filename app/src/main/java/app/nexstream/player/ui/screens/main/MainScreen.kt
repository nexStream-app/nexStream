package app.nexstream.player.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.ui.components.Sidebar
import app.nexstream.player.ui.screens.appearance.AppearanceScreen
import app.nexstream.player.ui.screens.catchup.CatchUpScreen
import app.nexstream.player.ui.screens.epg.EPGScreen
import app.nexstream.player.ui.screens.movies.MoviesScreen
import app.nexstream.player.ui.screens.search.SearchScreen
import app.nexstream.player.ui.screens.series.SeriesScreen
import app.nexstream.player.ui.screens.series.SeriesViewModel
import app.nexstream.player.ui.screens.settings.SettingsScreen
import app.nexstream.player.ui.screens.movies.MoviesViewModel
import app.nexstream.player.ui.screens.epg.EPGViewModel
import app.nexstream.player.ui.screens.player.PlayerScreen
import app.nexstream.player.ui.screens.profile.ProfileEditScreen
import app.nexstream.player.ui.screens.profile.ProfilesSettingsScreen
import app.nexstream.player.ui.screens.settings.LicenceScreen
import app.nexstream.player.ui.screens.settings.SettingsMenuScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.screens.recentlywatched.RecentlyWatchedScreen
import kotlinx.coroutines.flow.first
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.screens.main.hasCategoryPanel
import app.nexstream.player.ui.screens.main.isSettings
import app.nexstream.player.ui.screens.main.rootSection
import kotlinx.coroutines.launch

enum class Zone { RAIL, PANEL, CONTENT }

@Composable
fun MainScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToAddPlaylist: () -> Unit,
    pendingPlayUrl: String? = null,
    pendingPlayName: String? = null,
    onPendingPlayConsumed: () -> Unit = {},
    viewModel: MainScreenViewModel = hiltViewModel(),
    movieViewModel: MoviesViewModel = hiltViewModel(),
    epgViewModel: EPGViewModel = hiltViewModel(),
    seriesViewModel: SeriesViewModel = hiltViewModel(),
    watchlistViewModel: WatchlistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val repository = viewModel.repository
    val isLoadingEPG by repository.isLoadingEPG.collectAsState()
    val isLoadingVOD by repository.isLoadingVOD.collectAsState()
    val isLoadingSeries by repository.isLoadingSeries.collectAsState()
    val scope = rememberCoroutineScope()
    val catchUpViewModel: app.nexstream.player.ui.screens.catchup.CatchUpViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val catchUpChannels by catchUpViewModel.allCatchUpChannels.collectAsState()
    val recentlyWatched by repository.getRecentlyWatched().collectAsState(initial = emptyList())

    LaunchedEffect(recentlyWatched) {
        android.util.Log.d("RecentlyWatched", "Count: ${recentlyWatched.size}")
    }

    var currentNowPlayingTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNowPlayingSubtitle by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNowPlayingDescription by rememberSaveable { mutableStateOf<String?>(null) }

    var currentRoute by remember { mutableStateOf<AppRoute>(AppRoute.Guide) }

    var showMovieCategories by rememberSaveable { mutableStateOf(false) }
    var showSeriesCategories by rememberSaveable { mutableStateOf(false) }
    var showGuideCategories by rememberSaveable { mutableStateOf(false) }
    var showSettingsMenu by rememberSaveable { mutableStateOf(false) }

    var selectedMovieCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSeriesCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGuideCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val movieCategories by movieViewModel.getCategories().collectAsState(initial = emptyList())
    val guideCategories by epgViewModel.getCategories().collectAsState(initial = emptyList())
    val seriesCategories by seriesViewModel.getCategories().collectAsState(initial = emptyList())

    var currentChannelUrl by rememberSaveable { mutableStateOf("") }
    var currentMovieId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentEpisodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentStartPosition by rememberSaveable { mutableStateOf(0L) }
    var showPlayer by remember { mutableStateOf(false) }
    var epgPlayerVisible by remember { mutableStateOf(false) }
    var playerClosedFromRoute by remember { mutableStateOf<AppRoute?>(null) }
    var currentCatchupDuration by rememberSaveable { mutableStateOf(0L) }

    var lastMovieIndex by rememberSaveable { mutableStateOf(0) }
    var lastSeriesIndex by rememberSaveable { mutableStateOf(0) }
    var selectedCatchUpChannel by remember { mutableStateOf<app.nexstream.player.data.local.entity.ChannelEntity?>(null) }
    var selectedCatchUpDateKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(catchUpChannels) {
        if (selectedCatchUpChannel == null && catchUpChannels.isNotEmpty()) {
            selectedCatchUpChannel = catchUpChannels.first()
            selectedCatchUpDateKey = null // reset to All
        }
    }
    val catchUpAvailableDates by catchUpViewModel.availableDates.collectAsState()

    var epgFocusRequest by remember { mutableStateOf<(() -> Unit)?>(null) }
    var epgPlayerChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    var movieRestoreTick by remember { mutableStateOf(0) }
    var seriesRestoreTick by remember { mutableStateOf(0) }
    // Direct focus requesters so onRequestContentFocus can reach first card immediately
    // Grid view refs — set by AndroidView factory, used to push focus into RecyclerView
    var movieGridViewRef by remember { mutableStateOf<app.nexstream.player.ui.components.PosterGridView?>(null) }
    var seriesGridViewRef   by remember { mutableStateOf<app.nexstream.player.ui.components.PosterGridView?>(null) }
    var catchUpGridViewRef  by remember { mutableStateOf<app.nexstream.player.ui.components.PosterGridView?>(null) }
    var catchUpRestoreTick by remember { mutableStateOf(0) }
    var epgRestoreTick by remember { mutableStateOf(0) }

    val settingsViewModel: app.nexstream.player.ui.screens.settings.SettingsViewModel = hiltViewModel()
    val licenceViewModel: app.nexstream.player.ui.screens.settings.LicenceViewModel = hiltViewModel()
    val settingsPlaylists by settingsViewModel.playlists.collectAsState()
    val licenceState by licenceViewModel.uiState.collectAsState()
    val xtreamPlaylist = remember(settingsPlaylists) { settingsPlaylists.firstOrNull { it.type == "XTREAM" } }
    val watchlistIds by watchlistViewModel.watchlistIds.collectAsState()
    val activeProfile by watchlistViewModel.profileManager.activeProfile.collectAsState()
    val allProfiles by watchlistViewModel.profileManager.profiles.collectAsState()

    LaunchedEffect(pendingPlayUrl) {
        if (pendingPlayUrl != null) {
            currentChannelUrl            = pendingPlayUrl
            currentNowPlayingTitle       = pendingPlayName
            currentNowPlayingSubtitle    = null
            currentNowPlayingDescription = null
            currentMovieId               = null
            currentEpisodeId             = null
            currentSeriesId              = null
            currentStartPosition         = 0L
            showPlayer                   = true
            onPendingPlayConsumed()
        }
    }

    var sidebarPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var sidebarExpandedRoute by remember { mutableStateOf<AppRoute?>(null) }

    fun closeAllPanels() {
        showGuideCategories  = false
        showMovieCategories  = false
        showSeriesCategories = false
        showSettingsMenu     = false
        sidebarPanelExpanded = false
        sidebarExpandedRoute = null
    }

    var showExitDialog by remember { mutableStateOf(false) }
    var showProfileSwitch by remember { mutableStateOf(false) }
    var isDialogOpen by remember { mutableStateOf(false) }

    // ── Focus ────────────────────────────────────────────────────────────────
    val railFR    = remember { FocusRequester() }
    val panelFR   = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }

    // Zone is declared at package level (see below)
    var zone by remember { mutableStateOf(Zone.RAIL) }

    // Increment to re-focus the current rail item
    var sidebarRefocusTick by remember { mutableStateOf(0) }
    // Increment to focus the selected panel item
    var panelFocusTick by remember { mutableStateOf(0) }

    // Focus the appropriate content screen
    fun focusContent() {
        when (currentRoute) {
            AppRoute.Guide -> epgFocusRequest?.invoke()
                ?: try { contentFR.requestFocus() } catch (_: Exception) {}
            AppRoute.Movies -> scope.launch {
                if (movieGridViewRef?.requestItemFocusNow(lastMovieIndex) != true) {
                    kotlinx.coroutines.delay(150)
                    movieGridViewRef?.requestItemFocus(lastMovieIndex)
                }
            }
            AppRoute.Series -> scope.launch {
                if (seriesGridViewRef?.requestItemFocusNow(lastSeriesIndex) != true) {
                    kotlinx.coroutines.delay(150)
                    seriesGridViewRef?.requestItemFocus(lastSeriesIndex)
                }
            }
            AppRoute.CatchUp -> scope.launch {
                if (catchUpGridViewRef?.requestItemFocusNow(0) != true) {
                    kotlinx.coroutines.delay(150)
                    catchUpGridViewRef?.requestItemFocus(0)
                }
            }
            else -> try { contentFR.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── State transitions ────────────────────────────────────────────────────
    // Rail item navigated to → panel opens only if user explicitly navigated
    // (not on startup — startup lands on rail with no panel open)
    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute) {
        zone = Zone.RAIL
        if (isFirstLoad) {
            // Startup — no panel, just show the rail
            isFirstLoad = false
            sidebarPanelExpanded = false
            sidebarExpandedRoute = null
            android.util.Log.d("NexStreamFocus", "startup route=$currentRoute zone=RAIL panel=CLOSED")
        } else if (currentRoute.hasCategoryPanel) {
            movieGridViewRef?.blockFocus()
            seriesGridViewRef?.blockFocus()
            catchUpGridViewRef?.blockFocus()
            sidebarPanelExpanded = true
            sidebarExpandedRoute = if (currentRoute.isSettings) AppRoute.Settings else currentRoute
            android.util.Log.d("NexStreamFocus", "navigate route=$currentRoute zone=RAIL panel=OPEN")
        } else {
            sidebarPanelExpanded = false
            sidebarExpandedRoute = null
            android.util.Log.d("NexStreamFocus", "navigate route=$currentRoute zone=RAIL panel=CLOSED")
        }
    }

    // When Movies/Series categories load while zone=PANEL, focus the selected item
    LaunchedEffect(movieCategories) {
        if (zone == Zone.PANEL && currentRoute == AppRoute.Movies && movieCategories.isNotEmpty()) {
            panelFocusTick++
        }
    }
    LaunchedEffect(seriesCategories) {
        if (zone == Zone.PANEL && currentRoute == AppRoute.Series && seriesCategories.isNotEmpty()) {
            panelFocusTick++
        }
    }

    // Startup: land on Guide rail item
    var initialFocusGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        initialFocusGranted = true
        kotlinx.coroutines.delay(32)
        sidebarRefocusTick++
    }

    // D-pad right from rail: panel open → enter panel; no panel → enter content
    // D-pad right from panel → enter content
    // These are fired by Sidebar callbacks (onDpadRight / onRequestContentFocus)

    BackHandler(enabled = epgPlayerChannel != null) {
        scope.launch { epgPlayerChannel?.let { repository.recordRecentlyWatchedChannel(it) } }
        epgPlayerChannel = null
    }

    // Back from PANEL → close panel, return to rail (registered first = lower priority)
    BackHandler(enabled = zone == Zone.PANEL && sidebarPanelExpanded) {
        zone = Zone.RAIL
        sidebarPanelExpanded = false
        sidebarExpandedRoute = null
        sidebarRefocusTick++
    }

    // Back from CONTENT — only when no dialog is open inside the screen
    BackHandler(enabled = zone == Zone.CONTENT && !isDialogOpen) {
        movieGridViewRef?.blockFocus()
        seriesGridViewRef?.blockFocus()
        catchUpGridViewRef?.blockFocus()
        if (selectedMovieCategory == "__search__" || selectedMovieCategory == "__favourites__") selectedMovieCategory = null
        if (selectedSeriesCategory == "__search__" || selectedSeriesCategory == "__favourites__") selectedSeriesCategory = null
        if (sidebarPanelExpanded) {
            zone = Zone.PANEL
            panelFocusTick++
        } else {
            zone = Zone.RAIL
            sidebarRefocusTick++
        }
    }

    // Back on RAIL → show exit dialog (registered last = highest priority)
    BackHandler(enabled = zone == Zone.RAIL) {
        showExitDialog = true
    }

    // ── Restore ticks — fired after player closes ─────────────────────────
    LaunchedEffect(epgRestoreTick) {
        if (epgRestoreTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        zone = Zone.CONTENT
        epgFocusRequest?.invoke()
    }

    LaunchedEffect(movieRestoreTick) {
        if (movieRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT
        kotlinx.coroutines.delay(100)
        val focused = movieGridViewRef?.requestItemFocusNow(lastMovieIndex)
        if (focused != true) {
            kotlinx.coroutines.delay(200)
            movieGridViewRef?.requestItemFocus(lastMovieIndex)
        }
    }

    LaunchedEffect(seriesRestoreTick) {
        if (seriesRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT
        kotlinx.coroutines.delay(100)
        val focused = seriesGridViewRef?.requestItemFocusNow(lastSeriesIndex)
        if (focused != true) {
            kotlinx.coroutines.delay(200)
            seriesGridViewRef?.requestItemFocus(lastSeriesIndex)
        }
    }

    LaunchedEffect(catchUpRestoreTick) {
        if (catchUpRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT
        kotlinx.coroutines.delay(100)
        if (catchUpGridViewRef?.requestItemFocusNow(0) != true) {
            kotlinx.coroutines.delay(200)
            catchUpGridViewRef?.requestItemFocus(0)
        }
    }

    val epgOnBack: () -> Unit = {
        if (sidebarPanelExpanded) { zone = Zone.PANEL; panelFocusTick++ }
        else { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ }
    }


    if (showExitDialog) {
        var exitSelected by remember { mutableStateOf(0) } // 0=Cancel 1=Exit
        val exitDialogFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(80)
            try { exitDialogFocus.requestFocus() } catch (_: Exception) {}
        }
        Dialog(
            onDismissRequest = { showExitDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.55f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                        .focusRequester(exitDialogFocus)
                        .focusable()
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) when (e.key) {
                                Key.DirectionLeft  -> { exitSelected = (exitSelected - 1 + 2) % 2; true }
                                Key.DirectionRight -> { exitSelected = (exitSelected + 1) % 2; true }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (exitSelected == 0) showExitDialog = false
                                    else (context as? android.app.Activity)?.finish()
                                    true
                                }
                                else -> false
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Exit nexStream?", style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Are you sure you want to exit?", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(Modifier.weight(1f))
                        if (exitSelected == 0)
                            Button(onClick = { showExitDialog = false },
                                shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Cancel")
                            }
                        else OutlinedButton(onClick = { showExitDialog = false },
                            shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                        if (exitSelected == 1)
                            Button(onClick = { (context as? android.app.Activity)?.finish() },
                                shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.ExitToApp, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Exit")
                            }
                        else OutlinedButton(onClick = { (context as? android.app.Activity)?.finish() },
                            shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.ExitToApp, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Exit")
                        }
                    }
                }
            }
        }
    }

    if (showProfileSwitch) {
        app.nexstream.player.ui.screens.profile.ProfileSwitchDialog(
            profiles        = allProfiles,
            activeProfileId = activeProfile?.id,
            onProfileSelected = { profile ->
                showProfileSwitch = false
                scope.launch { watchlistViewModel.profileManager.setActiveProfile(profile) }
                // Reset to guide as if first load
                currentRoute = AppRoute.Guide
                sidebarPanelExpanded = false
                sidebarExpandedRoute = null
                selectedMovieCategory = null
                selectedSeriesCategory = null
                selectedGuideCategory = null
                scope.launch {
                    kotlinx.coroutines.delay(200)
                    zone = Zone.RAIL
                    sidebarRefocusTick++
                }
            },
            onDismiss = { showProfileSwitch = false },
            onManageProfiles = { showProfileSwitch = false; currentRoute = AppRoute.SettingsProfiles }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        val anyDialogOpen = isDialogOpen || showProfileSwitch || showExitDialog
        val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (anyDialogOpen) 0.55f else 0f,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "dialogScrim"
        )

        Row(modifier = Modifier.fillMaxSize()) {

            // ── Sidebar — always composed, collapsed when player is visible ───
            val sidebarVisible = !epgPlayerVisible && !showPlayer
            Box(
                modifier = Modifier
                    .then(if (sidebarVisible) Modifier.wrapContentWidth() else Modifier.width(0.dp))
                    .fillMaxHeight()
                    .graphicsLayer { alpha = if (sidebarVisible) 1f else 0f }
                    .then(if (!sidebarVisible || !initialFocusGranted) Modifier.focusProperties { canFocus = false } else Modifier)
            ) {
                Sidebar(
                    currentRoute = currentRoute,
                    panelExpanded         = sidebarPanelExpanded,
                    contentActive         = zone == Zone.CONTENT,
                    expandedRoute = sidebarExpandedRoute,
                    onPanelExpandedChange = { expanded, route ->
                        sidebarPanelExpanded = expanded
                        sidebarExpandedRoute = route
                        if (expanded) catchUpGridViewRef?.blockFocus()
                    },
                    railFR  = railFR,
                    panelFR = panelFR,
                    sidebarRefocusTick = sidebarRefocusTick,
                    panelFocusTick     = panelFocusTick,
                    onRailFocusChanged  = { if (it) zone = Zone.RAIL },
                    onPanelFocusChanged = { if (it) zone = Zone.PANEL },
                    onEnterPanel   = { zone = Zone.PANEL; panelFocusTick++ },
                    onEnterContent = { zone = Zone.CONTENT; focusContent() },
                    onExitPanelToRail = { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ },
                    onNavigate = { route -> currentRoute = route },
                    onBackToMainMenu = {
                        closeAllPanels()
                        selectedGuideCategory = null; selectedMovieCategory = null; selectedSeriesCategory = null
                        try { contentFR.requestFocus() } catch (_: Exception) {}
                    },
                    selectedSettingsRoute = if (currentRoute.isSettings && currentRoute != AppRoute.Settings) currentRoute else null,
                    onSettingsItemSelected = { route ->
                        currentRoute = route
                        zone = Zone.CONTENT
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            try { contentFR.requestFocus() } catch (_: Exception) {}
                        }
                    },
                    showGuideCategories  = showGuideCategories,
                    showMovieCategories  = showMovieCategories,
                    showSeriesCategories = showSeriesCategories,
                    showSettingsMenu     = showSettingsMenu,
                    guideCategories      = guideCategories,
                    movieCategories      = movieCategories,
                    seriesCategories       = seriesCategories,
                    catchUpAvailableDates    = catchUpAvailableDates,
                    catchUpChannels          = catchUpChannels,
                    selectedCatchUpChannel   = selectedCatchUpChannel,
                    onCatchUpChannelSelected = { ch -> selectedCatchUpChannel = ch; selectedCatchUpDateKey = null },
                    selectedCatchUpDateKey   = selectedCatchUpDateKey,
                    onCatchUpDateSelected    = { catchUpGridViewRef?.blockFocus(); selectedCatchUpDateKey = it },
                    selectedGuideCategory  = selectedGuideCategory,
                    selectedMovieCategory  = selectedMovieCategory,
                    selectedSeriesCategory = selectedSeriesCategory,
                    onGuideCategorySelected  = { selectedGuideCategory = it },
                    onMovieCategorySelected  = { movieGridViewRef?.blockFocus(); selectedMovieCategory = it; lastMovieIndex = 0 },
                    onSeriesCategorySelected = { seriesGridViewRef?.blockFocus(); selectedSeriesCategory = it; lastSeriesIndex = 0 },
                    isLoadingVOD     = isLoadingVOD,
                    isLoadingSeries  = isLoadingSeries,
                    xtreamUsername = xtreamPlaylist?.xtreamUsername,
                    xtreamExpiry   = xtreamPlaylist?.xtreamExpiry,
                    isLicensed     = licenceState.isActivated,
                    trialDaysLeft  = settingsViewModel.trialManager.getDaysLeft(),
                    activeProfileName  = activeProfile?.name ?: "Default",
                    activeProfileEmoji = activeProfile?.emoji ?: "👤",
                    onProfileClick = { showProfileSwitch = true },
                    onSearchRequest = {
                        when (currentRoute) {
                            AppRoute.Movies -> selectedMovieCategory = "__search__"
                            AppRoute.Series -> selectedSeriesCategory = "__search__"
                            AppRoute.Guide  -> selectedGuideCategory = "__search__"
                            else -> Unit
                        }
                    },
                    onFavouritesSelected = { route ->
                        when (route) {
                            AppRoute.Movies -> selectedMovieCategory = "__favourites__"
                            AppRoute.Series -> selectedSeriesCategory = "__favourites__"
                            AppRoute.Guide  -> selectedGuideCategory = "__favourites__"
                            else -> Unit
                        }
                    }
                )
            }

            // ── Content zone ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .focusRequester(contentFR)
            ) {
                val epgActive = currentRoute == AppRoute.Guide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (epgActive) 1f else 0f }
                        .then(if (!epgActive) Modifier.focusProperties { canFocus = false } else Modifier)
                        .then(if (!epgActive) Modifier.pointerInput(Unit) {} else Modifier)
                ) {
                    EPGScreen(
                        selectedCategory      = selectedGuideCategory,
                        isContentFocused      = epgActive && zone == Zone.CONTENT,
                        onPlayerVisibilityChanged = { visible -> epgPlayerVisible = visible },
                        onBack                = epgOnBack,
                        onGridFocusRequesterReady = { fn -> epgFocusRequest = fn },
                        onDialogOpen          = { isDialogOpen = it },
                        watchlistIds          = watchlistIds,
                        onToggleChannelWatchlist = { channelId, channelName, logoUrl, streamUrl ->
                            val isCurrentlyBookmarked = channelId in watchlistIds
                            watchlistViewModel.toggleWatchlist(
                                app.nexstream.player.data.local.entity.WatchlistEntity(
                                    id        = channelId,
                                    profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                                    type      = app.nexstream.player.data.local.entity.WatchlistType.CHANNEL,
                                    name      = channelName,
                                    posterUrl = logoUrl,
                                    streamUrl = streamUrl
                                ),
                                isCurrentlyBookmarked
                            )
                        },
                    )
                }

                when (currentRoute) {
                    AppRoute.Movies -> {
                        MoviesScreen(
                            restoreIndex  = lastMovieIndex,
                            restoreTick   = movieRestoreTick,
                            onItemFocused = { lastMovieIndex = it },
                            // onItemFocused only tracks index — focusZone set by EnterContent intent
                            selectedCategory        = selectedMovieCategory,
                            firstItemFocusRequester = null,
                            onMovieClick = { streamUrl, movieId, startPosition, movieName ->
                                currentChannelUrl = streamUrl; currentMovieId = movieId
                                currentNowPlayingTitle = movieName; currentNowPlayingSubtitle = null
                                currentNowPlayingDescription = null; currentEpisodeId = null
                                currentSeriesId = null; currentStartPosition = startPosition
                                showPlayer = true
                                scope.launch {
                                    movieId?.let { repository.getMovieById(it)?.let { movie ->
                                        repository.recordRecentlyWatchedMovie(movie)
                                        currentNowPlayingDescription = movie.plot
                                    }}
                                }
                            },
                            onBack = {
                                if (sidebarPanelExpanded) { zone = Zone.PANEL; panelFocusTick++ }
                                else { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ }
                            },
                            onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                            onRequestSidebarFocus = { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ },
                            onGridViewReady = { movieGridViewRef = it },
                            onContentFocused = { if (zone == Zone.CONTENT) zone = Zone.CONTENT },
                            onDialogOpen = { isDialogOpen = it }
                        )
                    }

                    AppRoute.Series -> {
                        SeriesScreen(
                            restoreIndex  = lastSeriesIndex,
                            restoreTick   = seriesRestoreTick,
                            onItemFocused = { lastSeriesIndex = it },
                            // onItemFocused only tracks index — focusZone set by EnterContent intent
                            selectedCategory        = selectedSeriesCategory,
                            firstItemFocusRequester = null,
                            onPlayEpisode = { streamUrl, episodeId, startPosition, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                                currentChannelUrl = streamUrl; currentEpisodeId = episodeId
                                currentSeriesId = seriesId; currentNowPlayingTitle = seriesName
                                currentNowPlayingSubtitle = "S${seasonNum}E${episodeNum} - $episodeName"
                                currentNowPlayingDescription = null; currentMovieId = null
                                currentStartPosition = startPosition; showPlayer = true
                                scope.launch {
                                    val series = repository.getSeriesById(seriesId)
                                    val episode = repository.getEpisodeById(episodeId)
                                    if (series != null && episode != null) {
                                        repository.recordRecentlyWatchedEpisode(series, episode)
                                        currentNowPlayingDescription = episode.plot ?: series.plot
                                    }
                                }
                            },
                            onBack = {
                                if (sidebarPanelExpanded) { zone = Zone.PANEL; panelFocusTick++ }
                                else { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ }
                            },
                            onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                            onGridViewReady = { seriesGridViewRef = it },
                            onContentFocused = { if (zone == Zone.CONTENT) zone = Zone.CONTENT },
                            onDialogOpen = { isDialogOpen = it },
                            onDownloadEpisode = { streamUrl, title ->
                                app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
                                    context = context,
                                    streamUrl = streamUrl,
                                    title = title,
                                    posterUrl = null
                                )
                            }
                        )
                    }

                    AppRoute.Downloads -> app.nexstream.player.ui.screens.downloads.DownloadsScreen(
                        firstItemFocusRequester = contentFR,
                        onBack = { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ },
                        onPlayFile = { filePath, title ->
                            // DownloadManager returns localUri as "file:///path/to/file"
                            // Strip any existing scheme prefix then rebuild cleanly
                            val cleanPath = filePath
                                .removePrefix("file://")
                                .removePrefix("file:")
                            val fileUri = android.net.Uri.fromFile(java.io.File(cleanPath)).toString()
                            currentChannelUrl            = fileUri
                            currentNowPlayingTitle       = title
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = null
                            currentMovieId               = null
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                        }
                    )

                    AppRoute.Search -> SearchScreen(
                        firstItemFocusRequester = contentFR,
                        onChannelClick = { streamUrl, channelName ->
                            currentChannelUrl            = streamUrl
                            currentNowPlayingTitle       = channelName
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = null
                            currentMovieId               = null
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                            scope.launch {
                                repository.getAllChannels().first()
                                    .firstOrNull { it.streamUrl == streamUrl }
                                    ?.let { repository.recordRecentlyWatchedChannel(it) }
                            }
                        },
                        onMovieClick = { movie ->
                            currentChannelUrl            = movie.streamUrl
                            currentMovieId               = movie.id
                            currentNowPlayingTitle       = movie.name
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = movie.plot
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                            scope.launch { repository.recordRecentlyWatchedMovie(movie) }
                        },
                        onSeriesClick = { _ ->
                            currentRoute = AppRoute.Series
                            closeAllPanels()
                            showSeriesCategories = true
                        }
                    )

                    AppRoute.MyList -> WatchlistScreen(
                        firstItemFocusRequester = contentFR,
                        onChannelClick = { streamUrl, channelName ->
                            currentChannelUrl            = streamUrl
                            currentNowPlayingTitle       = channelName
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = null
                            currentMovieId               = null
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                            scope.launch {
                                repository.getAllChannels().first()
                                    .firstOrNull { it.streamUrl == streamUrl }
                                    ?.let { repository.recordRecentlyWatchedChannel(it) }
                            }
                        },
                        onMovieClick = { item ->
                            item.streamUrl?.let { url ->
                                currentChannelUrl            = url
                                currentMovieId               = item.id
                                currentNowPlayingTitle       = item.name
                                currentNowPlayingSubtitle    = null
                                currentNowPlayingDescription = null
                                currentEpisodeId             = null
                                currentSeriesId              = null
                                currentStartPosition         = 0L
                                showPlayer                   = true
                                scope.launch {
                                    repository.getMovieById(item.id)?.let { movie ->
                                        repository.recordRecentlyWatchedMovie(movie)
                                        currentNowPlayingDescription = movie.plot
                                    }
                                }
                            }
                        },
                        onSeriesClick = { _ ->
                            currentRoute = AppRoute.Series
                            closeAllPanels()
                            showSeriesCategories = true
                        }
                    )

                    AppRoute.CatchUp -> CatchUpScreen(
                        catchUpChannels   = catchUpChannels,
                        onGridViewReady   = { catchUpGridViewRef = it },
                        onContentFocused  = { if (zone == Zone.PANEL || zone == Zone.CONTENT) zone = Zone.CONTENT },
                        onDialogOpen      = { isDialogOpen = it },
                        selectedDateKey   = selectedCatchUpDateKey,
                        restoreTick       = catchUpRestoreTick,
                        onDownloadEpisode = { streamUrl, title ->
                            app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
                                context   = context,
                                streamUrl = streamUrl,
                                title     = title,
                                posterUrl = null
                            )
                        },
                        onPlayTimeshift = { streamUrl, programmeName, subtitle, description, durationMs ->
                            currentChannelUrl            = streamUrl
                            currentNowPlayingTitle       = programmeName
                            currentNowPlayingSubtitle    = subtitle
                            currentNowPlayingDescription = description
                            currentMovieId               = "catchup"
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            currentCatchupDuration       = durationMs
                            showPlayer                   = true
                        }
                    )

                    AppRoute.Recent -> RecentlyWatchedScreen(
                        firstItemFocusRequester = contentFR,
                        onChannelClick = { streamUrl, name ->
                            currentChannelUrl            = streamUrl
                            currentNowPlayingTitle       = name
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = null
                            currentMovieId               = null
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                        },
                        onMovieClick = { item ->
                            currentChannelUrl            = item.streamUrl
                            currentMovieId               = item.movieId
                            currentNowPlayingTitle       = item.name
                            currentNowPlayingSubtitle    = null
                            currentNowPlayingDescription = null
                            currentEpisodeId             = null
                            currentSeriesId              = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                            scope.launch {
                                item.movieId?.let {
                                    repository.getMovieById(it)?.let { movie ->
                                        currentNowPlayingDescription = movie.plot
                                    }
                                }
                            }
                        },
                        onEpisodeClick = { item ->
                            currentChannelUrl            = item.streamUrl
                            currentEpisodeId             = item.episodeId
                            currentSeriesId              = item.seriesId
                            currentNowPlayingTitle       = item.name
                            currentNowPlayingSubtitle    = item.subtitle
                            currentNowPlayingDescription = null
                            currentMovieId               = null
                            currentStartPosition         = 0L
                            showPlayer                   = true
                            scope.launch {
                                val episode = item.episodeId?.let { repository.getEpisodeById(it) }
                                val series  = item.seriesId?.let { repository.getSeriesById(it) }
                                currentNowPlayingDescription = episode?.plot ?: series?.plot
                            }
                        }
                    )

                    AppRoute.SettingsPlaylists -> SettingsScreen(
                        onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                        firstItemFocusRequester = contentFR
                    )
                    AppRoute.SettingsAppearance -> AppearanceScreen(firstItemFocusRequester = contentFR)
                    AppRoute.SettingsLicence    -> LicenceScreen(firstItemFocusRequester = contentFR)
                    AppRoute.SettingsProfiles -> {
                        var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
                        var showEdit by remember { mutableStateOf(false) }
                        if (showEdit) {
                            ProfileEditScreen(
                                existingProfile = editingProfile,
                                onDone = { showEdit = false; editingProfile = null }
                            )
                        } else {
                            ProfilesSettingsScreen(
                                onEditProfile = { profile -> editingProfile = profile; showEdit = true },
                                firstItemFocusRequester = contentFR
                            )
                        }
                    }
                    AppRoute.SettingsPlayer  -> app.nexstream.player.ui.screens.settings.PlayerSettingsScreen(firstItemFocusRequester = contentFR)
                    AppRoute.SettingsAccount -> app.nexstream.player.ui.screens.settings.AccountScreen(firstItemFocusRequester = contentFR)
                    AppRoute.SettingsAbout   -> app.nexstream.player.ui.screens.settings.AboutScreen(firstItemFocusRequester = contentFR)
                    else -> Unit
                }

                // ── Main player (movies / series / catchup) ───────────────────
                if (showPlayer) {
                    key(currentChannelUrl, currentEpisodeId) {
                        PlayerScreen(
                            channelUrl            = currentChannelUrl,
                            movieId               = currentMovieId,
                            episodeId             = currentEpisodeId,
                            seriesId              = currentSeriesId,
                            startPosition         = currentStartPosition,
                            profileId             = activeProfile?.id ?: "default",
                            nowPlayingTitle       = currentNowPlayingTitle,
                            nowPlayingSubtitle    = currentNowPlayingSubtitle,
                            nowPlayingDescription = currentNowPlayingDescription,
                            onPlayNextEpisode = { nextEpisode ->
                                currentChannelUrl         = nextEpisode.streamUrl
                                currentEpisodeId          = nextEpisode.id
                                currentNowPlayingSubtitle = "S${nextEpisode.seasonNum}E${nextEpisode.episodeNum} - ${nextEpisode.name}"
                                currentNowPlayingDescription = null
                                currentStartPosition      = 0L
                                scope.launch {
                                    val series = currentSeriesId?.let { repository.getSeriesById(it) }
                                    currentNowPlayingDescription = nextEpisode.plot ?: series?.plot
                                }
                            },
                            onBack = {
                                val routeAtDismissal = currentRoute
                                playerClosedFromRoute = routeAtDismissal
                                showPlayer = false
                                currentNowPlayingDescription = null
                                // Increment the appropriate restore tick.
                                // Increment the appropriate restore tick.
                                zone = Zone.CONTENT
                                when (routeAtDismissal) {
                                    AppRoute.Movies  -> movieRestoreTick++
                                    AppRoute.Series  -> seriesRestoreTick++
                                    AppRoute.CatchUp -> catchUpRestoreTick++
                                    AppRoute.Guide   -> epgRestoreTick++
                                    else -> Unit
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Scrim overlay on top of blurred content ──────────────────────
        if (scrimAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = scrimAlpha))
            )
        }

        // ── EPG channel player (launched from within EPGScreen) ───────────────
        if (epgPlayerChannel != null) {
            key(epgPlayerChannel!!.streamUrl) {
                PlayerScreen(
                    channelUrl            = epgPlayerChannel!!.streamUrl,
                    movieId               = null,
                    episodeId             = null,
                    seriesId              = null,
                    startPosition         = 0L,
                    nowPlayingTitle       = epgPlayerChannel!!.name,
                    nowPlayingSubtitle    = null,
                    nowPlayingDescription = null,
                    onPlayNextEpisode     = {},
                    handleBackInternally  = false,
                    onBack = {
                        scope.launch { repository.recordRecentlyWatchedChannel(epgPlayerChannel!!) }
                        epgPlayerChannel = null
                    }
                )
            }
        }

    }
}