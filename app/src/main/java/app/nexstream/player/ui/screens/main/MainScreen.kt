package app.nexstream.player.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import app.nexstream.player.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.repository.PlaylistRepository
import app.nexstream.player.ui.components.PosterGridView
import app.nexstream.player.ui.components.Sidebar
import app.nexstream.player.ui.screens.appearance.AppearanceScreen
import app.nexstream.player.ui.screens.catchup.CatchUpScreen
import app.nexstream.player.ui.screens.catchup.catchUpDateLabel
import app.nexstream.player.ui.screens.catchup.todayStart
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
import app.nexstream.player.license.AppAccessState
import app.nexstream.player.ui.screens.settings.LicenceScreen
import app.nexstream.player.ui.screens.settings.SettingsMenuScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistScreen
import app.nexstream.player.ui.screens.watchlist.WatchlistViewModel
import app.nexstream.player.ui.screens.recentlywatched.RecentlyWatchedScreen
import app.nexstream.player.ui.screens.home.HomePageViewModel
import app.nexstream.player.ui.screens.home.ModernHomeScreen
import app.nexstream.player.ui.screens.music.MusicScreen
import app.nexstream.player.ui.screens.music.MusicViewModel
import app.nexstream.player.ui.screens.device.DeviceScreen
import app.nexstream.player.ui.screens.device.DeviceViewModel
import app.nexstream.player.ui.screens.picks.PickItem
import app.nexstream.player.ui.screens.picks.PicksScreen
import app.nexstream.player.ui.screens.picks.PicksViewModel
import app.nexstream.player.ui.screens.reminders.RemindersScreen
import app.nexstream.player.ui.theme.LocalUiStyle
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getStartRouteFlow
import app.nexstream.player.ui.screens.player.ExternalPlayerManager
import app.nexstream.player.ui.theme.getExtPlayerLiveTvFlow
import app.nexstream.player.ui.theme.getExtPlayerMoviesFlow
import app.nexstream.player.ui.theme.getExtPlayerSeriesFlow
import app.nexstream.player.ui.theme.getExtPlayerCatchupFlow
import kotlinx.coroutines.flow.first
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.screens.main.hasCategoryPanel
import app.nexstream.player.ui.screens.main.isSettings
import app.nexstream.player.ui.screens.main.rootSection
import kotlinx.coroutines.launch

enum class Zone { RAIL, PANEL, CONTENT }

