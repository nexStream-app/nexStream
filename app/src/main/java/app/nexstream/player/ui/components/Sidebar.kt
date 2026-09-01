package app.nexstream.player.ui.components

import androidx.compose.foundation.background
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.screens.main.hasCategoryPanel
import app.nexstream.player.ui.screens.main.isSettings
import app.nexstream.player.ui.screens.main.rootSection
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.nexstream.player.R
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.LogoMode
import app.nexstream.player.ui.theme.getRailHiddenFlow
import app.nexstream.player.ui.theme.getRailOrderFlow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage


@Composable
fun Sidebar(
    currentRoute: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    showMovieCategories: Boolean,
    showGuideCategories: Boolean,
    showSeriesCategories: Boolean,
    showSettingsMenu: Boolean,
    guideCategories: List<String>,
    movieCategories: List<String>,
    seriesCategories: List<String>,
    catchUpAvailableDates: List<Long> = emptyList(),
    catchUpChannels: List<app.nexstream.player.data.local.entity.ChannelEntity> = emptyList(),
    selectedCatchUpChannel: app.nexstream.player.data.local.entity.ChannelEntity? = null,
    onCatchUpChannelSelected: (app.nexstream.player.data.local.entity.ChannelEntity) -> Unit = {},
    selectedMovieCategory: String?,
    selectedGuideCategory: String?,
    selectedSeriesCategory: String?,
    selectedCatchUpDateKey: String? = null,
    onMovieCategorySelected: (String?) -> Unit,
    onGuideCategorySelected: (String?) -> Unit,
    onSeriesCategorySelected: (String?) -> Unit,
    onCatchUpDateSelected: (String?) -> Unit = {},
    selectedSettingsRoute: AppRoute? = null,
    onSettingsItemSelected: (AppRoute) -> Unit = {},
    onBackToMainMenu: () -> Unit,
    panelExpanded: Boolean = false,
    contentActive: Boolean = false,  // true only when user is in content — collapses rail
    expandedRoute: AppRoute? = null,
    onPanelExpandedChange: (expanded: Boolean, route: AppRoute?) -> Unit = { _, _ -> },
    // ── New focus architecture ───────────────────────────────────────────────
    railFR: FocusRequester = remember { FocusRequester() },
    panelFR: FocusRequester = remember { FocusRequester() },
    onRailFocusChanged: (Boolean) -> Unit = {},
    onPanelFocusChanged: (Boolean) -> Unit = {},
    onEnterPanel: () -> Unit = {},
    onEnterContent: () -> Unit = {},
    onExitPanelToRail: () -> Unit = {},
    sidebarRefocusTick: Int = 0,
    panelFocusTick: Int = 0,      // increment to focus selected category item
    // ── Other callbacks ──────────────────────────────────────────────────────
    activeProfileName: String = "Default",
    activeProfileEmoji: String = "👤",
    onProfileClick: () -> Unit = {},
    isLoadingEPG: Boolean = false,
    isLoadingVOD: Boolean = false,
    isLoadingSeries: Boolean = false,
    isLoadingCatchUp: Boolean = false,
    onSearchRequest: () -> Unit = {},
    onFavouritesSelected: (route: AppRoute) -> Unit = {},
    selectedRecentType: String? = null,
    onRecentTypeSelected: (String?) -> Unit = {},
    selectedSearchType: String? = null,
    onSearchTypeSelected: (String?) -> Unit = {},
    selectedMyListType: String? = null,
    onMyListTypeSelected: (String?) -> Unit = {},
    selectedRemindersType: String? = null,
    onRemindersTypeSelected: (String?) -> Unit = {},
    selectedDownloadsType: String? = null,
    onDownloadsTypeSelected: (String?) -> Unit = {},
    picksCategories: List<String> = emptyList(),
    selectedPicksCategory: String? = null,
    onPicksCategorySelected: (String?) -> Unit = {},
    sportsCategories: List<String> = emptyList(),
    selectedSportsCategory: String? = null,
    onSportsCategorySelected: (String?) -> Unit = {},
    onRecentClearAll: () -> Unit = {},
    onMyListClearAll: () -> Unit = {},
    xtreamUsername: String? = null,
    xtreamExpiry: String? = null,
    isLicensed: Boolean = true,
    trialDaysLeft: Int = 0,
    vodRestricted: Boolean = false,
    hasJellyfinPlaylist: Boolean = false,
    musicCategories: List<String> = emptyList(),
    selectedMusicCategory: String? = null,
    onMusicCategorySelected: (String?) -> Unit = {},
    showSyncSettings: Boolean = true,
    channelGroups: List<ChannelGroupEntity> = emptyList(),
    hasDeviceFolders: Boolean = false,
    deviceDates: List<String> = emptyList(),
    selectedDeviceDate: String? = null,
    onDeviceDateSelected: (String?) -> Unit = {},
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global

    val categories = remember(expandedRoute, guideCategories, movieCategories, seriesCategories, picksCategories, sportsCategories, musicCategories, hasJellyfinPlaylist, deviceDates) {
        when (expandedRoute) {
            AppRoute.Home      -> sportsCategories
            AppRoute.Guide     -> guideCategories
            AppRoute.Movies    -> movieCategories
            AppRoute.Series    -> seriesCategories
            AppRoute.Recent    -> listOf("Live TV", "Movies", "Episodes")
            AppRoute.Search    -> listOf("Live TV", "Movies", "Series", "People")
            AppRoute.MyList    -> buildList {
                add("Live TV"); add("Movies"); add("Series")
                if (hasJellyfinPlaylist) add("Music")
                add("Reminders"); add("Recordings"); add("Downloads")
            }
            AppRoute.Reminders -> emptyList()
            AppRoute.Downloads -> listOf("Active", "Completed", "Failed")
            AppRoute.Picks     -> picksCategories
            AppRoute.Music     -> musicCategories
            AppRoute.Device    -> deviceDates
            else               -> emptyList()
        }
    }
    val selectedCategory = when (expandedRoute) {
        AppRoute.Home      -> selectedSportsCategory
        AppRoute.Guide     -> selectedGuideCategory
        AppRoute.Movies    -> selectedMovieCategory
        AppRoute.Series    -> selectedSeriesCategory
        AppRoute.CatchUp   -> selectedCatchUpDateKey
        AppRoute.Recent    -> selectedRecentType
        AppRoute.Search    -> selectedSearchType
        AppRoute.MyList    -> selectedMyListType
        AppRoute.Reminders -> selectedRemindersType
        AppRoute.Downloads -> selectedDownloadsType
        AppRoute.Picks     -> selectedPicksCategory
        AppRoute.Music     -> selectedMusicCategory
        AppRoute.Device    -> selectedDeviceDate
        else               -> null
    }
    val onCategorySelected: (String?) -> Unit = when (expandedRoute) {
        AppRoute.Home      -> onSportsCategorySelected
        AppRoute.Guide     -> onGuideCategorySelected
        AppRoute.Movies    -> onMovieCategorySelected
        AppRoute.Series    -> onSeriesCategorySelected
        AppRoute.CatchUp   -> onCatchUpDateSelected
        AppRoute.Recent    -> onRecentTypeSelected
        AppRoute.Search    -> onSearchTypeSelected
        AppRoute.MyList    -> onMyListTypeSelected
        AppRoute.Reminders -> onRemindersTypeSelected
        AppRoute.Downloads -> onDownloadsTypeSelected
        AppRoute.Picks     -> onPicksCategorySelected
        AppRoute.Music     -> onMusicCategorySelected
        AppRoute.Device    -> onDeviceDateSelected
        else               -> ({})
    }

    val fontScale = LocalNexStreamTheme.current.typography.scale.coerceIn(0.85f, 1.3f)
    val railDp  = (180f * fontScale).dp
    val panelDp = (180f * fontScale).dp


    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(sTheme.background)
    ) {
        // ── Rail — hidden when panel is open ────────────────────────────
        if (!panelExpanded) {
            MainMenu(
                modifier           = Modifier
                    .width(railDp)
                    .fillMaxHeight()
                    .focusRequester(railFR)
                    .onFocusChanged { onRailFocusChanged(it.hasFocus) },
                currentRoute       = currentRoute,
                expandedRoute      = expandedRoute,
                panelExpanded      = panelExpanded,
                isLoadingEPG       = isLoadingEPG,
                isLoadingVOD       = isLoadingVOD,
                isLoadingSeries    = isLoadingSeries,
                isSidebarFocused   = true,
                sidebarRefocusTick = sidebarRefocusTick,
                showLabels         = true,
                onNavigate         = { route ->
                    val effectiveRoute = if (route == AppRoute.Settings) AppRoute.SettingsAbout else route
                    if (effectiveRoute.hasCategoryPanel) {
                        onPanelExpandedChange(true, effectiveRoute)
                        onNavigate(effectiveRoute)
                    } else {
                        onPanelExpandedChange(false, null)
                        onNavigate(effectiveRoute)
                    }
                },
                onEnterPanel      = onEnterPanel,
                onEnterContent    = onEnterContent,
                onExitPanelToRail = onExitPanelToRail,
                onDpadRight = {
                    if (panelExpanded) {
                        onEnterPanel()
                    } else {
                        onEnterContent()
                    }
                },
                onReopenPanel = { route ->
                    onPanelExpandedChange(true, route)
                    onEnterPanel()
                },
                activeProfileName  = activeProfileName,
                activeProfileEmoji = activeProfileEmoji,
                onProfileClick     = onProfileClick,
                xtreamUsername     = xtreamUsername,
                xtreamExpiry       = xtreamExpiry,
                isLicensed         = isLicensed,
                trialDaysLeft      = trialDaysLeft,
                vodRestricted      = vodRestricted,
                hasJellyfinPlaylist = hasJellyfinPlaylist,
                hasDeviceFolders    = hasDeviceFolders,
            )
        }

        // ── Panel — hidden when rail is visible ──────────────────────────
        if (panelExpanded) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val isAndroidTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
            Column(
                modifier = Modifier
                    .width(panelDp)
                    .fillMaxHeight()
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    expandedRoute?.isSettings == true -> SettingsPanel(
                        selectedRoute         = selectedSettingsRoute,
                        onItemSelected        = onSettingsItemSelected,
                        panelFR               = panelFR,
                        onPanelFocusChanged   = onPanelFocusChanged,
                        focusTick             = panelFocusTick,
                        onRequestContentFocus = { onEnterContent() },
                        onRequestRailFocus    = { onExitPanelToRail() },
                        isAndroidTV           = isAndroidTV,
                        onBackPressed         = onExitPanelToRail,
                        showSyncSettings      = showSyncSettings,
                        modifier              = Modifier.width(panelDp)
                    )
                    expandedRoute == AppRoute.CatchUp -> CatchUpDatePanel(
                        availableDates        = catchUpAvailableDates,
                        channels              = catchUpChannels,
                        selectedChannel       = selectedCatchUpChannel,
                        onChannelSelected     = onCatchUpChannelSelected,
                        selectedDateKey       = selectedCatchUpDateKey,
                        onDateSelected        = onCatchUpDateSelected,
                        panelFR               = panelFR,
                        onPanelFocusChanged   = onPanelFocusChanged,
                        focusTick             = panelFocusTick,
                        onRequestContentFocus = { onEnterContent() },
                        onRequestRailFocus    = { onExitPanelToRail() },
                        isLoading             = isLoadingCatchUp,
                        onSearchRequest       = onSearchRequest,
                        isAndroidTV           = isAndroidTV,
                        onBackPressed         = onExitPanelToRail,
                        modifier              = Modifier.width(panelDp)
                    )
                    else -> CategoryPanel(
                        categories            = categories,
                        selectedCategory      = selectedCategory,
                        onCategorySelected    = { cat -> onCategorySelected(cat) },
                        panelFR               = panelFR,
                        onPanelFocusChanged   = onPanelFocusChanged,
                        focusTick             = panelFocusTick,
                        onRequestContentFocus = { onEnterContent() },
                        onRequestRailFocus    = { onExitPanelToRail() },
                        onSearchRequest       = onSearchRequest,
                        onFavouritesSelected  = { expandedRoute?.let { r -> onFavouritesSelected(r) } },
                        showSearch            = false,
                        showFavourites        = expandedRoute == AppRoute.Guide || expandedRoute == AppRoute.Movies || expandedRoute == AppRoute.Series || expandedRoute == AppRoute.Music,
                        supportsKeyboardSearch = expandedRoute == AppRoute.Movies || expandedRoute == AppRoute.Series
                            || expandedRoute == AppRoute.Recent || expandedRoute == AppRoute.MyList,
                        showClearAll          = expandedRoute == AppRoute.Recent || expandedRoute == AppRoute.MyList,
                        onClearAll            = {
                            when (expandedRoute) {
                                AppRoute.Recent -> onRecentClearAll()
                                AppRoute.MyList -> onMyListClearAll()
                                else            -> {}
                            }
                        },
                        header                = when (expandedRoute) {
                            AppRoute.Home      -> stringResource(R.string.nav_sports_today)
                            AppRoute.Guide     -> stringResource(R.string.nav_guide)
                            AppRoute.Movies    -> stringResource(R.string.nav_movies)
                            AppRoute.Series    -> stringResource(R.string.nav_series)
                            AppRoute.Recent    -> stringResource(R.string.nav_recent)
                            AppRoute.Search    -> stringResource(R.string.nav_search)
                            AppRoute.MyList    -> stringResource(R.string.nav_my_list)
                            AppRoute.Downloads -> stringResource(R.string.nav_downloads)
                            AppRoute.Picks     -> stringResource(R.string.nav_picks)
                            AppRoute.Device    -> stringResource(R.string.nav_device)
                            else               -> stringResource(R.string.common_categories)
                        },
                        isAndroidTV           = isAndroidTV,
                        onBackPressed         = onExitPanelToRail,
                        modifier              = Modifier.width(panelDp),
                        channelGroups         = if (expandedRoute == AppRoute.Guide) channelGroups else emptyList()
                    )
                }
                } // end Box weight(1f)
            } // end Column
        }
    }
}