private fun String?.asAppRoute(): AppRoute = when (this) {
    "Home"      -> AppRoute.Home
    "Recent"    -> AppRoute.Recent
    "Movies"    -> AppRoute.Movies
    "Series"    -> AppRoute.Series
    "CatchUp"   -> AppRoute.CatchUp
    "Picks"     -> AppRoute.Picks
    "Search"    -> AppRoute.Search
    "MyList"    -> AppRoute.MyList
    "Reminders" -> AppRoute.Reminders
    "Downloads"   -> AppRoute.Downloads
    else          -> AppRoute.Guide
}

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
    watchlistViewModel: WatchlistViewModel = hiltViewModel(),
    homePageViewModel: HomePageViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiStyle = LocalUiStyle.current
    val repository = viewModel.repository
    val isLoadingVOD by repository.isLoadingVOD.collectAsState()
    val isLoadingSeries by repository.isLoadingSeries.collectAsState()
    val scope = rememberCoroutineScope()
    val catchUpViewModel: app.nexstream.player.ui.screens.catchup.CatchUpViewModel = hiltViewModel()
    val catchUpChannels by catchUpViewModel.allCatchUpChannels.collectAsState()
    val isLoadingCatchUp by catchUpViewModel.isLoadingListings.collectAsState()
    val recentlyWatched by repository.getRecentlyWatched().collectAsState(initial = emptyList())
    val allChannels by viewModel.allChannels.collectAsState()

    val picksViewModel: PicksViewModel = hiltViewModel()
    val picksGroups    by picksViewModel.groups.collectAsState()
    val picksCategories = remember(picksGroups) { picksGroups.map { it.seedTitle } }
    var selectedPicksCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val sportsCategories       by homePageViewModel.sportsCategories.collectAsState()
    val selectedSportsCategory by homePageViewModel.selectedSportCategory.collectAsState()
    var pendingMovieGoTo        by remember { mutableStateOf<String?>(null) }
    var pendingSeriesGoTo       by remember { mutableStateOf<String?>(null) }
    var pendingEpgChannelName   by remember { mutableStateOf<String?>(null) }

    var showMovieSearch   by remember { mutableStateOf(false) }
    var showSeriesSearch  by remember { mutableStateOf(false) }
    var showCatchUpSearch by remember { mutableStateOf(false) }
    var showRecentSearch  by remember { mutableStateOf(false) }
    var showMyListSearch  by remember { mutableStateOf(false) }

    LaunchedEffect(recentlyWatched.size) {
        android.util.Log.d("RecentlyWatched", "Count: ${recentlyWatched.size}")
    }

    var currentNowPlayingTitle       by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNowPlayingSubtitle    by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNowPlayingDescription by rememberSaveable { mutableStateOf<String?>(null) }

    val startRouteStored by context.applicationContext.getStartRouteFlow().collectAsState(initial = null)

    var currentRoute  by remember { mutableStateOf<AppRoute>(AppRoute.Guide) }
    var previousRoute by remember { mutableStateOf<AppRoute>(AppRoute.Guide) }
    var startRouteApplied by remember { mutableStateOf(false) }
    LaunchedEffect(startRouteStored) {
        if (!startRouteApplied && startRouteStored != null) {
            startRouteApplied = true
            currentRoute = startRouteStored.asAppRoute()
        }
    }

    var showMovieCategories  by rememberSaveable { mutableStateOf(false) }
    var showSeriesCategories by rememberSaveable { mutableStateOf(false) }
    var showGuideCategories  by rememberSaveable { mutableStateOf(false) }
    var showSettingsMenu     by rememberSaveable { mutableStateOf(false) }

    var selectedMovieCategory   by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSeriesCategory  by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGuideCategory   by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecentType      by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSearchType      by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMyListType      by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRemindersType   by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDownloadsType   by rememberSaveable { mutableStateOf<String?>(null) }
    var showRecentClearConfirm  by remember { mutableStateOf(false) }
    var showMyListClearConfirm  by remember { mutableStateOf(false) }

    val movieCategories  by movieViewModel.getCategories().collectAsState(initial = emptyList())
    val guideCategories  by epgViewModel.getCategories().collectAsState(initial = emptyList())
    val seriesCategories by seriesViewModel.getCategories().collectAsState(initial = emptyList())
    val guideChannelGroups: List<ChannelGroupEntity> by epgViewModel.channelGroups.collectAsState(initial = emptyList<ChannelGroupEntity>())

    var currentChannelUrl    by rememberSaveable { mutableStateOf("") }
    var currentMovieId       by rememberSaveable { mutableStateOf<String?>(null) }
    var currentEpisodeId     by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSeriesId      by rememberSaveable { mutableStateOf<String?>(null) }
    var currentStartPosition by rememberSaveable { mutableStateOf(0L) }
    var showPlayer           by remember { mutableStateOf(false) }
    var epgPlayerVisible     by remember { mutableStateOf(false) }
    var playerClosedFromRoute by remember { mutableStateOf<AppRoute?>(null) }
    var currentCatchupDuration by rememberSaveable { mutableStateOf(0L) }

    var lastMovieIndex  by rememberSaveable { mutableStateOf(0) }
    var lastSeriesIndex by rememberSaveable { mutableStateOf(0) }
    var selectedCatchUpChannel  by remember { mutableStateOf<ChannelEntity?>(null) }
    var selectedCatchUpDateKey  by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(catchUpChannels.size) {
        if (selectedCatchUpChannel == null && catchUpChannels.isNotEmpty()) {
            selectedCatchUpChannel = catchUpChannels.first()
            selectedCatchUpDateKey = null
        }
    }
    val catchUpAvailableDates by catchUpViewModel.availableDates.collectAsState()

    var epgFocusRequest  by remember { mutableStateOf<(() -> Unit)?>(null) }
    var epgPlayerChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    var movieRestoreTick  by remember { mutableStateOf(0) }
    var seriesRestoreTick by remember { mutableStateOf(0) }
    var homeRestoreTick   by remember { mutableStateOf(0) }
    var movieGridViewRef   by remember { mutableStateOf<PosterGridView?>(null) }
    var seriesGridViewRef  by remember { mutableStateOf<PosterGridView?>(null) }
    var catchUpGridViewRef by remember { mutableStateOf<PosterGridView?>(null) }
    var catchUpRestoreTick by remember { mutableStateOf(0) }
    var epgRestoreTick     by remember { mutableStateOf(0) }

    val settingsViewModel: app.nexstream.player.ui.screens.settings.SettingsViewModel = hiltViewModel()
    val licenceViewModel:  app.nexstream.player.ui.screens.settings.LicenceViewModel  = hiltViewModel()
    val settingsPlaylists by settingsViewModel.playlists.collectAsState()
    val licenceState      by licenceViewModel.uiState.collectAsState()
    val accessState       by licenceViewModel.accessState.collectAsState()
    val vodRestricted     = accessState == AppAccessState.TRIAL_EXPIRED
    val xtreamPlaylist = remember(settingsPlaylists) { settingsPlaylists.firstOrNull { it.type == "XTREAM" } }
    val hasJellyfinPlaylist = remember(settingsPlaylists) { settingsPlaylists.any { it.type == "JELLYFIN" } }
    LaunchedEffect(startRouteApplied) {
        if (startRouteApplied) {
            val loaded = settingsViewModel.playlists.first { it.isNotEmpty() }
            settingsViewModel.checkPlaylistConnectivity(loaded)
        }
    }
    val hasDeviceFolders by deviceViewModel.hasDeviceFolders.collectAsState()
    val deviceDates      by deviceViewModel.deviceDates.collectAsState()
    var selectedDeviceDate by rememberSaveable { mutableStateOf<String?>(null) }
    val musicArtists by musicViewModel.artists.collectAsState()
    val musicCategories = remember(musicArtists) {
        buildList { add("Queue"); addAll(musicArtists) }
    }
    var selectedMusicCategory by remember { mutableStateOf<String?>(null) }
    val watchlistIds   by watchlistViewModel.watchlistIds.collectAsState()
    val activeProfile  by watchlistViewModel.profileManager.activeProfile.collectAsState()
    val allProfiles    by watchlistViewModel.profileManager.profiles.collectAsState()

    // Channel prev/next navigation (live TV only)
    val isLiveChannelPlaying = currentMovieId == null && currentEpisodeId == null && showPlayer
    val liveChannelIndex = if (isLiveChannelPlaying) allChannels.indexOfFirst { it.streamUrl == currentChannelUrl } else -1
    val onPreviousChannelNav: (() -> Unit)? = if (isLiveChannelPlaying && liveChannelIndex > 0) ({
        val prev = allChannels[liveChannelIndex - 1]
        currentChannelUrl = prev.streamUrl
        currentNowPlayingTitle = prev.name
        currentNowPlayingSubtitle = null
        currentNowPlayingDescription = null
    }) else null
    val onNextChannelNav: (() -> Unit)? = if (isLiveChannelPlaying && liveChannelIndex in 0 until allChannels.lastIndex) ({
        val next = allChannels[liveChannelIndex + 1]
        currentChannelUrl = next.streamUrl
        currentNowPlayingTitle = next.name
        currentNowPlayingSubtitle = null
        currentNowPlayingDescription = null
    }) else null

    LaunchedEffect(pendingPlayUrl) {
        if (pendingPlayUrl != null) {
            currentChannelUrl = pendingPlayUrl; currentNowPlayingTitle = pendingPlayName
            currentNowPlayingSubtitle = null; currentNowPlayingDescription = null
            currentMovieId = null; currentEpisodeId = null; currentSeriesId = null
            currentStartPosition = 0L; showPlayer = true
            onPendingPlayConsumed()
        }
    }

    var sidebarPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var sidebarExpandedRoute by remember { mutableStateOf<AppRoute?>(null) }

    fun closeAllPanels() {
        showGuideCategories = false; showMovieCategories = false
        showSeriesCategories = false; showSettingsMenu = false
        sidebarPanelExpanded = false; sidebarExpandedRoute = null
    }

    var showExitDialog    by remember { mutableStateOf(false) }
    var showProfileSwitch by remember { mutableStateOf(false) }
    var isDialogOpen      by remember { mutableStateOf(false) }
    var profilesNavTick   by remember { mutableStateOf(0) }

    val railFR    = remember { FocusRequester() }
    val panelFR   = remember { FocusRequester() }
    val contentFR = remember { FocusRequester() }
    var guideStripFocusTick by remember { mutableStateOf(0) }
    var subStripFocusTick   by remember { mutableStateOf(0) }

    val today = remember { todayStart() }
    val catchUpDateLabels = remember(catchUpAvailableDates, today) {
        catchUpAvailableDates.map { catchUpDateLabel(it, today) }
    }
    val selectedCatchUpLabel = remember(selectedCatchUpDateKey, catchUpAvailableDates, catchUpDateLabels) {
        val key = selectedCatchUpDateKey ?: return@remember null
        val ms  = key.toLongOrNull() ?: return@remember null
        val idx = catchUpAvailableDates.indexOf(ms)
        if (idx >= 0) catchUpDateLabels.getOrNull(idx) else null
    }
    val isReseller = viewModel.isReseller
    val settingsSubCategories: List<String> = remember {
        buildList {
            add("Playlists"); add("Appearance"); add("Player")
            add("Sync")
            add("Licence"); add("Account"); add("Profiles"); add("Navigation"); add("Language"); add("Network"); add("About")
        }
    }
    val settingsLabelToRoute: Map<String, AppRoute> = remember { mapOf(
        "Playlists"  to AppRoute.SettingsPlaylists,
        "Appearance" to AppRoute.SettingsAppearance,
        "Player"     to AppRoute.SettingsPlayer,
        "Sync"       to AppRoute.SettingsSyncSettings,
        "Licence"    to AppRoute.SettingsLicence,
        "Account"    to AppRoute.SettingsAccount,
        "Profiles"   to AppRoute.SettingsProfiles,
        "Navigation" to AppRoute.SettingsNavigation,
        "Language"   to AppRoute.SettingsLanguage,
        "Network"    to AppRoute.SettingsProxy,
        "About"      to AppRoute.SettingsAbout,
    ) }
    val selectedSettingsLabel = remember(currentRoute) {
        settingsLabelToRoute.entries.firstOrNull { (_, route) -> route == currentRoute }?.key
    }

    var zone               by remember { mutableStateOf(Zone.RAIL) }
    var sidebarRefocusTick by remember { mutableStateOf(0) }
    var panelFocusTick     by remember { mutableStateOf(0) }

    LaunchedEffect(vodRestricted) {
        if (vodRestricted && (currentRoute == AppRoute.Movies || currentRoute == AppRoute.Series || currentRoute == AppRoute.CatchUp)) {
            currentRoute = AppRoute.Guide
            sidebarPanelExpanded = false
            sidebarExpandedRoute = null
            zone = Zone.RAIL
        }
    }

    fun focusContent() {
        if (uiStyle == UiStyle.MODERN) {
            when (currentRoute) {
                AppRoute.Guide -> epgFocusRequest?.invoke()
                    ?: try { contentFR.requestFocus() } catch (_: Exception) {}
                else -> try { contentFR.requestFocus() } catch (_: Exception) {}
            }
            return
        }
        when (currentRoute) {
            AppRoute.Guide -> epgFocusRequest?.invoke()
                ?: try { contentFR.requestFocus() } catch (_: Exception) {}
            AppRoute.Movies -> scope.launch {
                if (movieGridViewRef?.requestItemFocusNow(lastMovieIndex) != true) {
                    movieGridViewRef?.scrollToIndexTop(lastMovieIndex)
                    kotlinx.coroutines.delay(150); movieGridViewRef?.requestItemFocus(lastMovieIndex)
                }
            }
            AppRoute.Series -> scope.launch {
                if (seriesGridViewRef?.requestItemFocusNow(lastSeriesIndex) != true) {
                    seriesGridViewRef?.scrollToIndexTop(lastSeriesIndex)
                    kotlinx.coroutines.delay(150); seriesGridViewRef?.requestItemFocus(lastSeriesIndex)
                }
            }
            AppRoute.CatchUp -> scope.launch {
                if (catchUpGridViewRef?.requestItemFocusNow(0) != true) {
                    kotlinx.coroutines.delay(150); catchUpGridViewRef?.requestItemFocus(0)
                }
            }
            else -> try { contentFR.requestFocus() } catch (_: Exception) {}
        }
    }

    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute) {
        // Category cleanup — same for both UI modes
        if (previousRoute == AppRoute.Movies && currentRoute != AppRoute.Movies) { selectedMovieCategory = null; lastMovieIndex = 0; pendingMovieGoTo = null }
        if (previousRoute == AppRoute.Series && currentRoute != AppRoute.Series) { selectedSeriesCategory = null; lastSeriesIndex = 0; pendingSeriesGoTo = null }
        if (previousRoute == AppRoute.CatchUp && currentRoute != AppRoute.CatchUp) { selectedCatchUpDateKey = null }
        if (previousRoute == AppRoute.Music && currentRoute != AppRoute.Music) { selectedMusicCategory = null; musicViewModel.selectArtist(null) }
        // Always dismiss search keyboard when changing routes
        showMovieSearch = false; showSeriesSearch = false
        showCatchUpSearch = false; showRecentSearch = false; showMyListSearch = false
        previousRoute = currentRoute

        if (uiStyle == UiStyle.MODERN) {
            if (isFirstLoad) {
                isFirstLoad = false; zone = Zone.RAIL
            }
            // When a top-nav pill is pressed (zone = RAIL), the new route's category strip
            // may enter composition and receive auto-focus. Explicitly reclaim focus for
            // the top nav so the strip never hijacks it on Enter.
            if (zone == Zone.RAIL) {
                sidebarRefocusTick++
            }
            return@LaunchedEffect
        }

        // ── Classic mode ──────────────────────────────────────────────────────
        val isSettingsSubRoute = currentRoute.isSettings
                && currentRoute != AppRoute.Settings
                && previousRoute.isSettings

        val isSilentGoTo = (currentRoute == AppRoute.Movies && pendingMovieGoTo != null) ||
                           (currentRoute == AppRoute.Series && pendingSeriesGoTo != null)

        if (isFirstLoad) {
            isFirstLoad = false; zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null
            android.util.Log.d("NexStreamFocus", "startup route=$currentRoute zone=RAIL panel=CLOSED")
        } else if (isSilentGoTo) {
            // Panel open so Back returns to "All" category; grid LaunchedEffect drives focus
            sidebarPanelExpanded = true; sidebarExpandedRoute = currentRoute; zone = Zone.CONTENT
            android.util.Log.d("NexStreamFocus", "silentGoTo route=$currentRoute zone=CONTENT panel=OPEN")
        } else if (currentRoute.hasCategoryPanel) {
            movieGridViewRef?.blockFocus(); seriesGridViewRef?.blockFocus(); catchUpGridViewRef?.blockFocus()
            sidebarPanelExpanded = true
            sidebarExpandedRoute = if (currentRoute.isSettings) AppRoute.Settings else currentRoute
            if (!isSettingsSubRoute) {
                zone = Zone.PANEL
                android.util.Log.d("NexStreamFocus", "navigate route=$currentRoute zone=PANEL panel=OPEN")
                kotlinx.coroutines.delay(50); panelFocusTick++
            } else {
                android.util.Log.d("NexStreamFocus", "settings sub-route=$currentRoute zone=unchanged panel=OPEN")
            }
        } else {
            zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null
            android.util.Log.d("NexStreamFocus", "navigate route=$currentRoute zone=RAIL panel=CLOSED")
        }
    }

    LaunchedEffect(movieCategories.size) {
        if (uiStyle == UiStyle.CLASSIC && zone == Zone.PANEL && currentRoute == AppRoute.Movies && movieCategories.isNotEmpty()) panelFocusTick++
    }
    LaunchedEffect(seriesCategories.size) {
        if (uiStyle == UiStyle.CLASSIC && zone == Zone.PANEL && currentRoute == AppRoute.Series && seriesCategories.isNotEmpty()) panelFocusTick++
    }
    LaunchedEffect(guideCategories.size) {
        if (uiStyle == UiStyle.CLASSIC && zone == Zone.PANEL && currentRoute == AppRoute.Guide && guideCategories.isNotEmpty()) panelFocusTick++
    }

    var initialFocusGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300); initialFocusGranted = true
        kotlinx.coroutines.delay(32); sidebarRefocusTick++
    }

    BackHandler(enabled = epgPlayerChannel != null) {
        scope.launch { epgPlayerChannel?.let { repository.recordRecentlyWatchedChannel(it) } }
        epgPlayerChannel = null
    }
    // Modern: content → category strip → top nav (two-step back)
    BackHandler(enabled = uiStyle == UiStyle.MODERN && zone != Zone.RAIL && !isDialogOpen) {
        if (zone == Zone.CONTENT) {
            if (currentRoute == AppRoute.Movies) { lastMovieIndex = 0; movieGridViewRef?.scrollToIndex(0); pendingMovieGoTo = null }
            if (currentRoute == AppRoute.Series) { lastSeriesIndex = 0; seriesGridViewRef?.scrollToIndex(0); pendingSeriesGoTo = null }
            when {
                currentRoute == AppRoute.Guide && guideCategories.isNotEmpty() -> { zone = Zone.PANEL; guideStripFocusTick++ }
                currentRoute.hasCategoryPanel -> { zone = Zone.PANEL; subStripFocusTick++ }
                else -> { zone = Zone.RAIL; sidebarRefocusTick++ }
            }
        } else {
            zone = Zone.RAIL; sidebarRefocusTick++
        }
    }
    // Classic: panel → rail
    BackHandler(enabled = uiStyle == UiStyle.CLASSIC && zone == Zone.PANEL && sidebarPanelExpanded) {
        zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++
    }
    // Classic: content → panel or rail
    BackHandler(enabled = uiStyle == UiStyle.CLASSIC && zone == Zone.CONTENT && !isDialogOpen) {
        if (currentRoute == AppRoute.Movies) { lastMovieIndex = 0; movieGridViewRef?.scrollToIndex(0); pendingMovieGoTo = null }
        if (currentRoute == AppRoute.Series) { lastSeriesIndex = 0; seriesGridViewRef?.scrollToIndex(0); pendingSeriesGoTo = null }
        movieGridViewRef?.blockFocus(); seriesGridViewRef?.blockFocus(); catchUpGridViewRef?.blockFocus()
        if (selectedMovieCategory == "__favourites__") selectedMovieCategory = null
        if (selectedSeriesCategory == "__favourites__") selectedSeriesCategory = null
        showMovieSearch = false; showSeriesSearch = false
        showCatchUpSearch = false; showRecentSearch = false; showMyListSearch = false
        if (currentRoute.hasCategoryPanel) {
            if (!sidebarPanelExpanded) {
                sidebarPanelExpanded = true
                sidebarExpandedRoute = if (currentRoute.isSettings) AppRoute.Settings else currentRoute
            }
            zone = Zone.PANEL; panelFocusTick++
        } else if (sidebarPanelExpanded) {
            zone = Zone.PANEL; panelFocusTick++
        } else {
            zone = Zone.RAIL; sidebarRefocusTick++
        }
    }
    BackHandler(enabled = zone == Zone.RAIL) { showExitDialog = true }

    LaunchedEffect(epgRestoreTick) {
        if (epgRestoreTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(200); zone = Zone.CONTENT; epgFocusRequest?.invoke()
    }
    LaunchedEffect(movieRestoreTick) {
        if (movieRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT; kotlinx.coroutines.delay(100)
        if (movieGridViewRef?.requestItemFocusNow(lastMovieIndex) != true) {
            movieGridViewRef?.scrollToIndexTop(lastMovieIndex)
            kotlinx.coroutines.delay(200); movieGridViewRef?.requestItemFocus(lastMovieIndex)
        }
    }
    LaunchedEffect(seriesRestoreTick) {
        if (seriesRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT; kotlinx.coroutines.delay(100)
        if (seriesGridViewRef?.requestItemFocusNow(lastSeriesIndex) != true) {
            seriesGridViewRef?.scrollToIndexTop(lastSeriesIndex)
            kotlinx.coroutines.delay(200); seriesGridViewRef?.requestItemFocus(lastSeriesIndex)
        }
    }
    LaunchedEffect(catchUpRestoreTick) {
        if (catchUpRestoreTick == 0) return@LaunchedEffect
        zone = Zone.CONTENT; kotlinx.coroutines.delay(100)
        if (catchUpGridViewRef?.requestItemFocusNow(0) != true) {
            kotlinx.coroutines.delay(200); catchUpGridViewRef?.requestItemFocus(0)
        }
    }

    val epgOnBack: () -> Unit = {
        if (uiStyle == UiStyle.MODERN) {
            // Modern: step back to guide category strip first (if categories exist), then to top nav
            if (guideCategories.isNotEmpty()) { zone = Zone.PANEL; guideStripFocusTick++ }
            else { zone = Zone.RAIL; sidebarRefocusTick++ }
        } else {
            if (sidebarPanelExpanded) { zone = Zone.PANEL; panelFocusTick++ }
            else { zone = Zone.RAIL; sidebarPanelExpanded = false; sidebarExpandedRoute = null; sidebarRefocusTick++ }
        }
    }

    // ── Shared callbacks (defined once, referenced in both Modern + Classic layouts) ──
    val sharedOnEpgPlayerVisibleChange: (Boolean) -> Unit = { epgPlayerVisible = it }
    val sharedOnEpgFocusRequestReady: ((() -> Unit)?) -> Unit = { epgFocusRequest = it }
    val sharedOnDialogOpen: (Boolean) -> Unit = { isDialogOpen = it }
    val sharedOnPendingEpgChannelConsumed: () -> Unit = { pendingEpgChannelName = null }
    val sharedOnMovieIndexChange: (Int) -> Unit = { lastMovieIndex = it }
    val sharedOnMovieGridViewReady: (PosterGridView?) -> Unit = { movieGridViewRef = it }
    val sharedOnMovieCategoryChange: (String?) -> Unit = { selectedMovieCategory = it }
    val sharedOnSilentMovieFilterConsumed: () -> Unit = { pendingMovieGoTo = null }
    val sharedOnKeyboardMovieDismissed: () -> Unit = {
        showMovieSearch = false
        scope.launch {
            zone = Zone.CONTENT
            if (movieGridViewRef?.requestItemFocusNow(0) != true) {
                kotlinx.coroutines.delay(100); movieGridViewRef?.requestItemFocus(0)
            }
        }
    }
    val sharedOnKeyboardMovieDismissedEmpty: () -> Unit = {
        showMovieSearch = false
        scope.launch { zone = Zone.PANEL; kotlinx.coroutines.delay(60); panelFocusTick++ }
    }
    val sharedOnSeriesIndexChange: (Int) -> Unit = { lastSeriesIndex = it }
    val sharedOnSeriesGridViewReady: (PosterGridView?) -> Unit = { seriesGridViewRef = it }
    val sharedOnSeriesCategoryChange: (String?) -> Unit = { selectedSeriesCategory = it }
    val sharedOnSilentSeriesFilterConsumed: () -> Unit = { pendingSeriesGoTo = null }
    val sharedOnKeyboardSeriesDismissed: () -> Unit = {
        showSeriesSearch = false
        scope.launch {
            zone = Zone.CONTENT
            if (seriesGridViewRef?.requestItemFocusNow(0) != true) {
                kotlinx.coroutines.delay(100); seriesGridViewRef?.requestItemFocus(0)
            }
        }
    }
    val sharedOnKeyboardSeriesDismissedEmpty: () -> Unit = {
        showSeriesSearch = false
        scope.launch { zone = Zone.PANEL; kotlinx.coroutines.delay(60); panelFocusTick++ }
    }
    val sharedOnDownloadEpisode: (String, String) -> Unit = { url, title ->
        app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
            context = context, streamUrl = url, title = title,
            posterUrl = null, profileId = activeProfile?.id ?: "default"
        )
    }
    val sharedOnCatchUpGridViewReady: (PosterGridView?) -> Unit = { catchUpGridViewRef = it }
    val sharedOnCatchUpDateChange: (String?) -> Unit = { catchUpGridViewRef?.blockFocus(); selectedCatchUpDateKey = it }
    val sharedOnKeyboardCatchUpDismissed: () -> Unit = {
        showCatchUpSearch = false
        scope.launch {
            zone = Zone.CONTENT
            if (catchUpGridViewRef?.requestItemFocusNow(0) != true) {
                kotlinx.coroutines.delay(100); catchUpGridViewRef?.requestItemFocus(0)
            }
        }
    }
    val sharedOnKeyboardCatchUpDismissedEmpty: () -> Unit = {
        showCatchUpSearch = false
        scope.launch { zone = Zone.PANEL; kotlinx.coroutines.delay(60); panelFocusTick++ }
    }
    val sharedOnDownloadCatchUp: (String, String) -> Unit = { url, title ->
        app.nexstream.player.downloads.NexStreamDownloadManager.startDownload(
            context = context, streamUrl = url, title = title,
            posterUrl = null, profileId = activeProfile?.id ?: "default"
        )
    }
    val sharedOnMyListClearDismissed: () -> Unit = { showMyListClearConfirm = false }
    val sharedOnRecentClearDismissed: () -> Unit = { showRecentClearConfirm = false }
    val sharedOnNavigateToRoute: (AppRoute) -> Unit = { currentRoute = it }
    val sharedOnTogglePicksWatchlist: (app.nexstream.player.ui.screens.picks.PickItem, Boolean) -> Unit = { pick, isBookmarked ->
        val type = if (pick.mediaType == "movie") app.nexstream.player.data.local.entity.WatchlistType.MOVIE
                   else app.nexstream.player.data.local.entity.WatchlistType.SERIES
        watchlistViewModel.toggleWatchlist(
            app.nexstream.player.data.local.entity.WatchlistEntity(
                id = pick.tmdbId.toString(), profileId = activeProfile?.id ?: "default",
                type = type, name = pick.title, posterUrl = pick.posterUrl, streamUrl = null
            ), isBookmarked
        )
    }
    val extPlayerMovies  by context.getExtPlayerMoviesFlow().collectAsState(initial = "nexstream")
    val extPlayerSeries  by context.getExtPlayerSeriesFlow().collectAsState(initial = "nexstream")
    val extPlayerCatchup by context.getExtPlayerCatchupFlow().collectAsState(initial = "nexstream")
    val extPlayerLiveTv  by context.getExtPlayerLiveTvFlow().collectAsState(initial = "nexstream")

    val sharedOnPlayerLaunch: (String, String?, String?, String?, Long, String?, String?, String?) -> Unit = { url, movieId, episodeId, seriesId, startPos, title, subtitle, description ->
        val isMovie  = movieId != null && movieId != "catchup" && episodeId == null
        val isSeries = episodeId != null
        val extPkg = when {
            isMovie  -> extPlayerMovies
            isSeries -> extPlayerSeries
            else     -> "nexstream"
        }
        if (extPkg != "nexstream" && extPkg.isNotEmpty() &&
            ExternalPlayerManager.launch(context, extPkg, url, title)) {
            // launched externally — nothing to do
        } else {
            currentChannelUrl = url; currentMovieId = movieId; currentEpisodeId = episodeId
            currentSeriesId = seriesId; currentStartPosition = startPos
            currentNowPlayingTitle = title; currentNowPlayingSubtitle = subtitle
            currentNowPlayingDescription = description; showPlayer = true
        }
    }
    val sharedOnPlayerBack: (AppRoute) -> Unit = { fromRoute ->
        playerClosedFromRoute = fromRoute; showPlayer = false
        currentNowPlayingDescription = null; zone = Zone.CONTENT
        when (fromRoute) {
            AppRoute.Movies  -> movieRestoreTick++
            AppRoute.Series  -> seriesRestoreTick++
            AppRoute.CatchUp -> catchUpRestoreTick++
            AppRoute.Guide   -> epgRestoreTick++
            AppRoute.Home    -> homeRestoreTick++
            else -> Unit
        }
    }
    val sharedOnNowPlayingDescriptionChange: (String?) -> Unit = { currentNowPlayingDescription = it }
    val sharedOnPlayNextEpisode: (app.nexstream.player.data.local.entity.EpisodeEntity) -> Unit = { nextEpisode ->
        currentChannelUrl = nextEpisode.streamUrl; currentEpisodeId = nextEpisode.id
        currentNowPlayingSubtitle = "S${nextEpisode.seasonNum}E${nextEpisode.episodeNum} - ${nextEpisode.name}"
        currentNowPlayingDescription = null; currentStartPosition = 0L
        scope.launch {
            val series = currentSeriesId?.let { repository.getSeriesById(it) }
            currentNowPlayingDescription = nextEpisode.plot ?: series?.plot
        }
    }
    val sharedOnCatchUpPlay: (String, String, String?, String?, Long) -> Unit = { url, name, subtitle, description, durationMs ->
        if (extPlayerCatchup != "nexstream" && extPlayerCatchup.isNotEmpty() &&
            ExternalPlayerManager.launch(context, extPlayerCatchup, url, name)) {
            // launched externally
        } else {
            currentChannelUrl = url; currentNowPlayingTitle = name
            currentNowPlayingSubtitle = subtitle; currentNowPlayingDescription = description
            currentMovieId = "catchup"; currentEpisodeId = null; currentSeriesId = null
            currentStartPosition = 0L; currentCatchupDuration = durationMs; showPlayer = true
        }
    }
    val sharedOnZoneContent: () -> Unit = { zone = Zone.CONTENT }
    val sharedOnSearchChannelPlay: (String, String) -> Unit = { url, name ->
        currentChannelUrl = url; currentNowPlayingTitle = name
        currentNowPlayingSubtitle = null; currentNowPlayingDescription = null
        currentMovieId = null; currentEpisodeId = null; currentSeriesId = null
        currentStartPosition = 0L; showPlayer = true
        scope.launch {
            repository.getAllChannels().first()
                .firstOrNull { it.streamUrl == url }
                ?.let { repository.recordRecentlyWatchedChannel(it) }
        }
    }
    val sharedOnSearchMoviePlay: (app.nexstream.player.data.local.entity.MovieEntity) -> Unit = { movie ->
        currentChannelUrl = movie.streamUrl; currentMovieId = movie.id
        currentNowPlayingTitle = movie.name; currentNowPlayingSubtitle = null
        currentNowPlayingDescription = movie.plot; currentEpisodeId = null
        currentSeriesId = null; currentStartPosition = 0L; showPlayer = true
        scope.launch { repository.recordRecentlyWatchedMovie(movie) }
    }
    val sharedOnWatchlistChannelPlay: (String, String) -> Unit = { url, name ->
        currentChannelUrl = url; currentNowPlayingTitle = name
        currentNowPlayingSubtitle = null; currentNowPlayingDescription = null
        currentMovieId = null; currentEpisodeId = null; currentSeriesId = null
        currentStartPosition = 0L; showPlayer = true
        scope.launch {
            repository.getAllChannels().first()
                .firstOrNull { it.streamUrl == url }
                ?.let { repository.recordRecentlyWatchedChannel(it) }
        }
    }
    val sharedOnRecentChannelPlay: (String, String) -> Unit = { url, name ->
        currentChannelUrl = url; currentNowPlayingTitle = name
        currentNowPlayingSubtitle = null; currentNowPlayingDescription = null
        currentMovieId = null; currentEpisodeId = null; currentSeriesId = null
        currentStartPosition = 0L; showPlayer = true
    }
    val sharedOnEpgFocusUp: () -> Unit = { guideStripFocusTick++ }

    if (showExitDialog) {
        ExitDialog(
            onDismiss = { showExitDialog = false },
            onExit    = { (context as? android.app.Activity)?.finish() }
        )
    }

    if (showProfileSwitch) {
        app.nexstream.player.ui.screens.profile.ProfileSwitchDialog(
            profiles        = allProfiles,
            activeProfileId = activeProfile?.id,
            onProfileSelected = { profile ->
                showProfileSwitch = false
                scope.launch { watchlistViewModel.profileManager.setActiveProfile(profile) }
                currentRoute = startRouteStored.asAppRoute(); sidebarPanelExpanded = false; sidebarExpandedRoute = null
                selectedMovieCategory = null; selectedSeriesCategory = null; selectedGuideCategory = null
                scope.launch { kotlinx.coroutines.delay(200); zone = Zone.RAIL; sidebarRefocusTick++ }
            },
            onDismiss = { showProfileSwitch = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val anyDialogOpen = isDialogOpen || showProfileSwitch || showExitDialog
        val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (anyDialogOpen) 0.55f else 0f,
            animationSpec = androidx.compose.animation.core.tween(if (anyDialogOpen) 250 else 0),
            label = "dialogScrim"
        )

        if (uiStyle == UiStyle.MODERN) {
            ModernMainLayout(
                showPlayer = showPlayer, epgPlayerVisible = epgPlayerVisible,
                currentRoute = currentRoute, zone = zone, contentFR = contentFR,
                sidebarRefocusTick = sidebarRefocusTick, guideStripFocusTick = guideStripFocusTick,
                subStripFocusTick = subStripFocusTick,
                activeProfileEmoji = activeProfile?.emoji ?: "👤",
                activeProfileName = activeProfile?.name ?: "Default",
                guideCategories = guideCategories, selectedGuideCategory = selectedGuideCategory,
                movieCategories = movieCategories, selectedMovieCategory = selectedMovieCategory,
                seriesCategories = seriesCategories, selectedSeriesCategory = selectedSeriesCategory,
                catchUpAvailableDates = catchUpAvailableDates, catchUpDateLabels = catchUpDateLabels,
                selectedCatchUpLabel = selectedCatchUpLabel, selectedSearchType = selectedSearchType,
                selectedMyListType = selectedMyListType, selectedDownloadsType = selectedDownloadsType,
                settingsSubCategories = settingsSubCategories, selectedSettingsLabel = selectedSettingsLabel,
                settingsLabelToRoute = settingsLabelToRoute, epgFocusRequest = epgFocusRequest,
                epgOnBack = epgOnBack, watchlistIds = watchlistIds,
                pendingEpgChannelName = pendingEpgChannelName, lastMovieIndex = lastMovieIndex,
                homeRestoreTick = homeRestoreTick, movieRestoreTick = movieRestoreTick,
                showMovieSearch = showMovieSearch, pendingMovieGoTo = pendingMovieGoTo,
                lastSeriesIndex = lastSeriesIndex, seriesRestoreTick = seriesRestoreTick,
                showSeriesSearch = showSeriesSearch, pendingSeriesGoTo = pendingSeriesGoTo,
                catchUpChannels = catchUpChannels, selectedCatchUpDateKey = selectedCatchUpDateKey,
                showCatchUpSearch = showCatchUpSearch, catchUpRestoreTick = catchUpRestoreTick,
                showMyListSearch = showMyListSearch, showMyListClearConfirm = showMyListClearConfirm,
                selectedRecentType = selectedRecentType, showRecentSearch = showRecentSearch,
                showRecentClearConfirm = showRecentClearConfirm, selectedPicksCategory = selectedPicksCategory,
                selectedMusicCategory = selectedMusicCategory,
                selectedDeviceDate    = selectedDeviceDate,
                hasJellyfinPlaylist = hasJellyfinPlaylist,
                currentChannelUrl = currentChannelUrl, currentMovieId = currentMovieId,
                currentEpisodeId = currentEpisodeId, currentSeriesId = currentSeriesId,
                currentStartPosition = currentStartPosition, currentCatchupDuration = currentCatchupDuration,
                currentNowPlayingTitle = currentNowPlayingTitle,
                currentNowPlayingSubtitle = currentNowPlayingSubtitle,
                currentNowPlayingDescription = currentNowPlayingDescription,
                activeProfileId = activeProfile?.id ?: "default",
                picksViewModel = picksViewModel, watchlistViewModel = watchlistViewModel,
                repository = repository, context = context, scope = scope,
                setZone = { zone = it }, incrSidebarRefocusTick = { sidebarRefocusTick++ },
                incrGuideStripFocusTick = { guideStripFocusTick++ },
                incrSubStripFocusTick = { subStripFocusTick++ },
                setCurrentRoute = { currentRoute = it },
                setSelectedGuideCategory = { selectedGuideCategory = it },
                setSelectedMovieCategory = { selectedMovieCategory = it },
                setSelectedSeriesCategory = { selectedSeriesCategory = it },
                setSelectedCatchUpDateKey = { selectedCatchUpDateKey = it },
                setSelectedSearchType = { selectedSearchType = it },
                setSelectedMyListType = { selectedMyListType = it; showMyListSearch = false },
                setSelectedDownloadsType = { selectedDownloadsType = it },
                setShowProfileSwitch = { showProfileSwitch = it },
                setPendingMovieGoTo = { pendingMovieGoTo = it },
                setShowMovieSearch = { showMovieSearch = it },
                setPendingSeriesGoTo = { pendingSeriesGoTo = it },
                setShowSeriesSearch = { showSeriesSearch = it },
                setPendingEpgChannelName = { pendingEpgChannelName = it },
                focusContent = { focusContent() },
                onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                onPreviousChannel = onPreviousChannelNav, onNextChannel = onNextChannelNav,
                onEpgPlayerVisibleChange = sharedOnEpgPlayerVisibleChange,
                onEpgFocusRequestReady = sharedOnEpgFocusRequestReady,
                onDialogOpen = sharedOnDialogOpen,
                onPendingEpgChannelConsumed = sharedOnPendingEpgChannelConsumed,
                onMovieIndexChange = sharedOnMovieIndexChange,
                onMovieGridViewReady = sharedOnMovieGridViewReady,
                onMovieCategoryChange = sharedOnMovieCategoryChange,
                onSilentMovieFilterConsumed = sharedOnSilentMovieFilterConsumed,
                onKeyboardMovieDismissed = sharedOnKeyboardMovieDismissed,
                onKeyboardMovieDismissedEmpty = sharedOnKeyboardMovieDismissedEmpty,
                onSeriesIndexChange = sharedOnSeriesIndexChange,
                onSeriesGridViewReady = sharedOnSeriesGridViewReady,
                onSeriesCategoryChange = sharedOnSeriesCategoryChange,
                onSilentSeriesFilterConsumed = sharedOnSilentSeriesFilterConsumed,
                onKeyboardSeriesDismissed = sharedOnKeyboardSeriesDismissed,
                onKeyboardSeriesDismissedEmpty = sharedOnKeyboardSeriesDismissedEmpty,
                onDownloadEpisode = sharedOnDownloadEpisode,
                onCatchUpGridViewReady = sharedOnCatchUpGridViewReady,
                onCatchUpDateChange = sharedOnCatchUpDateChange,
                onKeyboardCatchUpDismissed = sharedOnKeyboardCatchUpDismissed,
                onKeyboardCatchUpDismissedEmpty = sharedOnKeyboardCatchUpDismissedEmpty,
                onDownloadCatchUp = sharedOnDownloadCatchUp,
                onMyListClearDismissed = sharedOnMyListClearDismissed,
                onRecentClearDismissed = sharedOnRecentClearDismissed,
                onNavigateToRoute = sharedOnNavigateToRoute,
                onTogglePicksWatchlist = sharedOnTogglePicksWatchlist,
                onPlayerLaunch = sharedOnPlayerLaunch, onPlayerBack = sharedOnPlayerBack,
                onNowPlayingDescriptionChange = sharedOnNowPlayingDescriptionChange,
                onPlayNextEpisode = sharedOnPlayNextEpisode,
                onCatchUpPlay = sharedOnCatchUpPlay, onZoneContent = sharedOnZoneContent,
                onSearchChannelPlay = sharedOnSearchChannelPlay,
                onSearchMoviePlay = sharedOnSearchMoviePlay,
                onWatchlistChannelPlay = sharedOnWatchlistChannelPlay,
                onRecentChannelPlay = sharedOnRecentChannelPlay,
                onRefreshSports = { homePageViewModel.refreshSports() },
                isRefreshingSports = homePageViewModel.isLoadingSports.collectAsState().value,
                incrProfilesNavTick = { profilesNavTick++ },
            )
        } else {
            // ── Classic layout: sidebar rail + content ────────────────────────
            ClassicMainLayout(
                currentRoute          = currentRoute,
                zone                  = zone,
                epgPlayerVisible      = epgPlayerVisible,
                showPlayer            = showPlayer,
                sidebarPanelExpanded  = sidebarPanelExpanded,
                sidebarExpandedRoute  = sidebarExpandedRoute,
                initialFocusGranted   = initialFocusGranted,
                railFR                = railFR,
                panelFR               = panelFR,
                contentFR             = contentFR,
                sidebarRefocusTick    = sidebarRefocusTick,
                panelFocusTick        = panelFocusTick,
                showGuideCategories   = showGuideCategories,
                showMovieCategories   = showMovieCategories,
                showSeriesCategories  = showSeriesCategories,
                showSettingsMenu      = showSettingsMenu,
                guideCategories       = guideCategories,
                movieCategories       = movieCategories,
                seriesCategories      = seriesCategories,
                catchUpAvailableDates = catchUpAvailableDates,
                catchUpChannels       = catchUpChannels,
                selectedCatchUpChannel = selectedCatchUpChannel,
                selectedCatchUpDateKey = selectedCatchUpDateKey,
                selectedGuideCategory  = selectedGuideCategory,
                selectedMovieCategory  = selectedMovieCategory,
                selectedSeriesCategory = selectedSeriesCategory,
                isLoadingVOD          = isLoadingVOD,
                isLoadingSeries       = isLoadingSeries,
                isLoadingCatchUp      = isLoadingCatchUp,
                selectedRecentType    = selectedRecentType,
                selectedSearchType    = selectedSearchType,
                selectedMyListType    = selectedMyListType,
                selectedRemindersType = selectedRemindersType,
                selectedDownloadsType = selectedDownloadsType,
                picksCategories       = picksCategories,
                selectedPicksCategory = selectedPicksCategory,
                sportsCategories      = sportsCategories,
                selectedSportsCategory = selectedSportsCategory,
                hasJellyfinPlaylist   = hasJellyfinPlaylist,
                hasDeviceFolders      = hasDeviceFolders,
                deviceDates           = deviceDates,
                selectedDeviceDate    = selectedDeviceDate,
                onDeviceDateSelected  = { selectedDeviceDate = it },
                musicCategories       = musicCategories,
                selectedMusicCategory = selectedMusicCategory,
                onMusicCategorySelected = { cat ->
                    selectedMusicCategory = cat
                    musicViewModel.selectArtist(if (cat == "Favourites" || cat == "Queue") null else cat)
                },
                xtreamUsername        = xtreamPlaylist?.xtreamUsername,
                xtreamExpiry          = xtreamPlaylist?.xtreamExpiry,
                isLicensed            = licenceState.isActivated,
                trialDaysLeft         = settingsViewModel.trialManager.getDaysLeft(),
                vodRestricted         = vodRestricted,
                activeProfileName     = activeProfile?.name ?: "Default",
                activeProfileEmoji    = activeProfile?.emoji ?: "👤",
                showSyncSettings     = true,
                channelGroups         = guideChannelGroups,
                selectedSettingsRoute = if (currentRoute.isSettings && currentRoute != AppRoute.Settings) currentRoute else null,
                watchlistIds          = watchlistIds,
                pendingEpgChannelName = pendingEpgChannelName,
                epgFocusRequest       = epgFocusRequest,
                lastMovieIndex        = lastMovieIndex,
                movieRestoreTick      = movieRestoreTick,
                showMovieSearch       = showMovieSearch,
                pendingMovieGoTo      = pendingMovieGoTo,
                lastSeriesIndex       = lastSeriesIndex,
                seriesRestoreTick     = seriesRestoreTick,
                showSeriesSearch      = showSeriesSearch,
                pendingSeriesGoTo     = pendingSeriesGoTo,
                showCatchUpSearch     = showCatchUpSearch,
                catchUpRestoreTick    = catchUpRestoreTick,
                showMyListSearch      = showMyListSearch,
                showMyListClearConfirm = showMyListClearConfirm,
                showRecentSearch      = showRecentSearch,
                showRecentClearConfirm = showRecentClearConfirm,
                homeRestoreTick       = homeRestoreTick,
                currentChannelUrl     = currentChannelUrl,
                currentMovieId        = currentMovieId,
                currentEpisodeId      = currentEpisodeId,
                currentSeriesId       = currentSeriesId,
                currentStartPosition  = currentStartPosition,
                currentCatchupDuration = currentCatchupDuration,
                currentNowPlayingTitle       = currentNowPlayingTitle,
                currentNowPlayingSubtitle    = currentNowPlayingSubtitle,
                currentNowPlayingDescription = currentNowPlayingDescription,
                activeProfileId       = activeProfile?.id ?: "default",
                // Setters
                setZone                  = { zone = it },
                incrPanelFocusTick       = { panelFocusTick++ },
                incrSidebarRefocusTick   = { sidebarRefocusTick++ },
                setCurrentRoute          = { currentRoute = it },
                setSidebarPanelExpanded  = { sidebarPanelExpanded = it },
                setSidebarExpandedRoute  = { sidebarExpandedRoute = it },
                setSelectedGuideCategory = { selectedGuideCategory = it },
                setSelectedMovieCategory = { selectedMovieCategory = it },
                setSelectedSeriesCategory = { selectedSeriesCategory = it },
                setSelectedCatchUpChannel = { selectedCatchUpChannel = it },
                setSelectedCatchUpDateKey = { selectedCatchUpDateKey = it },
                setSelectedRecentType    = { selectedRecentType = it },
                setSelectedSearchType    = { selectedSearchType = it },
                setSelectedMyListType    = { selectedMyListType = it },
                setSelectedRemindersType = { selectedRemindersType = it },
                setSelectedDownloadsType = { selectedDownloadsType = it },
                setSelectedPicksCategory = { selectedPicksCategory = it },
                setLastMovieIndex        = { lastMovieIndex = it },
                setLastSeriesIndex       = { lastSeriesIndex = it },
                setShowRecentClearConfirm = { showRecentClearConfirm = it },
                setShowMyListClearConfirm = { showMyListClearConfirm = it },
                setShowSeriesCategories  = { showSeriesCategories = it },
                setPendingMovieGoTo      = { pendingMovieGoTo = it },
                setPendingSeriesGoTo     = { pendingSeriesGoTo = it },
                setPendingEpgChannelName = { pendingEpgChannelName = it },
                setShowMovieSearch       = { showMovieSearch = it },
                setShowSeriesSearch      = { showSeriesSearch = it },
                setShowCatchUpSearch     = { showCatchUpSearch = it },
                setShowRecentSearch      = { showRecentSearch = it },
                setShowMyListSearch      = { showMyListSearch = it },
                setShowProfileSwitch     = { showProfileSwitch = it },
                onBlockCatchUpFocus      = { catchUpGridViewRef?.blockFocus() },
                onBlockMovieFocus        = { movieGridViewRef?.blockFocus() },
                onBlockSeriesFocus       = { seriesGridViewRef?.blockFocus() },
                onSelectSportCategory    = { homePageViewModel.selectSportCategory(it) },
                focusContent             = { focusContent() },
                closeAllPanels           = { closeAllPanels() },
                // Objects
                picksViewModel     = picksViewModel,
                watchlistViewModel = watchlistViewModel,
                homePageViewModel  = homePageViewModel,
                repository         = repository,
                context            = context,
                scope              = scope,
                // Shared callbacks
                onNavigateToAddPlaylist  = onNavigateToAddPlaylist,
                onEpgPlayerVisibleChange    = sharedOnEpgPlayerVisibleChange,
                onEpgFocusRequestReady      = sharedOnEpgFocusRequestReady,
                onDialogOpen                = sharedOnDialogOpen,
                onMovieGridViewReady        = sharedOnMovieGridViewReady,
                onSilentMovieFilterConsumed = sharedOnSilentMovieFilterConsumed,
                onKeyboardMovieDismissed    = sharedOnKeyboardMovieDismissed,
                onKeyboardMovieDismissedEmpty = sharedOnKeyboardMovieDismissedEmpty,
                onSeriesGridViewReady       = sharedOnSeriesGridViewReady,
                onSilentSeriesFilterConsumed = sharedOnSilentSeriesFilterConsumed,
                onKeyboardSeriesDismissed   = sharedOnKeyboardSeriesDismissed,
                onKeyboardSeriesDismissedEmpty = sharedOnKeyboardSeriesDismissedEmpty,
                onDownloadEpisode           = sharedOnDownloadEpisode,
                onCatchUpGridViewReady           = sharedOnCatchUpGridViewReady,
                onDownloadCatchUp                = sharedOnDownloadCatchUp,
                onKeyboardCatchUpDismissed       = sharedOnKeyboardCatchUpDismissed,
                onKeyboardCatchUpDismissedEmpty  = sharedOnKeyboardCatchUpDismissedEmpty,
                onMyListClearDismissed           = sharedOnMyListClearDismissed,
                onRecentClearDismissed      = sharedOnRecentClearDismissed,
                onTogglePicksWatchlist      = sharedOnTogglePicksWatchlist,
                onPlayerLaunch              = sharedOnPlayerLaunch,
                onPlayerBack                = sharedOnPlayerBack,
                onNowPlayingDescriptionChange = sharedOnNowPlayingDescriptionChange,
                onPlayNextEpisode           = sharedOnPlayNextEpisode,
                onPreviousChannel           = onPreviousChannelNav,
                onNextChannel               = onNextChannelNav,
                onCatchUpPlay               = sharedOnCatchUpPlay,
                onSearchChannelPlay         = sharedOnSearchChannelPlay,
                onSearchMoviePlay           = sharedOnSearchMoviePlay,
                onWatchlistChannelPlay      = sharedOnWatchlistChannelPlay,
                onRecentChannelPlay         = sharedOnRecentChannelPlay,
                onEpgFocusUp                = sharedOnEpgFocusUp,
                onPendingEpgChannelConsumed = sharedOnPendingEpgChannelConsumed,
                onCatchUpDateChange         = sharedOnCatchUpDateChange,
                onMovieIndexChange          = sharedOnMovieIndexChange,
                onMovieCategoryChange       = sharedOnMovieCategoryChange,
                onSeriesIndexChange         = sharedOnSeriesIndexChange,
                onSeriesCategoryChange      = sharedOnSeriesCategoryChange,
                onZoneContent               = sharedOnZoneContent,
                onNavigateToRoute           = sharedOnNavigateToRoute,
                onEpgOnBack                 = epgOnBack,
            )
        }

        if (scrimAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = scrimAlpha)))
        }

        if (epgPlayerChannel != null) {
            key(epgPlayerChannel!!.streamUrl) {
                PlayerScreen(
                    channelUrl            = epgPlayerChannel!!.streamUrl,
                    movieId               = null, episodeId = null, seriesId = null,
                    startPosition         = 0L,
                    nowPlayingTitle       = epgPlayerChannel!!.name,
                    nowPlayingSubtitle    = null, nowPlayingDescription = null,
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

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("LongParameterList")
@Composable
private fun ModernNavAndStrips(
    currentRoute: AppRoute,
    zone: Zone,
    onNavigate: (AppRoute) -> Unit,
    onDropToContent: () -> Unit,
    navFocusTick: Int,
    activeProfileEmoji: String,
    activeProfileName: String,
    onProfileClick: () -> Unit,
    guideCategories: List<String>,
    selectedGuideCategory: String?,
    onSelectGuideCategory: (String?) -> Unit,
    guideStripFocusTick: Int,
    movieCategories: List<String>,
    selectedMovieCategory: String?,
    onSelectMovieCategory: (String?) -> Unit,
    seriesCategories: List<String>,
    selectedSeriesCategory: String?,
    onSelectSeriesCategory: (String?) -> Unit,
    catchUpAvailableDates: List<Long>,
    catchUpDateLabels: List<String>,
    selectedCatchUpLabel: String?,
    onSelectCatchUpDate: (String?) -> Unit,
    selectedSearchType: String?,
    onSelectSearchType: (String?) -> Unit,
    selectedMyListType: String?,
    onSelectMyListType: (String?) -> Unit,
    selectedDownloadsType: String?,
    onSelectDownloadsType: (String?) -> Unit,
    settingsSubCategories: List<String>,
    selectedSettingsLabel: String?,
    settingsLabelToRoute: Map<String, AppRoute>,
    onSelectSettingsLabel: (String?) -> Unit,
    subStripFocusTick: Int,
    onFocusUp: () -> Unit,
    onFocusDown: () -> Unit,
    onBack: (() -> Unit)? = null,
    onStopPlayer: () -> Unit = {},
) {
    val routeHasStrip = when {
        currentRoute == AppRoute.Guide && guideCategories.isNotEmpty() -> true
        currentRoute == AppRoute.Movies && movieCategories.isNotEmpty() -> true
        currentRoute == AppRoute.Series && seriesCategories.isNotEmpty() -> true
        currentRoute == AppRoute.CatchUp && catchUpAvailableDates.isNotEmpty() -> true
        currentRoute == AppRoute.Search  -> true
        currentRoute == AppRoute.MyList  -> true
        currentRoute == AppRoute.Downloads -> true
        currentRoute.isSettings          -> true
        else -> false
    }
    val showTopNav = zone == Zone.RAIL || !routeHasStrip
    if (showTopNav) {
        ModernTopNav(
            currentRoute       = currentRoute,
            onNavigate         = onNavigate,
            onDropToContent    = onDropToContent,
            focusTick          = navFocusTick,
            activeProfileEmoji = activeProfileEmoji,
            activeProfileName  = activeProfileName,
            onProfileClick     = onProfileClick,
            onStopPlayer       = onStopPlayer,
        )
    } else {
        when {
            currentRoute == AppRoute.Guide && guideCategories.isNotEmpty() -> ModernCategoryStrip(
                categories       = guideCategories,
                selected         = selectedGuideCategory,
                onSelect         = onSelectGuideCategory,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = guideStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.Movies && movieCategories.isNotEmpty() -> ModernCategoryStrip(
                categories       = movieCategories,
                selected         = selectedMovieCategory,
                onSelect         = onSelectMovieCategory,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.Series && seriesCategories.isNotEmpty() -> ModernCategoryStrip(
                categories       = seriesCategories,
                selected         = selectedSeriesCategory,
                onSelect         = onSelectSeriesCategory,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.CatchUp && catchUpAvailableDates.isNotEmpty() -> ModernCategoryStrip(
                categories       = catchUpDateLabels,
                selected         = selectedCatchUpLabel,
                onSelect         = onSelectCatchUpDate,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.Search -> ModernCategoryStrip(
                categories       = listOf("Live TV", "Movies", "Series", "People"),
                selected         = selectedSearchType,
                onSelect         = onSelectSearchType,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.MyList -> ModernCategoryStrip(
                categories       = listOf("Live TV", "Movies", "Series", "Reminders"),
                selected         = selectedMyListType,
                onSelect         = onSelectMyListType,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute == AppRoute.Downloads -> ModernCategoryStrip(
                categories       = listOf("Active", "Completed", "Failed"),
                selected         = selectedDownloadsType,
                onSelect         = onSelectDownloadsType,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                focusTick        = subStripFocusTick,
                showLeadingLogo  = true,
                onBack           = onBack,
            )
            currentRoute.isSettings -> ModernCategoryStrip(
                categories       = settingsSubCategories,
                selected         = selectedSettingsLabel,
                onSelect         = onSelectSettingsLabel,
                onFocusUp        = onFocusUp,
                onFocusDown      = onFocusDown,
                showLeadingLogo  = true,
                focusTick        = subStripFocusTick,
                onBack           = onBack,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ClassicMainLayout(
    // Read-only state
    currentRoute: AppRoute,
    zone: Zone,
    epgPlayerVisible: Boolean,
    showPlayer: Boolean,
    sidebarPanelExpanded: Boolean,
    sidebarExpandedRoute: AppRoute?,
    initialFocusGranted: Boolean,
    railFR: FocusRequester,
    panelFR: FocusRequester,
    contentFR: FocusRequester,
    sidebarRefocusTick: Int,
    panelFocusTick: Int,
    showGuideCategories: Boolean,
    showMovieCategories: Boolean,
    showSeriesCategories: Boolean,
    showSettingsMenu: Boolean,
    guideCategories: List<String>,
    movieCategories: List<String>,
    seriesCategories: List<String>,
    catchUpAvailableDates: List<Long>,
    catchUpChannels: List<ChannelEntity>,
    selectedCatchUpChannel: ChannelEntity?,
    selectedCatchUpDateKey: String?,
    selectedGuideCategory: String?,
    selectedMovieCategory: String?,
    selectedSeriesCategory: String?,
    isLoadingVOD: Boolean,
    isLoadingSeries: Boolean,
    isLoadingCatchUp: Boolean,
    selectedRecentType: String?,
    selectedSearchType: String?,
    selectedMyListType: String?,
    selectedRemindersType: String?,
    selectedDownloadsType: String?,
    picksCategories: List<String>,
    selectedPicksCategory: String?,
    sportsCategories: List<String>,
    selectedSportsCategory: String?,
    hasJellyfinPlaylist: Boolean,
    hasDeviceFolders: Boolean,
    deviceDates: List<String>,
    selectedDeviceDate: String?,
    onDeviceDateSelected: (String?) -> Unit,
    musicCategories: List<String>,
    selectedMusicCategory: String?,
    onMusicCategorySelected: (String?) -> Unit,
    xtreamUsername: String?,
    xtreamExpiry: String?,
    isLicensed: Boolean,
    trialDaysLeft: Int,
    vodRestricted: Boolean,
    activeProfileName: String,
    activeProfileEmoji: String,
    showSyncSettings: Boolean,
    channelGroups: List<ChannelGroupEntity>,
    selectedSettingsRoute: AppRoute?,
    watchlistIds: Set<String>,
    pendingEpgChannelName: String?,
    epgFocusRequest: (() -> Unit)?,
    lastMovieIndex: Int,
    movieRestoreTick: Int,
    showMovieSearch: Boolean,
    pendingMovieGoTo: String?,
    lastSeriesIndex: Int,
    seriesRestoreTick: Int,
    showSeriesSearch: Boolean,
    pendingSeriesGoTo: String?,
    showCatchUpSearch: Boolean,
    catchUpRestoreTick: Int,
    showMyListSearch: Boolean,
    showMyListClearConfirm: Boolean,
    showRecentSearch: Boolean,
    showRecentClearConfirm: Boolean,
    homeRestoreTick: Int,
    currentChannelUrl: String,
    currentMovieId: String?,
    currentEpisodeId: String?,
    currentSeriesId: String?,
    currentStartPosition: Long,
    currentCatchupDuration: Long,
    currentNowPlayingTitle: String?,
    currentNowPlayingSubtitle: String?,
    currentNowPlayingDescription: String?,
    activeProfileId: String,
    // Setters
    setZone: (Zone) -> Unit,
    incrPanelFocusTick: () -> Unit,
    incrSidebarRefocusTick: () -> Unit,
    setCurrentRoute: (AppRoute) -> Unit,
    setSidebarPanelExpanded: (Boolean) -> Unit,
    setSidebarExpandedRoute: (AppRoute?) -> Unit,
    setSelectedGuideCategory: (String?) -> Unit,
    setSelectedMovieCategory: (String?) -> Unit,
    setSelectedSeriesCategory: (String?) -> Unit,
    setSelectedCatchUpChannel: (ChannelEntity?) -> Unit,
    setSelectedCatchUpDateKey: (String?) -> Unit,
    setSelectedRecentType: (String?) -> Unit,
    setSelectedSearchType: (String?) -> Unit,
    setSelectedMyListType: (String?) -> Unit,
    setSelectedRemindersType: (String?) -> Unit,
    setSelectedDownloadsType: (String?) -> Unit,
    setSelectedPicksCategory: (String?) -> Unit,
    setLastMovieIndex: (Int) -> Unit,
    setLastSeriesIndex: (Int) -> Unit,
    setShowRecentClearConfirm: (Boolean) -> Unit,
    setShowMyListClearConfirm: (Boolean) -> Unit,
    setShowSeriesCategories: (Boolean) -> Unit,
    setPendingMovieGoTo: (String?) -> Unit,
    setPendingSeriesGoTo: (String?) -> Unit,
    setPendingEpgChannelName: (String?) -> Unit,
    setShowMovieSearch: (Boolean) -> Unit,
    setShowSeriesSearch: (Boolean) -> Unit,
    setShowCatchUpSearch: (Boolean) -> Unit,
    setShowRecentSearch: (Boolean) -> Unit,
    setShowMyListSearch: (Boolean) -> Unit,
    setShowProfileSwitch: (Boolean) -> Unit,
    onBlockCatchUpFocus: () -> Unit,
    onBlockMovieFocus: () -> Unit,
    onBlockSeriesFocus: () -> Unit,
    onSelectSportCategory: (String?) -> Unit,
    focusContent: () -> Unit,
    closeAllPanels: () -> Unit,
    // Objects
    picksViewModel: PicksViewModel,
    watchlistViewModel: WatchlistViewModel,
    homePageViewModel: HomePageViewModel,
    repository: PlaylistRepository,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    // Shared callbacks
    onNavigateToAddPlaylist: () -> Unit,
    onEpgPlayerVisibleChange: (Boolean) -> Unit,
    onEpgFocusRequestReady: ((() -> Unit)?) -> Unit,
    onDialogOpen: (Boolean) -> Unit,
    onMovieGridViewReady: (PosterGridView?) -> Unit,
    onSilentMovieFilterConsumed: () -> Unit,
    onKeyboardMovieDismissed: () -> Unit,
    onKeyboardMovieDismissedEmpty: () -> Unit = {},
    onSeriesGridViewReady: (PosterGridView?) -> Unit,
    onSilentSeriesFilterConsumed: () -> Unit,
    onKeyboardSeriesDismissed: () -> Unit,
    onKeyboardSeriesDismissedEmpty: () -> Unit = {},
    onDownloadEpisode: (String, String) -> Unit,
    onCatchUpGridViewReady: (PosterGridView?) -> Unit,
    onDownloadCatchUp: (String, String) -> Unit,
    onKeyboardCatchUpDismissed: () -> Unit,
    onKeyboardCatchUpDismissedEmpty: () -> Unit = {},
    onMyListClearDismissed: () -> Unit,
    onRecentClearDismissed: () -> Unit,
    onTogglePicksWatchlist: (PickItem, Boolean) -> Unit,
    onPlayerLaunch: (String, String?, String?, String?, Long, String?, String?, String?) -> Unit,
    onPlayerBack: (AppRoute) -> Unit,
    onNowPlayingDescriptionChange: (String?) -> Unit,
    onPlayNextEpisode: (EpisodeEntity) -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    onCatchUpPlay: (String, String, String?, String?, Long) -> Unit,
    onSearchChannelPlay: (String, String) -> Unit,
    onSearchMoviePlay: (MovieEntity) -> Unit,
    onWatchlistChannelPlay: (String, String) -> Unit,
    onRecentChannelPlay: (String, String) -> Unit,
    onEpgFocusUp: () -> Unit,
    onPendingEpgChannelConsumed: () -> Unit,
    onCatchUpDateChange: (String?) -> Unit,
    onMovieIndexChange: (Int) -> Unit,
    onMovieCategoryChange: (String?) -> Unit,
    onSeriesIndexChange: (Int) -> Unit,
    onSeriesCategoryChange: (String?) -> Unit,
    onZoneContent: () -> Unit,
    onNavigateToRoute: (AppRoute) -> Unit,
    onEpgOnBack: () -> Unit,
) {
    // Compose complex Sidebar callbacks from setters
    val onPanelExpandedChange: (Boolean, AppRoute?) -> Unit = { expanded, route ->
        setSidebarPanelExpanded(expanded); setSidebarExpandedRoute(route)
        if (expanded) onBlockCatchUpFocus()
    }
    val onBackToMainMenu: () -> Unit = {
        closeAllPanels()
        setSelectedGuideCategory(null); setSelectedMovieCategory(null); setSelectedSeriesCategory(null)
        try { contentFR.requestFocus() } catch (_: Exception) {}
    }
    val onSearchRequest: () -> Unit = {
        when (currentRoute) {
            AppRoute.Movies  -> setShowMovieSearch(true)
            AppRoute.Series  -> setShowSeriesSearch(true)
            AppRoute.CatchUp -> setShowCatchUpSearch(true)
            AppRoute.Recent  -> setShowRecentSearch(true)
            AppRoute.MyList  -> setShowMyListSearch(true)
            AppRoute.Guide   -> setSelectedGuideCategory("__search__")
            else -> Unit
        }
    }
    val onFavouritesSelected: (AppRoute) -> Unit = { route ->
        when (route) {
            AppRoute.Movies -> setSelectedMovieCategory("__favourites__")
            AppRoute.Series -> setSelectedSeriesCategory("__favourites__")
            AppRoute.Guide  -> setSelectedGuideCategory("__favourites__")
            AppRoute.Music  -> onMusicCategorySelected("Favourites")
            else -> Unit
        }
    }

    // Classic-specific MainContentArea callbacks
    val classicOnGoToMovie: (String) -> Unit = { name ->
        setPendingMovieGoTo(name); setShowMovieSearch(false)
        setSelectedMovieCategory(null); closeAllPanels()
        setSidebarPanelExpanded(true); setSidebarExpandedRoute(AppRoute.Movies)
        setCurrentRoute(AppRoute.Movies); setZone(Zone.CONTENT)
    }
    val classicOnGoToSeries: (String) -> Unit = { name ->
        setPendingSeriesGoTo(name); setShowSeriesSearch(false)
        setSelectedSeriesCategory(null); closeAllPanels()
        setSidebarPanelExpanded(true); setSidebarExpandedRoute(AppRoute.Series)
        setCurrentRoute(AppRoute.Series); setZone(Zone.CONTENT)
    }
    val classicOnGoToEpgForChannel: (String) -> Unit = { channelName ->
        setPendingEpgChannelName(channelName); setCurrentRoute(AppRoute.Guide)
        setSelectedGuideCategory(null); closeAllPanels()
        setZone(Zone.CONTENT)
        scope.launch { kotlinx.coroutines.delay(300); focusContent() }
    }
    val classicOnPickSelected: (PickItem) -> Unit = { pick ->
        if (pick.mediaType == "movie") {
            setPendingMovieGoTo(pick.title); setShowMovieSearch(false)
            setSelectedMovieCategory(null); closeAllPanels()
            setSidebarPanelExpanded(true); setSidebarExpandedRoute(AppRoute.Movies)
            setCurrentRoute(AppRoute.Movies); setZone(Zone.CONTENT)
        } else {
            setPendingSeriesGoTo(pick.title); setShowSeriesSearch(false)
            setSelectedSeriesCategory(null); closeAllPanels()
            setSidebarPanelExpanded(true); setSidebarExpandedRoute(AppRoute.Series)
            setCurrentRoute(AppRoute.Series); setZone(Zone.CONTENT)
        }
    }
    val classicOnPanelBack: () -> Unit = { setZone(Zone.PANEL); incrPanelFocusTick() }
    val classicOnRailBack: () -> Unit = {
        setZone(Zone.RAIL); setSidebarPanelExpanded(false); setSidebarExpandedRoute(null); incrSidebarRefocusTick()
    }
    val classicOnSearchSeriesClick: () -> Unit = {
        setCurrentRoute(AppRoute.Series); closeAllPanels(); setShowSeriesCategories(true)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        val sidebarVisible = !epgPlayerVisible && !showPlayer
        Box(
            modifier = Modifier
                .then(if (sidebarVisible) Modifier.wrapContentWidth() else Modifier.width(0.dp))
                .fillMaxHeight()
                .graphicsLayer { alpha = if (sidebarVisible) 1f else 0f }
                .then(if (!sidebarVisible || !initialFocusGranted || zone == Zone.CONTENT) Modifier.focusProperties { canFocus = false } else Modifier)
        ) {
            Sidebar(
                currentRoute          = currentRoute,
                panelExpanded         = sidebarPanelExpanded,
                contentActive         = zone == Zone.CONTENT,
                expandedRoute         = sidebarExpandedRoute,
                onPanelExpandedChange = onPanelExpandedChange,
                railFR             = railFR,
                panelFR            = panelFR,
                sidebarRefocusTick = sidebarRefocusTick,
                panelFocusTick     = panelFocusTick,
                onRailFocusChanged  = { hasFocus ->
                    if (hasFocus) { if (zone == Zone.CONTENT) scope.launch { focusContent() } else setZone(Zone.RAIL) }
                },
                onPanelFocusChanged = { hasFocus ->
                    if (hasFocus) { if (zone == Zone.CONTENT) scope.launch { focusContent() } else setZone(Zone.PANEL) }
                },
                onEnterPanel      = { setZone(Zone.PANEL); incrPanelFocusTick() },
                onEnterContent    = { setZone(Zone.CONTENT); focusContent() },
                onExitPanelToRail = { setZone(Zone.RAIL); setSidebarPanelExpanded(false); setSidebarExpandedRoute(null); incrSidebarRefocusTick() },
                onNavigate        = { route -> setCurrentRoute(route) },
                onBackToMainMenu  = onBackToMainMenu,
                selectedSettingsRoute  = selectedSettingsRoute,
                onSettingsItemSelected = { route -> setCurrentRoute(route) },
                showGuideCategories    = showGuideCategories,
                showMovieCategories    = showMovieCategories,
                showSeriesCategories   = showSeriesCategories,
                showSettingsMenu       = showSettingsMenu,
                guideCategories        = guideCategories,
                movieCategories        = movieCategories,
                seriesCategories       = seriesCategories,
                catchUpAvailableDates  = catchUpAvailableDates,
                catchUpChannels        = catchUpChannels,
                selectedCatchUpChannel = selectedCatchUpChannel,
                onCatchUpChannelSelected = { ch -> setSelectedCatchUpChannel(ch); setSelectedCatchUpDateKey(null) },
                selectedCatchUpDateKey   = selectedCatchUpDateKey,
                onCatchUpDateSelected    = { onBlockCatchUpFocus(); setSelectedCatchUpDateKey(it) },
                selectedGuideCategory    = selectedGuideCategory,
                selectedMovieCategory    = selectedMovieCategory,
                selectedSeriesCategory   = selectedSeriesCategory,
                onGuideCategorySelected  = { setSelectedGuideCategory(it) },
                onMovieCategorySelected  = { onBlockMovieFocus(); setSelectedMovieCategory(it); setLastMovieIndex(0) },
                onSeriesCategorySelected = { onBlockSeriesFocus(); setSelectedSeriesCategory(it); setLastSeriesIndex(0) },
                isLoadingVOD             = isLoadingVOD,
                isLoadingSeries          = isLoadingSeries,
                isLoadingCatchUp         = isLoadingCatchUp,
                selectedRecentType       = selectedRecentType,
                onRecentTypeSelected     = { setSelectedRecentType(it) },
                selectedSearchType       = selectedSearchType,
                onSearchTypeSelected     = { setSelectedSearchType(it) },
                selectedMyListType       = selectedMyListType,
                onMyListTypeSelected     = { setSelectedMyListType(it) },
                selectedRemindersType    = selectedRemindersType,
                onRemindersTypeSelected  = { setSelectedRemindersType(it) },
                selectedDownloadsType    = selectedDownloadsType,
                onDownloadsTypeSelected  = { setSelectedDownloadsType(it) },
                picksCategories          = picksCategories,
                selectedPicksCategory    = selectedPicksCategory,
                onPicksCategorySelected  = { setSelectedPicksCategory(it) },
                sportsCategories         = sportsCategories,
                selectedSportsCategory   = selectedSportsCategory,
                onSportsCategorySelected = { onSelectSportCategory(it) },
                hasJellyfinPlaylist      = hasJellyfinPlaylist,
                hasDeviceFolders         = hasDeviceFolders,
                deviceDates              = deviceDates,
                selectedDeviceDate       = selectedDeviceDate,
                onDeviceDateSelected     = onDeviceDateSelected,
                musicCategories          = musicCategories,
                selectedMusicCategory    = selectedMusicCategory,
                onMusicCategorySelected  = onMusicCategorySelected,
                onRecentClearAll         = { setShowRecentClearConfirm(true) },
                onMyListClearAll         = { if (selectedMyListType != "Reminders") setShowMyListClearConfirm(true) },
                xtreamUsername     = xtreamUsername,
                xtreamExpiry       = xtreamExpiry,
                isLicensed         = isLicensed,
                trialDaysLeft      = trialDaysLeft,
                vodRestricted      = vodRestricted,
                activeProfileName  = activeProfileName,
                activeProfileEmoji = activeProfileEmoji,
                onProfileClick     = { setShowProfileSwitch(true) },
                onSearchRequest    = onSearchRequest,
                onFavouritesSelected = onFavouritesSelected,
                showSyncSettings   = showSyncSettings,
                channelGroups      = channelGroups,
            )
        }

        MainContentArea(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(contentFR),
            currentRoute          = currentRoute,
            zone                  = zone,
            contentFR             = contentFR,
            sidebarPanelExpanded  = sidebarPanelExpanded,
            epgPlayerVisible      = epgPlayerVisible,
            selectedGuideCategory = selectedGuideCategory,
            epgFocusRequest       = epgFocusRequest,
            watchlistIds          = watchlistIds,
            pendingEpgChannelName = pendingEpgChannelName,
            epgOnBack             = onEpgOnBack,
            onEpgFocusUp          = onEpgFocusUp,
            homeRestoreTick       = homeRestoreTick,
            lastMovieIndex        = lastMovieIndex,
            movieRestoreTick      = movieRestoreTick,
            selectedMovieCategory = selectedMovieCategory,
            showMovieSearch       = showMovieSearch,
            pendingMovieGoTo      = pendingMovieGoTo,
            lastSeriesIndex       = lastSeriesIndex,
            seriesRestoreTick     = seriesRestoreTick,
            selectedSeriesCategory = selectedSeriesCategory,
            showSeriesSearch      = showSeriesSearch,
            pendingSeriesGoTo     = pendingSeriesGoTo,
            catchUpChannels       = catchUpChannels,
            selectedCatchUpDateKey = selectedCatchUpDateKey,
            showCatchUpSearch     = showCatchUpSearch,
            catchUpRestoreTick    = catchUpRestoreTick,
            selectedDownloadsType = selectedDownloadsType,
            selectedSearchType    = selectedSearchType,
            selectedMyListType    = selectedMyListType,
            showMyListSearch      = showMyListSearch,
            showMyListClearConfirm = showMyListClearConfirm,
            selectedRecentType    = selectedRecentType,
            showRecentSearch      = showRecentSearch,
            showRecentClearConfirm = showRecentClearConfirm,
            selectedPicksCategory = selectedPicksCategory,
            selectedMusicCategory = selectedMusicCategory,
            selectedDeviceDate    = selectedDeviceDate,
            hasJellyfinPlaylist   = hasJellyfinPlaylist,
            showPlayer            = showPlayer,
            currentChannelUrl     = currentChannelUrl,
            currentMovieId        = currentMovieId,
            currentEpisodeId      = currentEpisodeId,
            currentSeriesId       = currentSeriesId,
            currentStartPosition  = currentStartPosition,
            currentCatchupDuration = currentCatchupDuration,
            currentNowPlayingTitle       = currentNowPlayingTitle,
            currentNowPlayingSubtitle    = currentNowPlayingSubtitle,
            currentNowPlayingDescription = currentNowPlayingDescription,
            activeProfileId    = activeProfileId,
            picksViewModel     = picksViewModel,
            watchlistViewModel = watchlistViewModel,
            repository         = repository,
            context            = context,
            scope              = scope,
            onNavigateToAddPlaylist = onNavigateToAddPlaylist,
            onEpgPlayerVisibleChange = onEpgPlayerVisibleChange,
            onEpgFocusRequestReady   = onEpgFocusRequestReady,
            onDialogOpen             = onDialogOpen,
            onPendingEpgChannelConsumed = onPendingEpgChannelConsumed,
            onMovieIndexChange    = onMovieIndexChange,
            onMovieGridViewReady  = onMovieGridViewReady,
            onMovieCategoryChange = onMovieCategoryChange,
            onSilentMovieFilterConsumed = onSilentMovieFilterConsumed,
            onKeyboardMovieDismissed    = onKeyboardMovieDismissed,
            onKeyboardMovieDismissedEmpty = onKeyboardMovieDismissedEmpty,
            onSeriesIndexChange   = onSeriesIndexChange,
            onSeriesGridViewReady = onSeriesGridViewReady,
            onSeriesCategoryChange = onSeriesCategoryChange,
            onSilentSeriesFilterConsumed = onSilentSeriesFilterConsumed,
            onKeyboardSeriesDismissed    = onKeyboardSeriesDismissed,
            onKeyboardSeriesDismissedEmpty = onKeyboardSeriesDismissedEmpty,
            onDownloadEpisode      = onDownloadEpisode,
            onCatchUpGridViewReady = onCatchUpGridViewReady,
            onCatchUpDateChange    = onCatchUpDateChange,
            onKeyboardCatchUpDismissed      = onKeyboardCatchUpDismissed,
            onKeyboardCatchUpDismissedEmpty = onKeyboardCatchUpDismissedEmpty,
            onDownloadCatchUp      = onDownloadCatchUp,
            onMyListClearDismissed = onMyListClearDismissed,
            onRecentClearDismissed = onRecentClearDismissed,
            onGoToMovie            = classicOnGoToMovie,
            onGoToSeries           = classicOnGoToSeries,
            onGoToEpgForChannel    = classicOnGoToEpgForChannel,
            onNavigateToRoute      = onNavigateToRoute,
            onPickSelected         = classicOnPickSelected,
            onTogglePicksWatchlist = onTogglePicksWatchlist,
            onPlayerLaunch         = onPlayerLaunch,
            onPlayerBack           = onPlayerBack,
            onNowPlayingDescriptionChange = onNowPlayingDescriptionChange,
            onPlayNextEpisode      = onPlayNextEpisode,
            onPreviousChannel      = onPreviousChannel,
            onNextChannel          = onNextChannel,
            onCatchUpPlay          = onCatchUpPlay,
            onPanelBack            = classicOnPanelBack,
            onRailBack             = classicOnRailBack,
            onZoneContent          = onZoneContent,
            onSearchSeriesClick    = classicOnSearchSeriesClick,
            onSearchChannelPlay    = onSearchChannelPlay,
            onSearchMoviePlay      = onSearchMoviePlay,
            onWatchlistChannelPlay = onWatchlistChannelPlay,
            onRecentChannelPlay    = onRecentChannelPlay,
            onRefreshSports        = { homePageViewModel.refreshSports() },
            isRefreshingSports     = homePageViewModel.isLoadingSports.collectAsState().value,
        )
    }
}

@Composable
private fun ExitDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    var exitSelected by remember { mutableStateOf(0) }
    val exitDialogFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { exitDialogFocus.requestFocus() } catch (_: Exception) {}
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.55f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
                    .focusRequester(exitDialogFocus).focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.DirectionLeft  -> { exitSelected = (exitSelected - 1 + 2) % 2; true }
                            Key.DirectionRight -> { exitSelected = (exitSelected + 1) % 2; true }
                            Key.Enter, Key.DirectionCenter -> {
                                if (exitSelected == 0) onDismiss() else onExit(); true
                            }
                            else -> false
                        } else false
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.exit_dialog_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.exit_dialog_message), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    if (exitSelected == 0)
                        Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.common_cancel))
                        }
                    else OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.common_cancel))
                    }
                    if (exitSelected == 1)
                        Button(onClick = onExit, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.ExitToApp, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.common_exit))
                        }
                    else OutlinedButton(onClick = onExit, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.ExitToApp, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.common_exit))
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun MainContentArea(
    modifier: Modifier,
    // Route / focus
    currentRoute: AppRoute,
    zone: Zone,
    contentFR: FocusRequester,
    sidebarPanelExpanded: Boolean,
    // EPG
    epgPlayerVisible: Boolean,
    selectedGuideCategory: String?,
    epgFocusRequest: (() -> Unit)?,
    watchlistIds: Set<String>,
    pendingEpgChannelName: String?,
    epgOnBack: () -> Unit,
    onEpgFocusUp: () -> Unit,
    // Home
    homeRestoreTick: Int,
    // Movies
    lastMovieIndex: Int,
    movieRestoreTick: Int,
    selectedMovieCategory: String?,
    showMovieSearch: Boolean,
    pendingMovieGoTo: String?,
    // Series
    lastSeriesIndex: Int,
    seriesRestoreTick: Int,
    selectedSeriesCategory: String?,
    showSeriesSearch: Boolean,
    pendingSeriesGoTo: String?,
    // CatchUp
    catchUpChannels: List<ChannelEntity>,
    selectedCatchUpDateKey: String?,
    showCatchUpSearch: Boolean,
    catchUpRestoreTick: Int,
    // Other routes
    selectedDownloadsType: String?,
    selectedSearchType: String?,
    selectedMyListType: String?,
    showMyListSearch: Boolean,
    showMyListClearConfirm: Boolean,
    selectedRecentType: String?,
    showRecentSearch: Boolean,
    showRecentClearConfirm: Boolean,
    selectedPicksCategory: String?,
    selectedMusicCategory: String?,
    selectedDeviceDate: String?,
    hasJellyfinPlaylist: Boolean,
    // Player state
    showPlayer: Boolean,
    currentChannelUrl: String,
    currentMovieId: String?,
    currentEpisodeId: String?,
    currentSeriesId: String?,
    currentStartPosition: Long,
    currentCatchupDuration: Long,
    currentNowPlayingTitle: String?,
    currentNowPlayingSubtitle: String?,
    currentNowPlayingDescription: String?,
    activeProfileId: String,
    // Objects
    picksViewModel: PicksViewModel,
    watchlistViewModel: WatchlistViewModel,
    repository: PlaylistRepository,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToAddPlaylist: () -> Unit,
    // Callbacks
    onEpgPlayerVisibleChange: (Boolean) -> Unit,
    onEpgFocusRequestReady: ((() -> Unit)?) -> Unit,
    onDialogOpen: (Boolean) -> Unit,
    onPendingEpgChannelConsumed: () -> Unit,
    onMovieIndexChange: (Int) -> Unit,
    onMovieGridViewReady: (PosterGridView?) -> Unit,
    onMovieCategoryChange: (String?) -> Unit,
    onSilentMovieFilterConsumed: () -> Unit,
    onKeyboardMovieDismissed: () -> Unit,
    onKeyboardMovieDismissedEmpty: () -> Unit = {},
    onSeriesIndexChange: (Int) -> Unit,
    onSeriesGridViewReady: (PosterGridView?) -> Unit,
    onSeriesCategoryChange: (String?) -> Unit,
    onSilentSeriesFilterConsumed: () -> Unit,
    onKeyboardSeriesDismissed: () -> Unit,
    onKeyboardSeriesDismissedEmpty: () -> Unit = {},
    onDownloadEpisode: (String, String) -> Unit,
    onCatchUpGridViewReady: (PosterGridView?) -> Unit,
    onCatchUpDateChange: (String?) -> Unit,
    onKeyboardCatchUpDismissed: () -> Unit,
    onKeyboardCatchUpDismissedEmpty: () -> Unit = {},
    onDownloadCatchUp: (String, String) -> Unit,
    onMyListClearDismissed: () -> Unit,
    onRecentClearDismissed: () -> Unit,
    onGoToMovie: (String) -> Unit,
    onGoToSeries: (String) -> Unit,
    onGoToEpgForChannel: (String) -> Unit,
    onNavigateToRoute: (AppRoute) -> Unit,
    onPickSelected: (PickItem) -> Unit,
    onTogglePicksWatchlist: (PickItem, Boolean) -> Unit,
    onPlayerLaunch: (url: String, movieId: String?, episodeId: String?, seriesId: String?, startPos: Long, title: String?, subtitle: String?, description: String?) -> Unit,
    onPlayerBack: (fromRoute: AppRoute) -> Unit,
    onNowPlayingDescriptionChange: (String?) -> Unit,
    onPlayNextEpisode: (EpisodeEntity) -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    onCatchUpPlay: (url: String, name: String, subtitle: String?, description: String?, durationMs: Long) -> Unit,
    onPanelBack: () -> Unit,
    onRailBack: () -> Unit,
    onZoneContent: () -> Unit,
    onSearchSeriesClick: () -> Unit,
    onSearchChannelPlay: (url: String, name: String) -> Unit,
    onSearchMoviePlay: (MovieEntity) -> Unit,
    onWatchlistChannelPlay: (url: String, name: String) -> Unit,
    onRecentChannelPlay: (url: String, name: String) -> Unit,
    onRefreshSports: () -> Unit,
    isRefreshingSports: Boolean,
) {
    var showMultiScreen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        val epgActive = currentRoute == AppRoute.Guide
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (epgActive) 1f else 0f }
                .then(if (!epgActive) Modifier.focusProperties { canFocus = false } else Modifier)
                .then(if (!epgActive) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else Modifier)
        ) {
            EPGScreen(
                selectedCategory          = selectedGuideCategory,
                isContentFocused          = epgActive && zone == Zone.CONTENT,
                onPlayerVisibilityChanged = onEpgPlayerVisibleChange,
                onBack                    = epgOnBack,
                onGridFocusRequesterReady = { fn -> onEpgFocusRequestReady(fn) },
                onDialogOpen              = onDialogOpen,
                watchlistIds              = watchlistIds,
                onToggleChannelWatchlist  = { channelId, channelName, logoUrl, streamUrl ->
                    val isBookmarked = channelId in watchlistIds
                    watchlistViewModel.toggleWatchlist(
                        app.nexstream.player.data.local.entity.WatchlistEntity(
                            id        = channelId,
                            profileId = watchlistViewModel.profileManager.activeProfile.value?.id ?: "default",
                            type      = app.nexstream.player.data.local.entity.WatchlistType.CHANNEL,
                            name      = channelName,
                            posterUrl = logoUrl,
                            streamUrl = streamUrl
                        ),
                        isBookmarked
                    )
                },
                pendingChannelName       = pendingEpgChannelName,
                onPendingChannelConsumed = onPendingEpgChannelConsumed,
                onFocusUp               = onEpgFocusUp,
                panelExpanded           = sidebarPanelExpanded,
            )
        }

        when (currentRoute) {
            AppRoute.Movies -> MoviesScreen(
                restoreIndex  = lastMovieIndex,
                restoreTick   = movieRestoreTick,
                onItemFocused = onMovieIndexChange,
                selectedCategory        = selectedMovieCategory,
                showSearch              = showMovieSearch,
                firstItemFocusRequester = null,
                onMovieClick = { streamUrl, movieId, startPosition, movieName ->
                    onPlayerLaunch(streamUrl, movieId, null, null, startPosition, movieName, null, null)
                    scope.launch {
                        movieId?.let { id -> repository.getMovieById(id)?.let { movie ->
                            repository.recordRecentlyWatchedMovie(movie)
                            onNowPlayingDescriptionChange(movie.plot)
                        }}
                    }
                },
                onBack                  = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                onRequestSidebarFocus   = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onGridViewReady         = onMovieGridViewReady,
                onContentFocused        = { if (zone == Zone.PANEL || zone == Zone.CONTENT) onZoneContent() },
                onDialogOpen            = onDialogOpen,
                silentFilterQuery       = pendingMovieGoTo,
                onSilentFilterConsumed  = onSilentMovieFilterConsumed,
                onKeyboardDismissed      = onKeyboardMovieDismissed,
                onKeyboardDismissedEmpty = onKeyboardMovieDismissedEmpty,
                onCategorySelect         = onMovieCategoryChange
            )

            AppRoute.Series -> SeriesScreen(
                restoreIndex  = lastSeriesIndex,
                restoreTick   = seriesRestoreTick,
                onItemFocused = onSeriesIndexChange,
                selectedCategory        = selectedSeriesCategory,
                showSearch              = showSeriesSearch,
                firstItemFocusRequester = null,
                onPlayEpisode = { streamUrl, episodeId, startPosition, seriesId, seriesName, seasonNum, episodeNum, episodeName ->
                    onPlayerLaunch(streamUrl, null, episodeId, seriesId, startPosition, seriesName, "S${seasonNum}E${episodeNum} - $episodeName", null)
                    scope.launch {
                        val series  = repository.getSeriesById(seriesId)
                        val episode = repository.getEpisodeById(episodeId)
                        if (series != null && episode != null) {
                            repository.recordRecentlyWatchedEpisode(series, episode)
                            onNowPlayingDescriptionChange(episode.plot ?: series.plot)
                        }
                    }
                },
                onBack                  = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onRequestSidebarFocus   = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                onGridViewReady         = onSeriesGridViewReady,
                onContentFocused        = { if (zone == Zone.PANEL || zone == Zone.CONTENT) onZoneContent() },
                onDialogOpen            = onDialogOpen,
                onDownloadEpisode       = onDownloadEpisode,
                silentFilterQuery       = pendingSeriesGoTo,
                onSilentFilterConsumed  = onSilentSeriesFilterConsumed,
                onKeyboardDismissed      = onKeyboardSeriesDismissed,
                onKeyboardDismissedEmpty = onKeyboardSeriesDismissedEmpty,
                onCategorySelect         = onSeriesCategoryChange,
            )

            AppRoute.Downloads -> app.nexstream.player.ui.screens.downloads.DownloadsScreen(
                firstItemFocusRequester = contentFR,
                selectedType = selectedDownloadsType,
                profileId    = activeProfileId,
                onBack = onRailBack,
                onRequestSidebarFocus = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onPlayFile = { filePath, title ->
                    val cleanPath = filePath.removePrefix("file://").removePrefix("file:")
                    val fileUri = android.net.Uri.fromFile(java.io.File(cleanPath)).toString()
                    onPlayerLaunch(fileUri, null, null, null, 0L, title, null, null)
                }
            )

            AppRoute.Search -> SearchScreen(
                firstItemFocusRequester = contentFR,
                selectedType            = selectedSearchType,
                onChannelClick          = onSearchChannelPlay,
                onMovieClick            = onSearchMoviePlay,
                onSeriesClick           = { s -> onGoToSeries(s.name) },
                onGoToMovie             = onGoToMovie,
                onGoToEpgForChannel     = onGoToEpgForChannel,
                onRequestSidebarFocus   = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() }
            )

            AppRoute.MyList -> when (selectedMyListType) {
                "Reminders" -> RemindersScreen(firstItemFocusRequester = contentFR)
                "Downloads" -> app.nexstream.player.ui.screens.downloads.DownloadsScreen(
                    firstItemFocusRequester = contentFR,
                    selectedType = null,
                    profileId    = activeProfileId,
                    onBack       = onRailBack,
                    onRequestSidebarFocus = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                    onPlayFile   = { filePath, title ->
                        val cleanPath = filePath.removePrefix("file://").removePrefix("file:")
                        val fileUri = android.net.Uri.fromFile(java.io.File(cleanPath)).toString()
                        onPlayerLaunch(fileUri, null, null, null, 0L, title, null, null)
                    }
                )
                else -> WatchlistScreen(
                firstItemFocusRequester = contentFR,
                selectedType     = selectedMyListType,
                showSearch       = showMyListSearch,
                showClearConfirm = showMyListClearConfirm,
                onClearDismissed = onMyListClearDismissed,
                onChannelClick   = onWatchlistChannelPlay,
                onMovieClick     = { _ -> },
                onSeriesClick    = { _ -> },
                onGoToMovie          = onGoToMovie,
                onGoToSeries         = onGoToSeries,
                onGoToEpgForChannel  = onGoToEpgForChannel,
                profileId        = activeProfileId,
                onRequestSidebarFocus = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onLaunchPlayer   = { url, movieId, episodeId, seriesId, startPos, title, subtitle ->
                    onPlayerLaunch(url, movieId, episodeId, seriesId, startPos, title, subtitle, null)
                },
                onPlayFile       = { filePath, title ->
                    val cleanPath = filePath.removePrefix("file://").removePrefix("file:")
                    val fileUri = android.net.Uri.fromFile(java.io.File(cleanPath)).toString()
                    onPlayerLaunch(fileUri, null, null, null, 0L, title, null, null)
                }
            )}

            AppRoute.Reminders -> RemindersScreen(firstItemFocusRequester = contentFR)

            AppRoute.CatchUp -> CatchUpScreen(
                catchUpChannels       = catchUpChannels,
                onGridViewReady       = onCatchUpGridViewReady,
                onContentFocused      = { if (zone == Zone.PANEL || zone == Zone.CONTENT) onZoneContent() },
                onRequestSidebarFocus = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                onDialogOpen          = onDialogOpen,
                selectedDateKey       = selectedCatchUpDateKey,
                showSearch                = showCatchUpSearch,
                restoreTick               = catchUpRestoreTick,
                onKeyboardDismissed       = onKeyboardCatchUpDismissed,
                onKeyboardDismissedEmpty  = onKeyboardCatchUpDismissedEmpty,
                onDownloadEpisode         = onDownloadCatchUp,
                onDateSelect          = onCatchUpDateChange,
                onPlayTimeshift       = { streamUrl, programmeName, subtitle, description, durationMs ->
                    onCatchUpPlay(streamUrl, programmeName, subtitle, description, durationMs)
                }
            )

            AppRoute.Recent -> RecentlyWatchedScreen(
                firstItemFocusRequester = contentFR,
                selectedType     = selectedRecentType,
                showSearch       = showRecentSearch,
                showClearConfirm = showRecentClearConfirm,
                onClearDismissed = onRecentClearDismissed,
                onChannelClick   = onRecentChannelPlay,
                onMovieClick     = { _ -> },
                onEpisodeClick   = { _ -> },
                onGoToMovie         = onGoToMovie,
                onGoToSeries        = onGoToSeries,
                onGoToEpgForChannel = onGoToEpgForChannel,
                onLaunchPlayer  = { url, movieId, episodeId, seriesId, startPos, title, subtitle ->
                    onPlayerLaunch(url, movieId, episodeId, seriesId, startPos, title, subtitle, null)
                }
            )

            AppRoute.Settings           -> app.nexstream.player.ui.screens.settings.SettingsMenuScreen(onNavigate = onNavigateToRoute)
            AppRoute.SettingsPlaylists  -> SettingsScreen(
                onNavigateToAddPlaylist = onNavigateToAddPlaylist,
                firstItemFocusRequester = contentFR,
            )
            AppRoute.SettingsSports     -> app.nexstream.player.ui.screens.settings.SportsSettingsScreen(
                firstItemFocusRequester = contentFR,
                onRefresh = onRefreshSports,
                isRefreshing = isRefreshingSports,
            )
            AppRoute.SettingsAppearance   -> AppearanceScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsChannelGroups -> app.nexstream.player.ui.screens.settings.ChannelGroupsScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsLicence    -> LicenceScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsProfiles -> {
                var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
                var showEdit       by remember { mutableStateOf(false) }
                if (showEdit) {
                    ProfileEditScreen(
                        existingProfile = editingProfile,
                        onDone   = { showEdit = false; editingProfile = null },
                        onCancel = { showEdit = false; editingProfile = null }
                    )
                } else {
                    ProfilesSettingsScreen(
                        onEditProfile = { profile -> editingProfile = profile; showEdit = true },
                        firstItemFocusRequester = contentFR
                    )
                }
            }
            AppRoute.SettingsPlayer     -> app.nexstream.player.ui.screens.settings.PlayerSettingsScreen(firstItemFocusRequester = contentFR, profileId = activeProfileId)
            AppRoute.SettingsSyncSettings -> app.nexstream.player.ui.screens.settings.SyncSettingsScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsAccount    -> app.nexstream.player.ui.screens.settings.AccountScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsAbout      -> app.nexstream.player.ui.screens.settings.AboutScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsLanguage   -> app.nexstream.player.ui.screens.settings.LanguageSettingsScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsProxy      -> app.nexstream.player.ui.screens.settings.ProxySettingsScreen(firstItemFocusRequester = contentFR)
            AppRoute.SettingsNavigation -> app.nexstream.player.ui.screens.settings.NavigationSettingsScreen(
                firstItemFocusRequester = contentFR,
                hasJellyfinPlaylist = hasJellyfinPlaylist,
            )
            AppRoute.Picks -> PicksScreen(
                selectedSeed            = selectedPicksCategory,
                onContentFocused        = { if (zone == Zone.PANEL || zone == Zone.CONTENT) onZoneContent() },
                onDialogOpen            = onDialogOpen,
                firstItemFocusRequester = contentFR,
                onPickSelected          = onPickSelected,
                watchlistIds            = watchlistIds,
                onToggleWatchlist       = onTogglePicksWatchlist,
                onPlayerLaunch          = onPlayerLaunch,
                viewModel               = picksViewModel
            )
            AppRoute.Home -> ModernHomeScreen(
                firstItemFocusRequester = contentFR,
                homeRestoreTick         = homeRestoreTick,
                onPickSelected          = onPickSelected,
                onPlayerLaunch          = onPlayerLaunch,
                onChannelPlay           = onWatchlistChannelPlay,
                onGoToEpg               = { channelName ->
                    onGoToEpgForChannel(channelName)
                },
            )
            AppRoute.Music -> MusicScreen(
                selectedCategory = selectedMusicCategory,
                onBack           = { if (sidebarPanelExpanded) onPanelBack() else onRailBack() },
                isContentFocused = zone == Zone.CONTENT,
            )
            AppRoute.Device -> DeviceScreen(
                selectedDateKey         = selectedDeviceDate,
                firstItemFocusRequester = contentFR,
                isContentFocused        = zone == Zone.CONTENT,
                onPlayerLaunch          = onPlayerLaunch,
            )
            else -> Unit
        }

        if (showPlayer && !showMultiScreen) {
            key(currentChannelUrl, currentEpisodeId) {
                PlayerScreen(
                    channelUrl            = currentChannelUrl,
                    movieId               = currentMovieId,
                    episodeId             = currentEpisodeId,
                    seriesId              = currentSeriesId,
                    startPosition         = currentStartPosition,
                    profileId             = activeProfileId,
                    nowPlayingTitle       = currentNowPlayingTitle,
                    nowPlayingSubtitle    = currentNowPlayingSubtitle,
                    nowPlayingDescription = currentNowPlayingDescription,
                    onPlayNextEpisode     = onPlayNextEpisode,
                    onPreviousChannel     = onPreviousChannel,
                    onNextChannel         = onNextChannel,
                    onOpenMultiScreen     = { showMultiScreen = true },
                    onBack                = { onPlayerBack(currentRoute) }
                )
            }
        }

        if (showMultiScreen) {
            app.nexstream.player.ui.screens.player.MultiScreenPlayerScreen(
                initialChannelUrl  = currentChannelUrl,
                initialChannelName = currentNowPlayingTitle ?: "",
                onBack             = { showMultiScreen = false },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ModernMainLayout(
    showPlayer: Boolean,
    epgPlayerVisible: Boolean,
    currentRoute: AppRoute,
    zone: Zone,
    contentFR: FocusRequester,
    sidebarRefocusTick: Int,
    guideStripFocusTick: Int,
    subStripFocusTick: Int,
    activeProfileEmoji: String,
    activeProfileName: String,
    guideCategories: List<String>,
    selectedGuideCategory: String?,
    movieCategories: List<String>,
    selectedMovieCategory: String?,
    seriesCategories: List<String>,
    selectedSeriesCategory: String?,
    catchUpAvailableDates: List<Long>,
    catchUpDateLabels: List<String>,
    selectedCatchUpLabel: String?,
    selectedSearchType: String?,
    selectedMyListType: String?,
    selectedDownloadsType: String?,
    settingsSubCategories: List<String>,
    selectedSettingsLabel: String?,
    settingsLabelToRoute: Map<String, AppRoute>,
    epgFocusRequest: (() -> Unit)?,
    epgOnBack: () -> Unit,
    watchlistIds: Set<String>,
    pendingEpgChannelName: String?,
    lastMovieIndex: Int,
    homeRestoreTick: Int,
    movieRestoreTick: Int,
    showMovieSearch: Boolean,
    pendingMovieGoTo: String?,
    lastSeriesIndex: Int,
    seriesRestoreTick: Int,
    showSeriesSearch: Boolean,
    pendingSeriesGoTo: String?,
    catchUpChannels: List<ChannelEntity>,
    selectedCatchUpDateKey: String?,
    showCatchUpSearch: Boolean,
    catchUpRestoreTick: Int,
    showMyListSearch: Boolean,
    showMyListClearConfirm: Boolean,
    selectedRecentType: String?,
    showRecentSearch: Boolean,
    showRecentClearConfirm: Boolean,
    selectedPicksCategory: String?,
    selectedMusicCategory: String?,
    selectedDeviceDate: String?,
    hasJellyfinPlaylist: Boolean,
    currentChannelUrl: String,
    currentMovieId: String?,
    currentEpisodeId: String?,
    currentSeriesId: String?,
    currentStartPosition: Long,
    currentCatchupDuration: Long,
    currentNowPlayingTitle: String?,
    currentNowPlayingSubtitle: String?,
    currentNowPlayingDescription: String?,
    activeProfileId: String,
    picksViewModel: PicksViewModel,
    watchlistViewModel: WatchlistViewModel,
    repository: PlaylistRepository,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    // Setters
    setZone: (Zone) -> Unit,
    incrSidebarRefocusTick: () -> Unit,
    incrGuideStripFocusTick: () -> Unit,
    incrSubStripFocusTick: () -> Unit,
    setCurrentRoute: (AppRoute) -> Unit,
    setSelectedGuideCategory: (String?) -> Unit,
    setSelectedMovieCategory: (String?) -> Unit,
    setSelectedSeriesCategory: (String?) -> Unit,
    setSelectedCatchUpDateKey: (String?) -> Unit,
    setSelectedSearchType: (String?) -> Unit,
    setSelectedMyListType: (String?) -> Unit,
    setSelectedDownloadsType: (String?) -> Unit,
    setShowProfileSwitch: (Boolean) -> Unit,
    setPendingMovieGoTo: (String?) -> Unit,
    setShowMovieSearch: (Boolean) -> Unit,
    setPendingSeriesGoTo: (String?) -> Unit,
    setShowSeriesSearch: (Boolean) -> Unit,
    setPendingEpgChannelName: (String?) -> Unit,
    focusContent: () -> Unit,
    onNavigateToAddPlaylist: () -> Unit,
    onPreviousChannel: (() -> Unit)?,
    onNextChannel: (() -> Unit)?,
    // Shared callbacks
    onEpgPlayerVisibleChange: (Boolean) -> Unit,
    onEpgFocusRequestReady: ((() -> Unit)?) -> Unit,
    onDialogOpen: (Boolean) -> Unit,
    onPendingEpgChannelConsumed: () -> Unit,
    onMovieIndexChange: (Int) -> Unit,
    onMovieGridViewReady: (PosterGridView?) -> Unit,
    onMovieCategoryChange: (String?) -> Unit,
    onSilentMovieFilterConsumed: () -> Unit,
    onKeyboardMovieDismissed: () -> Unit,
    onKeyboardMovieDismissedEmpty: () -> Unit = {},
    onSeriesIndexChange: (Int) -> Unit,
    onSeriesGridViewReady: (PosterGridView?) -> Unit,
    onSeriesCategoryChange: (String?) -> Unit,
    onSilentSeriesFilterConsumed: () -> Unit,
    onKeyboardSeriesDismissed: () -> Unit,
    onKeyboardSeriesDismissedEmpty: () -> Unit = {},
    onDownloadEpisode: (String, String) -> Unit,
    onCatchUpGridViewReady: (PosterGridView?) -> Unit,
    onCatchUpDateChange: (String?) -> Unit,
    onKeyboardCatchUpDismissed: () -> Unit,
    onKeyboardCatchUpDismissedEmpty: () -> Unit = {},
    onDownloadCatchUp: (String, String) -> Unit,
    onMyListClearDismissed: () -> Unit,
    onRecentClearDismissed: () -> Unit,
    onNavigateToRoute: (AppRoute) -> Unit,
    onTogglePicksWatchlist: (PickItem, Boolean) -> Unit,
    onPlayerLaunch: (String, String?, String?, String?, Long, String?, String?, String?) -> Unit,
    onPlayerBack: (AppRoute) -> Unit,
    onNowPlayingDescriptionChange: (String?) -> Unit,
    onPlayNextEpisode: (EpisodeEntity) -> Unit,
    onCatchUpPlay: (String, String, String?, String?, Long) -> Unit,
    onZoneContent: () -> Unit,
    onSearchChannelPlay: (String, String) -> Unit,
    onSearchMoviePlay: (MovieEntity) -> Unit,
    onWatchlistChannelPlay: (String, String) -> Unit,
    onRecentChannelPlay: (String, String) -> Unit,
    onRefreshSports: () -> Unit,
    isRefreshingSports: Boolean,
    incrProfilesNavTick: () -> Unit = {},
) {
    val onGoToMovie: (String) -> Unit = { name ->
        setPendingMovieGoTo(name); setShowMovieSearch(false)
        setSelectedMovieCategory(null); setCurrentRoute(AppRoute.Movies); setZone(Zone.CONTENT)
    }
    val onGoToSeries: (String) -> Unit = { name ->
        setPendingSeriesGoTo(name); setShowSeriesSearch(false)
        setSelectedSeriesCategory(null); setCurrentRoute(AppRoute.Series); setZone(Zone.CONTENT)
    }
    val onGoToEpgForChannel: (String) -> Unit = { channelName ->
        setPendingEpgChannelName(channelName); setCurrentRoute(AppRoute.Guide)
        setSelectedGuideCategory(null); setZone(Zone.CONTENT)
        scope.launch { kotlinx.coroutines.delay(300); focusContent() }
    }
    val onPickSelected: (PickItem) -> Unit = { pick ->
        if (pick.mediaType == "movie") {
            setPendingMovieGoTo(pick.title); setShowMovieSearch(false)
            setSelectedMovieCategory(null); setCurrentRoute(AppRoute.Movies); setZone(Zone.CONTENT)
        } else {
            setPendingSeriesGoTo(pick.title); setShowSeriesSearch(false)
            setSelectedSeriesCategory(null); setCurrentRoute(AppRoute.Series); setZone(Zone.CONTENT)
        }
    }
    val onPanelBack: () -> Unit = { setZone(Zone.RAIL); incrSidebarRefocusTick() }
    val onRailBack: () -> Unit  = { setZone(Zone.RAIL); incrSidebarRefocusTick() }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        if (!showPlayer && !epgPlayerVisible) {
            ModernNavAndStrips(
                currentRoute       = currentRoute,
                zone               = zone,
                onNavigate         = { setCurrentRoute(it) },
                onDropToContent    = {
                    when {
                        currentRoute == AppRoute.Guide && guideCategories.isNotEmpty() ->
                            { setZone(Zone.PANEL); incrGuideStripFocusTick() }
                        currentRoute in setOf(AppRoute.Movies, AppRoute.Series, AppRoute.Search,
                            AppRoute.MyList, AppRoute.Downloads) ->
                            { setZone(Zone.PANEL); incrSubStripFocusTick() }
                        currentRoute == AppRoute.CatchUp && catchUpAvailableDates.isNotEmpty() ->
                            { setZone(Zone.PANEL); incrSubStripFocusTick() }
                        currentRoute.isSettings -> { setZone(Zone.PANEL); incrSubStripFocusTick() }
                        else -> { setZone(Zone.CONTENT); focusContent() }
                    }
                },
                navFocusTick       = sidebarRefocusTick,
                activeProfileEmoji = activeProfileEmoji,
                activeProfileName  = activeProfileName,
                onProfileClick     = { setShowProfileSwitch(true) },
                guideCategories    = guideCategories,
                selectedGuideCategory = selectedGuideCategory,
                onSelectGuideCategory = { setSelectedGuideCategory(it) },
                guideStripFocusTick = guideStripFocusTick,
                movieCategories    = movieCategories,
                selectedMovieCategory = selectedMovieCategory,
                onSelectMovieCategory = { setSelectedMovieCategory(it) },
                seriesCategories   = seriesCategories,
                selectedSeriesCategory = selectedSeriesCategory,
                onSelectSeriesCategory = { setSelectedSeriesCategory(it) },
                catchUpAvailableDates = catchUpAvailableDates,
                catchUpDateLabels  = catchUpDateLabels,
                selectedCatchUpLabel = selectedCatchUpLabel,
                onSelectCatchUpDate = { label ->
                    val idx = catchUpDateLabels.indexOf(label)
                    setSelectedCatchUpDateKey(if (idx >= 0) catchUpAvailableDates.getOrNull(idx)?.toString() else null)
                },
                selectedSearchType = selectedSearchType,
                onSelectSearchType = { setSelectedSearchType(it) },
                selectedMyListType = selectedMyListType,
                onSelectMyListType = { setSelectedMyListType(it) },
                selectedDownloadsType = selectedDownloadsType,
                onSelectDownloadsType = { setSelectedDownloadsType(it) },
                settingsSubCategories = settingsSubCategories,
                selectedSettingsLabel = selectedSettingsLabel,
                settingsLabelToRoute  = settingsLabelToRoute,
                onSelectSettingsLabel = { label ->
                    if (label == "Profiles") incrProfilesNavTick()
                    setCurrentRoute(if (label == null) AppRoute.Settings else settingsLabelToRoute[label] ?: AppRoute.Settings)
                },
                subStripFocusTick  = subStripFocusTick,
                onFocusUp          = { setZone(Zone.RAIL); incrSidebarRefocusTick() },
                onFocusDown        = { setZone(Zone.CONTENT); focusContent() },
                onBack             = { setZone(Zone.RAIL); incrSidebarRefocusTick() },
                onStopPlayer       = { if (showPlayer) onPlayerBack(currentRoute) },
            )
        }
        MainContentArea(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .focusRequester(contentFR),
            currentRoute          = currentRoute,
            zone                  = zone,
            contentFR             = contentFR,
            sidebarPanelExpanded  = false,
            epgPlayerVisible      = epgPlayerVisible,
            selectedGuideCategory = selectedGuideCategory,
            epgFocusRequest       = epgFocusRequest,
            watchlistIds          = watchlistIds,
            pendingEpgChannelName = pendingEpgChannelName,
            epgOnBack             = epgOnBack,
            onEpgFocusUp          = { incrGuideStripFocusTick() },
            homeRestoreTick       = homeRestoreTick,
            lastMovieIndex        = lastMovieIndex,
            movieRestoreTick      = movieRestoreTick,
            selectedMovieCategory = selectedMovieCategory,
            showMovieSearch       = showMovieSearch,
            pendingMovieGoTo      = pendingMovieGoTo,
            lastSeriesIndex       = lastSeriesIndex,
            seriesRestoreTick     = seriesRestoreTick,
            selectedSeriesCategory = selectedSeriesCategory,
            showSeriesSearch      = showSeriesSearch,
            pendingSeriesGoTo     = pendingSeriesGoTo,
            catchUpChannels       = catchUpChannels,
            selectedCatchUpDateKey = selectedCatchUpDateKey,
            showCatchUpSearch     = showCatchUpSearch,
            catchUpRestoreTick    = catchUpRestoreTick,
            selectedDownloadsType = selectedDownloadsType,
            selectedSearchType    = selectedSearchType,
            selectedMyListType    = selectedMyListType,
            showMyListSearch      = showMyListSearch,
            showMyListClearConfirm = showMyListClearConfirm,
            selectedRecentType    = selectedRecentType,
            showRecentSearch      = showRecentSearch,
            showRecentClearConfirm = showRecentClearConfirm,
            selectedPicksCategory = selectedPicksCategory,
            selectedMusicCategory = selectedMusicCategory,
            selectedDeviceDate    = selectedDeviceDate,
            hasJellyfinPlaylist   = hasJellyfinPlaylist,
            showPlayer            = showPlayer,
            currentChannelUrl     = currentChannelUrl,
            currentMovieId        = currentMovieId,
            currentEpisodeId      = currentEpisodeId,
            currentSeriesId       = currentSeriesId,
            currentStartPosition  = currentStartPosition,
            currentCatchupDuration = currentCatchupDuration,
            currentNowPlayingTitle       = currentNowPlayingTitle,
            currentNowPlayingSubtitle    = currentNowPlayingSubtitle,
            currentNowPlayingDescription = currentNowPlayingDescription,
            activeProfileId    = activeProfileId,
            picksViewModel     = picksViewModel,
            watchlistViewModel = watchlistViewModel,
            repository         = repository,
            context            = context,
            scope              = scope,
            onNavigateToAddPlaylist      = onNavigateToAddPlaylist,
            onEpgPlayerVisibleChange     = onEpgPlayerVisibleChange,
            onEpgFocusRequestReady       = onEpgFocusRequestReady,
            onDialogOpen                 = onDialogOpen,
            onPendingEpgChannelConsumed  = onPendingEpgChannelConsumed,
            onMovieIndexChange           = onMovieIndexChange,
            onMovieGridViewReady         = onMovieGridViewReady,
            onMovieCategoryChange        = onMovieCategoryChange,
            onSilentMovieFilterConsumed  = onSilentMovieFilterConsumed,
            onKeyboardMovieDismissed     = onKeyboardMovieDismissed,
            onKeyboardMovieDismissedEmpty = onKeyboardMovieDismissedEmpty,
            onSeriesIndexChange          = onSeriesIndexChange,
            onSeriesGridViewReady        = onSeriesGridViewReady,
            onSeriesCategoryChange       = onSeriesCategoryChange,
            onSilentSeriesFilterConsumed = onSilentSeriesFilterConsumed,
            onKeyboardSeriesDismissed    = onKeyboardSeriesDismissed,
            onKeyboardSeriesDismissedEmpty = onKeyboardSeriesDismissedEmpty,
            onDownloadEpisode            = onDownloadEpisode,
            onCatchUpGridViewReady          = onCatchUpGridViewReady,
            onCatchUpDateChange             = onCatchUpDateChange,
            onKeyboardCatchUpDismissed      = onKeyboardCatchUpDismissed,
            onKeyboardCatchUpDismissedEmpty = onKeyboardCatchUpDismissedEmpty,
            onDownloadCatchUp               = onDownloadCatchUp,
            onMyListClearDismissed          = onMyListClearDismissed,
            onRecentClearDismissed       = onRecentClearDismissed,
            onGoToMovie                  = onGoToMovie,
            onGoToSeries                 = onGoToSeries,
            onGoToEpgForChannel          = onGoToEpgForChannel,
            onNavigateToRoute            = onNavigateToRoute,
            onPickSelected               = onPickSelected,
            onTogglePicksWatchlist       = onTogglePicksWatchlist,
            onPlayerLaunch               = onPlayerLaunch,
            onPlayerBack                 = onPlayerBack,
            onNowPlayingDescriptionChange = onNowPlayingDescriptionChange,
            onPlayNextEpisode            = onPlayNextEpisode,
            onPreviousChannel            = onPreviousChannel,
            onNextChannel                = onNextChannel,
            onCatchUpPlay                = onCatchUpPlay,
            onPanelBack                  = onPanelBack,
            onRailBack                   = onRailBack,
            onZoneContent                = onZoneContent,
            onSearchSeriesClick          = { setCurrentRoute(AppRoute.Series) },
            onSearchChannelPlay          = onSearchChannelPlay,
            onSearchMoviePlay            = onSearchMoviePlay,
            onWatchlistChannelPlay       = onWatchlistChannelPlay,
            onRecentChannelPlay          = onRecentChannelPlay,
            onRefreshSports              = onRefreshSports,
            isRefreshingSports           = isRefreshingSports,
        )
    }
}