// ── Main menu ─────────────────────────────────────────────────────────────────

@Composable
private fun MainMenu(
    modifier: Modifier,
    currentRoute: AppRoute,
    expandedRoute: AppRoute?,
    panelExpanded: Boolean,
    isLoadingEPG: Boolean,
    isLoadingVOD: Boolean,
    isLoadingSeries: Boolean,
    isSidebarFocused: Boolean,
    sidebarRefocusTick: Int = 0,
    showLabels: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onEnterPanel: () -> Unit = {},
    onEnterContent: () -> Unit = {},
    onExitPanelToRail: () -> Unit = {},
    onDpadRight: () -> Unit,
    onReopenPanel: (route: AppRoute) -> Unit = {},
    activeProfileName: String = "Default",
    activeProfileEmoji: String = "👤",
    onProfileClick: () -> Unit = {},
    xtreamUsername: String?,
    xtreamExpiry: String?,
    isLicensed: Boolean,
    trialDaysLeft: Int,
    vodRestricted: Boolean = false,
    hasJellyfinPlaylist: Boolean = false,
    hasDeviceFolders: Boolean = false,
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global
    val homeFocus        = remember { FocusRequester() }
    val recentFocus      = remember { FocusRequester() }
    val guideFocus       = remember { FocusRequester() }
    val moviesFocus      = remember { FocusRequester() }
    val seriesFocus      = remember { FocusRequester() }
    val catchupFocus     = remember { FocusRequester() }
    val picksFocus       = remember { FocusRequester() }
    val musicFocus       = remember { FocusRequester() }
    val deviceFocus      = remember { FocusRequester() }
    val searchFocus      = remember { FocusRequester() }
    val mylistFocus      = remember { FocusRequester() }
    val downloadsFocus   = remember { FocusRequester() }
    val settingsFocus    = remember { FocusRequester() }
    val profileInfoFocus = remember { FocusRequester() }

    val menuScope = rememberCoroutineScope()

    fun focusForRoute(route: AppRoute) {
        try {
            when (route.rootSection()) {
                AppRoute.Home        -> homeFocus.requestFocus()
                AppRoute.Recent      -> recentFocus.requestFocus()
                AppRoute.Guide       -> guideFocus.requestFocus()
                AppRoute.Movies      -> moviesFocus.requestFocus()
                AppRoute.Series      -> seriesFocus.requestFocus()
                AppRoute.CatchUp     -> catchupFocus.requestFocus()
                AppRoute.Picks       -> picksFocus.requestFocus()
                AppRoute.Music       -> musicFocus.requestFocus()
                AppRoute.Device      -> deviceFocus.requestFocus()
                AppRoute.Search      -> searchFocus.requestFocus()
                AppRoute.MyList      -> mylistFocus.requestFocus()
                AppRoute.Downloads   -> downloadsFocus.requestFocus()
                AppRoute.Settings    -> settingsFocus.requestFocus()
                else                 -> Unit
            }
        } catch (_: Exception) {}
    }

    // Focus the correct rail item when route changes (skip first composition —
    // MainScreen LaunchedEffect(Unit) handles initial focus)
    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute) {
        if (isFirstComposition) { isFirstComposition = false; return@LaunchedEffect }
        if (panelExpanded) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        if (isSidebarFocused) focusForRoute(currentRoute)
    }


    data class MenuEntry(
        val icon: ImageVector,
        val label: String,
        val route: AppRoute,
        val focusRequester: FocusRequester,
        val isLoading: Boolean = false,
        val hasSubPanel: Boolean = false,
        val onReopenPanel: (() -> Unit)? = null,
    )

    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val storedOrder  by context.getRailOrderFlow().collectAsState(initial = null)
    val storedHidden by context.getRailHiddenFlow().collectAsState(initial = null)
    val hiddenRoutes = remember(storedHidden) {
        storedHidden?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    val allMenuEntries = remember(isLoadingEPG, isLoadingVOD, isLoadingSeries, hasJellyfinPlaylist, hasDeviceFolders) {
        buildMap {
            put("Home",     MenuEntry(Icons.Default.SportsSoccer,  context.getString(R.string.nav_sports_today), AppRoute.Home,     homeFocus,    hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Home) }))
            put("Recent",   MenuEntry(Icons.Default.History,       context.getString(R.string.nav_recent),       AppRoute.Recent,   recentFocus,  hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Recent) }))
            put("Guide",    MenuEntry(Icons.Default.CalendarToday, context.getString(R.string.nav_guide),        AppRoute.Guide,    guideFocus,   isLoading = isLoadingEPG,    onReopenPanel = { onReopenPanel(AppRoute.Guide) },   hasSubPanel = true))
            put("Movies",   MenuEntry(Icons.Default.Movie,         context.getString(R.string.nav_movies),       AppRoute.Movies,   moviesFocus,  isLoading = isLoadingVOD,    onReopenPanel = { onReopenPanel(AppRoute.Movies) },  hasSubPanel = true))
            put("Series",   MenuEntry(Icons.Default.VideoLibrary,  context.getString(R.string.nav_series),       AppRoute.Series,   seriesFocus,  isLoading = isLoadingSeries, onReopenPanel = { onReopenPanel(AppRoute.Series) },  hasSubPanel = true))
            put("CatchUp",  MenuEntry(Icons.Default.Replay,        context.getString(R.string.nav_catch_up),     AppRoute.CatchUp,  catchupFocus, hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.CatchUp) }))
            put("Picks",    MenuEntry(Icons.Default.Stars,         context.getString(R.string.nav_picks),        AppRoute.Picks,    picksFocus,   hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Picks) }))
            if (hasJellyfinPlaylist) {
                put("Music", MenuEntry(Icons.Default.MusicNote,    context.getString(R.string.nav_music),        AppRoute.Music,    musicFocus,   hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Music) }))
            }
            if (hasDeviceFolders) {
                put("Device", MenuEntry(Icons.Default.Folder,      context.getString(R.string.nav_device),       AppRoute.Device,   deviceFocus,  hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Device) }))
            }
            put("Search",   MenuEntry(Icons.Default.Search,        context.getString(R.string.nav_search),       AppRoute.Search,   searchFocus,  hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.Search) }))
            put("MyList",   MenuEntry(Icons.Default.Bookmark,      context.getString(R.string.nav_my_list),      AppRoute.MyList,   mylistFocus,  hasSubPanel = true, onReopenPanel = { onReopenPanel(AppRoute.MyList) }))
            put("Settings", MenuEntry(Icons.Default.Settings,      context.getString(R.string.nav_settings),     AppRoute.Settings, settingsFocus))
        }
    }

    val menuItems = remember(storedOrder, storedHidden, allMenuEntries, vodRestricted) {
        val restrictedKeys = if (vodRestricted) setOf("Movies", "Series", "CatchUp") else emptySet()
        // Guide and Settings can never be hidden; apply user visibility prefs to others
        val alwaysVisible = setOf("Guide", "Settings")
        val entries = allMenuEntries.filterKeys { key ->
            key !in restrictedKeys && (key in alwaysVisible || key !in hiddenRoutes)
        }
        val order = storedOrder
        if (order.isNullOrBlank()) {
            entries.values.toList()
        } else {
            val names = order.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val ordered = names.mapNotNull { entries[it] }
            val missing = entries.values.filter { entry ->
                names.none { n -> entries[n] == entry }
            }
            ordered + missing
        }
    }

    // Explicit tick-based redirect — fires only when MainScreen increments it
    // Replaces onFocusChanged redirect which fired spuriously during normal traversal
    LaunchedEffect(sidebarRefocusTick) {
        if (sidebarRefocusTick > 0) {
            kotlinx.coroutines.delay(16)
            focusForRoute(expandedRoute ?: currentRoute)
        }
    }

    val firstRailFR = menuItems.first().focusRequester
    val lastRailFR  = menuItems.last().focusRequester

    Column(
        modifier = modifier
            .background(sTheme.railBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
        val headerHeight = (56 * textScale).dp
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp),
            contentAlignment = if (nsTheme.identity.logoMode == LogoMode.IMAGE && nsTheme.identity.logoUrl != null)
                Alignment.Center else Alignment.CenterStart
        ) {
            if (nsTheme.identity.logoMode == LogoMode.IMAGE && nsTheme.identity.logoUrl != null) {
                AsyncImage(
                    model             = nsTheme.identity.logoUrl,
                    contentDescription = nsTheme.identity.appName,
                    contentScale      = ContentScale.Fit,
                    modifier          = Modifier.fillMaxHeight().padding(vertical = 8.dp)
                )
            } else {
                Text(
                    text  = nsTheme.identity.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = gTheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        HorizontalDivider(color = sTheme.divider)

        ProfileInfo(
            showLabels         = showLabels,
            activeProfileName  = activeProfileName,
            activeProfileEmoji = activeProfileEmoji,
            onProfileClick     = onProfileClick,
            focusRequester     = profileInfoFocus,
            upFR               = lastRailFR,
            downFR             = firstRailFR,
        )

        HorizontalDivider(color = sTheme.divider)

        val menuScrollState = rememberScrollState()
        Column(
            modifier            = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(menuScrollState)
                .padding(vertical = 8.dp, horizontal = if (showLabels) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Wrap-around: top ↔ bottom via profileInfoFocus
            menuItems.forEachIndexed { idx, entry ->
                val isFirst = idx == 0
                val isLast  = idx == menuItems.lastIndex
                val prevFR  = if (isFirst) profileInfoFocus else menuItems[idx - 1].focusRequester
                val nextFR  = if (isLast)  profileInfoFocus else menuItems[idx + 1].focusRequester
                MenuItem(
                    icon           = entry.icon,
                    label          = entry.label,
                    route          = entry.route,
                    currentRoute   = currentRoute,
                    expandedRoute  = expandedRoute,
                    panelExpanded  = panelExpanded,
                    showLabels     = showLabels,
                    hasSubPanel    = entry.hasSubPanel,
                    focusRequester = entry.focusRequester,
                    onDpadRight    = onDpadRight,
                    onClick        = { onNavigate(entry.route) },
                    onExitPanelToRail = onExitPanelToRail,
                    onReopenPanel  = entry.onReopenPanel,
                    isLoading      = entry.isLoading,
                    upFR           = prevFR,
                    downFR         = nextFR
                )
            }
        }
    }
}

// ── Menu item ─────────────────────────────────────────────────────────────────

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    route: AppRoute,
    currentRoute: AppRoute,
    expandedRoute: AppRoute?,
    panelExpanded: Boolean,
    showLabels: Boolean,
    hasSubPanel: Boolean = false,
    focusRequester: FocusRequester,
    onDpadRight: () -> Unit,
    onClick: () -> Unit,
    onEnterPanel: () -> Unit = {},
    onEnterContent: () -> Unit = {},
    onExitPanelToRail: () -> Unit = {},
    onReopenPanel: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    isLoading: Boolean = false,
    upFR: FocusRequester? = null,
    downFR: FocusRequester? = null
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global
    var isFocused by remember { mutableStateOf(false) }
    val itemScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness    = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "itemScale"
    )
    val interaction = remember { MutableInteractionSource() }

    val isCurrentRoute = currentRoute.rootSection() == route.rootSection()
    val isBright = isCurrentRoute && (!panelExpanded || expandedRoute == route)
    val isDimmed = isCurrentRoute && panelExpanded && expandedRoute != route

    val bgColor = when {
        isBright && isFocused -> sTheme.railItemFocusedBg  // hover bg takes priority so cursor location is clear
        isBright  -> sTheme.railItemActiveBg
        isFocused -> sTheme.railItemFocusedBg
        else      -> androidx.compose.ui.graphics.Color.Transparent
    }
    // Extra border when hovering over the already-active item
    val showActiveFocusBorder = isBright && isFocused
    val iconTint = when {
        isBright  -> sTheme.railIconActive
        isDimmed  -> sTheme.railIcon.copy(alpha = 0.4f)
        isFocused -> sTheme.railIconActive
        else      -> sTheme.railIcon
    }
    val textColor = when {
        isBright  -> sTheme.categoryTextSelected
        isDimmed  -> sTheme.categoryText.copy(alpha = 0.4f)
        isFocused -> sTheme.categoryTextFocused
        else      -> sTheme.categoryText
    }

    val effectiveClick: () -> Unit = {
        if (isCurrentRoute && !panelExpanded && route.hasCategoryPanel && onReopenPanel != null) onReopenPanel()
        else onClick()
    }

    val sharedModifier = Modifier
        .focusRequester(focusRequester)
        .focusProperties {
            // Wrap-around up/down declared by caller
            if (upFR != null) up = upFR
            if (downFR != null) down = downFR
            // Right: navigate to this route first if not current,
            // then open panel or go to content on next press
        }
        .onFocusChanged { state ->
            isFocused = state.isFocused
            if (state.isFocused) onFocused()
        }
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) when (e.key) {
                Key.DirectionRight -> {
                    if (!isCurrentRoute) {
                        onClick()
                        // If this route has a panel, also enter it
                        if (route.hasCategoryPanel) onEnterPanel()
                        true
                    } else if (route.hasCategoryPanel && !panelExpanded && onReopenPanel != null) { onReopenPanel(); true }
                    else { onDpadRight(); true }
                }
                Key.DirectionLeft -> {
                    if (panelExpanded && isCurrentRoute) onExitPanelToRail()
                    true
                }
                else -> false
            } else false
        }
        .clickable(
            interactionSource = interaction,
            indication        = null,
            onClick           = effectiveClick
        )

    if (showLabels) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .then(if (isBright && isFocused) Modifier.border(2.dp, sTheme.railIconActive, RoundedCornerShape(8.dp))
                else if (isBright) Modifier.border(1.5.dp, sTheme.railIconActive.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                else Modifier)
                .then(sharedModifier)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = iconTint)
            } else {
                Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = when {
                    isBright -> when (nsTheme.typography.weight) {
                        "bold", "semibold" -> androidx.compose.ui.text.font.FontWeight.Bold
                        else               -> androidx.compose.ui.text.font.FontWeight.SemiBold
                    }
                    nsTheme.typography.weight == "bold" || nsTheme.typography.weight == "semibold" ->
                        androidx.compose.ui.text.font.FontWeight.SemiBold
                    else -> androidx.compose.ui.text.font.FontWeight.Normal
                },
                color      = textColor,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .then(if (isBright && isFocused) Modifier.border(2.dp, sTheme.railIconActive, RoundedCornerShape(12.dp))
                else if (isBright) Modifier.border(1.5.dp, sTheme.railIconActive.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                else Modifier)
                .then(sharedModifier),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = iconTint)
            } else {
                Icon(imageVector = icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Category panel ────────────────────────────────────────────────────────────

@Composable
private fun CategoryPanel(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    panelFR: FocusRequester,
    onPanelFocusChanged: (Boolean) -> Unit = {},
    focusTick: Int = 0,
    onRequestContentFocus: () -> Unit = {},
    onRequestRailFocus: () -> Unit = {},
    onSearchRequest: () -> Unit = {},
    onFavouritesSelected: () -> Unit = {},
    showSearch: Boolean = true,
    showFavourites: Boolean = true,
    showClearAll: Boolean = false,
    onClearAll: () -> Unit = {},
    header: String = "Categories",
    isAndroidTV: Boolean = false,
    onBackPressed: () -> Unit = {},
    supportsKeyboardSearch: Boolean = false,
    modifier: Modifier = Modifier,
    channelGroups: List<ChannelGroupEntity> = emptyList()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    val headerHeight = (56 * textScale).dp

    val allFR         = remember { FocusRequester() }
    val searchFR      = remember { FocusRequester() }
    val favouritesFR  = remember { FocusRequester() }
    val categoryFocusMap = remember { androidx.compose.runtime.mutableStateMapOf<String, FocusRequester>() }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Stable index constants so focusTick can scroll to the target before requesting focus
    val favouritesIndex = if (showSearch) 1 else 0
    val groupsOffset    = if (showFavourites) channelGroups.size else 0
    val allIndex        = (if (showSearch) 1 else 0) + (if (showFavourites) 1 else 0) + groupsOffset
    val fixedCount      = allIndex + 1

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        val targetIndex = when {
            selectedCategory == "__search__"     -> 0
            selectedCategory == "__favourites__" -> favouritesIndex
            selectedCategory?.startsWith("__grp_") == true -> {
                val grpIdx = channelGroups.indexOfFirst { "__grp_${it.id}" == selectedCategory }
                if (grpIdx >= 0) favouritesIndex + 1 + grpIdx else allIndex
            }
            selectedCategory == null -> allIndex
            else -> {
                val catIdx = categories.indexOf(selectedCategory)
                if (catIdx >= 0) fixedCount + catIdx else allIndex
            }
        }
        // Scroll one item above the target so the selected item isn't flush against the top
        val scrollAnchor = (targetIndex - 1).coerceAtLeast(0)
        listState.animateScrollToItem(scrollAnchor)
        kotlinx.coroutines.delay(150)
        val target = when (selectedCategory) {
            "__search__"     -> searchFR
            "__favourites__" -> favouritesFR
            null             -> allFR
            else             -> categoryFocusMap[selectedCategory] ?: allFR
        }
        try { target.requestFocus(); onPanelFocusChanged(true) }
        catch (e: Exception) { android.util.Log.e("NexStreamPanel", "requestFocus FAILED: ${e.message}") }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(sTheme.panelBackground)
            .focusRequester(panelFR)
            .onFocusChanged { fs -> onPanelFocusChanged(fs.hasFocus) }
    ) {
        if (!isAndroidTV) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .clickable { onBackPressed() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                    tint = sTheme.categoryText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = header, style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = sTheme.categoryText)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text       = header,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color      = sTheme.categoryText,
                    modifier   = Modifier.padding(horizontal = 10.dp)
                )
            }
        }

        HorizontalDivider(color = sTheme.divider)

        androidx.compose.foundation.lazy.LazyColumn(
            state    = listState,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
        ) {
            if (showSearch) {
                item(key = "__search__") {
                    PanelItem(
                        text                  = stringResource(R.string.nav_search),
                        isSelected            = selectedCategory == "__search__",
                        focusRequester        = searchFR,
                        onRequestContentFocus = onRequestContentFocus,
                        onRequestRailFocus    = onRequestRailFocus,
                        onFocused             = { onPanelFocusChanged(true) },
                        onClick               = { onSearchRequest() }
                    )
                }
            }
            if (showFavourites) {
                item(key = "__favourites__") {
                    PanelItem(
                        text                  = "Favourites",
                        isSelected            = selectedCategory == "__favourites__",
                        focusRequester        = favouritesFR,
                        onRequestContentFocus = onRequestContentFocus,
                        onRequestRailFocus    = onRequestRailFocus,
                        onFocused             = { onPanelFocusChanged(true) },
                        onClick               = { onFavouritesSelected() }
                    )
                }
                // Channel groups — shown as sub-items under Favourites
                items(channelGroups, key = { "__grp_${it.id}" }) { group ->
                    val grpKey = "__grp_${group.id}"
                    val fr = remember { FocusRequester() }
                    SideEffect { categoryFocusMap[grpKey] = fr }
                    PanelItem(
                        text                  = group.name,
                        isSelected            = selectedCategory == grpKey,
                        focusRequester        = fr,
                        onRequestContentFocus = onRequestContentFocus,
                        onRequestRailFocus    = onRequestRailFocus,
                        onFocused             = { onPanelFocusChanged(true) },
                        onClick               = { onCategorySelected(grpKey) }
                    )
                }
            }
            item(key = "__all__") {
                PanelItem(
                    text                  = "All",
                    isSelected            = selectedCategory == null,
                    focusRequester        = allFR,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onFocused             = { onPanelFocusChanged(true) },
                    onDoubleClick         = if (supportsKeyboardSearch) { { onSearchRequest() } } else { {} },
                    onClick               = { onCategorySelected(null) }
                )
            }
            itemsIndexed(categories, key = { idx, cat -> "${idx}_${cat}" }) { _, cat ->
                val fr = remember { FocusRequester() }
                SideEffect { categoryFocusMap[cat] = fr }
                PanelItem(
                    text                  = cat,
                    isSelected            = selectedCategory == cat,
                    focusRequester        = fr,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onFocused             = { onPanelFocusChanged(true) },
                    onDoubleClick         = if (supportsKeyboardSearch) { { onSearchRequest() } } else { {} },
                    onClick               = { onCategorySelected(cat) }
                )
            }
        }
        if (showClearAll) {
            HorizontalDivider(
                color = sTheme.divider,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            val clearAllFR = remember { FocusRequester() }
            var clearAllFocused by remember { mutableStateOf(false) }
            val clearScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (clearAllFocused) 1.04f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness    = androidx.compose.animation.core.Spring.StiffnessHigh
                ),
                label = "clearScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = clearScale; scaleY = clearScale }
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (clearAllFocused) MaterialTheme.colorScheme.errorContainer
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .focusRequester(clearAllFR)
                    .onFocusChanged { clearAllFocused = it.isFocused; if (it.isFocused) onPanelFocusChanged(true) }
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClearAll(); true }
                            Key.DirectionLeft  -> { onRequestRailFocus(); true }
                            Key.DirectionRight -> { onRequestContentFocus(); true }
                            else -> false
                        }
                    }
                    .clickable { onClearAll() }
                    .focusable()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = if (clearAllFocused) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text  = "Clear All",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (clearAllFocused) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (supportsKeyboardSearch) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "Double-click to search",
                    style = MaterialTheme.typography.labelSmall,
                    color = sTheme.categoryText.copy(alpha = 0.35f),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelItem(
    text: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onRequestContentFocus: () -> Unit,
    onRequestRailFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onFocused: () -> Unit = {},
    prevFR: FocusRequester? = null,
    nextFR: FocusRequester? = null,
    maxLines: Int = 2,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    var isFocused by remember { mutableStateOf(false) }
    var longPressConsumed by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val itemScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness    = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "itemScale"
    )
    val interaction = remember { MutableInteractionSource() }

    val bgColor = when {
        isFocused  -> sTheme.categoryFocusedBg   // hover always wins so cursor location is clear
        isSelected -> sTheme.categorySelectedBg
        else       -> androidx.compose.ui.graphics.Color.Transparent
    }
    val textColor = when {
        isFocused  -> sTheme.categoryTextFocused
        isSelected -> sTheme.categoryTextSelected
        else       -> sTheme.categoryText
    }
    val w = nsTheme.typography.weight
    val textWeight = when {
        isSelected -> when (w) {
            "bold", "semibold" -> androidx.compose.ui.text.font.FontWeight.Bold
            else               -> androidx.compose.ui.text.font.FontWeight.SemiBold
        }
        w == "bold" || w == "semibold" -> androidx.compose.ui.text.font.FontWeight.SemiBold
        else -> androidx.compose.ui.text.font.FontWeight.Normal
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isSelected && isFocused) Modifier.border(2.dp, sTheme.categoryTextSelected, RoundedCornerShape(8.dp))
                else if (isSelected) Modifier.border(1.5.dp, sTheme.categoryTextSelected.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (prevFR != null) up   = prevFR
                if (nextFR != null) down = nextFR
            }
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when {
                        e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime in 1L..400L) {
                                onDoubleClick()
                                lastClickTime = 0L
                            } else {
                                onClick()
                                lastClickTime = now
                            }
                            true
                        }
                        e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 && !longPressConsumed -> {
                            longPressConsumed = true; onLongClick(); true
                        }
                        e.type == KeyEventType.KeyUp && longPressConsumed -> {
                            longPressConsumed = false; true
                        }
                        else -> false
                    }
                    Key.DirectionRight ->
                        if (e.type == KeyEventType.KeyDown) { onRequestContentFocus(); true } else false
                    Key.DirectionLeft  ->
                        if (e.type == KeyEventType.KeyDown) { onRequestRailFocus(); true } else false
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick,
                onDoubleClick     = onDoubleClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = text,
                style      = MaterialTheme.typography.bodyMedium,
                color      = textColor,
                fontWeight = textWeight,
                maxLines   = maxLines,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            if (trailingContent != null) {
                Spacer(Modifier.width(6.dp))
                trailingContent()
            }
        }
    }
}

// ── Account info ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileInfo(
    showLabels: Boolean,
    activeProfileName: String,
    activeProfileEmoji: String,
    onProfileClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    upFR: FocusRequester? = null,
    downFR: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val itemScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness    = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "itemScale"
    )

    if (showLabels) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .background(if (isFocused) sTheme.railItemFocusedBg else sTheme.accountBarBg)
                .focusRequester(focusRequester)
                .onFocusChanged { fs -> isFocused = fs.isFocused; if (fs.isFocused) onFocused() }
                .focusable(true)
                .onKeyEvent { e ->
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) when (e.key) {
                        androidx.compose.ui.input.key.Key.DirectionUp -> { try { upFR?.requestFocus() } catch (_: Exception) {}; true }
                        androidx.compose.ui.input.key.Key.DirectionDown -> { try { downFR?.requestFocus() } catch (_: Exception) {}; true }
                        androidx.compose.ui.input.key.Key.Enter,
                        androidx.compose.ui.input.key.Key.DirectionCenter,
                        androidx.compose.ui.input.key.Key.NumPadEnter -> { onProfileClick(); true }
                        else -> false
                    } else false
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onProfileClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gTheme.primary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(activeProfileEmoji, style = MaterialTheme.typography.bodyMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(activeProfileName, style = MaterialTheme.typography.bodySmall,
                    color = sTheme.accountBarText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Switch profile", style = MaterialTheme.typography.labelSmall,
                    color = sTheme.accountBarTextSecondary, maxLines = 1)
            }
            Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp), tint = sTheme.accountBarTextSecondary)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) sTheme.railItemFocusedBg else sTheme.accountBarBg)
                .focusRequester(focusRequester)
                .onFocusChanged { fs -> isFocused = fs.isFocused; if (fs.isFocused) onFocused() }
                .focusable(true)
                .onKeyEvent { e ->
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) when (e.key) {
                        androidx.compose.ui.input.key.Key.DirectionUp -> { try { upFR?.requestFocus() } catch (_: Exception) {}; true }
                        androidx.compose.ui.input.key.Key.DirectionDown -> { try { downFR?.requestFocus() } catch (_: Exception) {}; true }
                        androidx.compose.ui.input.key.Key.Enter,
                        androidx.compose.ui.input.key.Key.DirectionCenter,
                        androidx.compose.ui.input.key.Key.NumPadEnter -> { onProfileClick(); true }
                        else -> false
                    } else false
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onProfileClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(gTheme.primary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(activeProfileEmoji, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ── Settings panel ────────────────────────────────────────────────────────────

@Composable
private fun SettingsPanel(
    selectedRoute: AppRoute?,
    onItemSelected: (AppRoute) -> Unit,
    panelFR: FocusRequester,
    onPanelFocusChanged: (Boolean) -> Unit,
    focusTick: Int,
    onRequestContentFocus: () -> Unit,
    onRequestRailFocus: () -> Unit,
    isAndroidTV: Boolean = false,
    onBackPressed: () -> Unit = {},
    showSyncSettings: Boolean = true,
    modifier: Modifier = Modifier
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    val headerHeight = (56 * textScale).dp

    data class SettingsEntry(val label: String, val route: AppRoute, val fr: FocusRequester)

    val playlistsFR   = remember { FocusRequester() }
    val appearanceFR    = remember { FocusRequester() }
    val playerFR        = remember { FocusRequester() }
    val syncFR          = remember { FocusRequester() }
    val licenceFR       = remember { FocusRequester() }
    val accountFR       = remember { FocusRequester() }
    val profilesFR      = remember { FocusRequester() }
    val navigationFR    = remember { FocusRequester() }
    val aboutFR         = remember { FocusRequester() }
    val channelGroupsFR = remember { FocusRequester() }
    val languageFR      = remember { FocusRequester() }

    val networkFR = remember { FocusRequester() }

    val entries = remember(showSyncSettings) {
        buildList {
            add(SettingsEntry("About",          AppRoute.SettingsAbout,          aboutFR))
            add(SettingsEntry("Account",        AppRoute.SettingsAccount,        accountFR))
            add(SettingsEntry("Appearance",     AppRoute.SettingsAppearance,     appearanceFR))
            add(SettingsEntry("Channel Groups", AppRoute.SettingsChannelGroups,  channelGroupsFR))
            add(SettingsEntry("Language",       AppRoute.SettingsLanguage,       languageFR))
            add(SettingsEntry("Licence",        AppRoute.SettingsLicence,        licenceFR))
            add(SettingsEntry("Navigation",     AppRoute.SettingsNavigation,     navigationFR))
            add(SettingsEntry("Network",        AppRoute.SettingsProxy,          networkFR))
            add(SettingsEntry("Player",         AppRoute.SettingsPlayer,         playerFR))
            add(SettingsEntry("Playlists",      AppRoute.SettingsPlaylists,      playlistsFR))
            add(SettingsEntry("Profiles",       AppRoute.SettingsProfiles,       profilesFR))
            if (showSyncSettings) add(SettingsEntry("Sync and Update", AppRoute.SettingsSyncSettings, syncFR))
        }
    }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        val target = entries.firstOrNull { it.route == selectedRoute }?.fr ?: entries.first().fr
        try { target.requestFocus(); onPanelFocusChanged(true) } catch (_: Exception) {}
    }

    val scrollState = androidx.compose.foundation.rememberScrollState(0)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(sTheme.panelBackground)
            .focusRequester(panelFR)
            .onFocusChanged { fs -> onPanelFocusChanged(fs.hasFocus) }
    ) {
        if (!isAndroidTV) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .clickable { onBackPressed() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                    tint = sTheme.categoryText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.nav_settings), style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = sTheme.categoryText)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text       = stringResource(R.string.nav_settings),
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color      = sTheme.categoryText,
                    modifier   = Modifier.padding(horizontal = 10.dp)
                )
            }
        }

        HorizontalDivider(color = sTheme.divider)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 4.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                val prevFR = if (index > 0) entries[index - 1].fr else entries.last().fr
                val nextFR = if (index < entries.lastIndex) entries[index + 1].fr else entries.first().fr
                PanelItem(
                    text                  = entry.label,
                    isSelected            = selectedRoute == entry.route,
                    focusRequester        = entry.fr,
                    prevFR                = prevFR,
                    nextFR                = nextFR,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onFocused             = { onPanelFocusChanged(true) },
                    onClick               = { onItemSelected(entry.route) }
                )
            }
        }
    }
}

// ── CatchUp date panel ───────────────────────────────────────────────────────
// Shows: All, then available dates (Today, Yesterday, Mon 20th etc)
// selectedCategory = null means "All", else date ms as string

@Composable
private fun CatchUpDatePanel(
    availableDates:       List<Long>,
    channels:             List<app.nexstream.player.data.local.entity.ChannelEntity> = emptyList(),
    selectedChannel:      app.nexstream.player.data.local.entity.ChannelEntity? = null,
    onChannelSelected:    (app.nexstream.player.data.local.entity.ChannelEntity) -> Unit = {},
    selectedDateKey:      String?,
    onDateSelected:       (String?) -> Unit,
    panelFR:              FocusRequester,
    onPanelFocusChanged:  (Boolean) -> Unit,
    focusTick:            Int,
    onRequestContentFocus: () -> Unit,
    onRequestRailFocus:    () -> Unit,
    isLoading:            Boolean = false,
    onSearchRequest:      () -> Unit = {},
    isAndroidTV:          Boolean = false,
    onBackPressed:        () -> Unit = {},
    modifier:             Modifier = Modifier
) {
    val nsTheme     = LocalNexStreamTheme.current
    val sTheme      = nsTheme.sidebar
    val textScale   = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    val headerHeight = (56 * textScale).dp
    val today       = remember { app.nexstream.player.ui.screens.catchup.todayStart() }

    // FocusRequesters: index 0 = All, 1..n = dates
    val allFR    = remember { FocusRequester() }
    val dateFRs  = availableDates.map { remember(it) { FocusRequester() } }
    val scrollState = androidx.compose.foundation.rememberScrollState(0)

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        try {
            if (selectedDateKey == null) allFR.requestFocus()
            else {
                val idx = availableDates.indexOfFirst { it.toString() == selectedDateKey }
                dateFRs.getOrNull(idx)?.requestFocus() ?: allFR.requestFocus()
            }
            onPanelFocusChanged(true)
        } catch (_: Exception) {}
    }

    val favouritesFR = remember { FocusRequester() }
    val allEntries: List<Pair<String, FocusRequester>> = buildList {
        add(Pair("Favourites", favouritesFR))
        add(Pair("All", allFR))
        availableDates.forEachIndexed { i, ms ->
            add(Pair(app.nexstream.player.ui.screens.catchup.catchUpDateLabel(ms, today), dateFRs[i]))
        }
    }
    val entryKeys: List<String?> = buildList {
        add("FAVOURITES")
        add(null) // All
        availableDates.forEach { ms -> add(ms.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(Modifier.focusRequester(panelFR))
            .onFocusChanged { fs: androidx.compose.ui.focus.FocusState -> onPanelFocusChanged(fs.hasFocus) }
    ) {
        if (!isAndroidTV) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .clickable { onBackPressed() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                    tint = sTheme.categoryText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.nav_catch_up), style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = sTheme.categoryText)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(headerHeight), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.nav_catch_up),
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color      = sTheme.categoryText,
                    modifier   = Modifier.padding(horizontal = 10.dp))
            }
        }
        HorizontalDivider(color = sTheme.divider)


        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(vertical = 4.dp)
        ) {
            allEntries.forEachIndexed { index, (label, fr) ->
                val categoryKey = entryKeys[index]
                val isSelected  = selectedDateKey == categoryKey
                val prevFR = if (index > 0) allEntries[index - 1].second else allEntries.last().second
                val nextFR = if (index < allEntries.lastIndex) allEntries[index + 1].second else allEntries.first().second


                PanelItem(
                    text           = label,
                    isSelected     = isSelected,
                    focusRequester = fr,
                    prevFR         = prevFR,
                    nextFR         = nextFR,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onFocused     = { onPanelFocusChanged(true) },
                    onDoubleClick = { onSearchRequest() },
                    onClick       = { onDateSelected(categoryKey) },
                    trailingContent = if (isLoading && categoryKey == null) {
                        { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp) }
                    } else null
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = "Double-click to search",
                style = MaterialTheme.typography.labelSmall,
                color = sTheme.categoryText.copy(alpha = 0.35f),
                maxLines = 1
            )
        }
    }
}