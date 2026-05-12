package app.nexstream.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.screens.main.hasCategoryPanel
import app.nexstream.player.ui.screens.main.isSettings
import app.nexstream.player.ui.screens.main.rootSection
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.nexstream.player.R
import app.nexstream.player.ui.theme.LocalNexStreamTheme


private val FULL_SIDEBAR_WIDTH = 200.dp
private val RAIL_WIDTH         = 64.dp
private val PANEL_WIDTH        = 180.dp


// Reduced from 140ms — snappier panel open/close
private const val ANIM_PANEL_MS  = 140

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
    onSearchRequest: () -> Unit = {},
    onFavouritesSelected: (route: AppRoute) -> Unit = {},
    xtreamUsername: String? = null,
    xtreamExpiry: String? = null,
    isLicensed: Boolean = true,
    trialDaysLeft: Int = 0
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global
    val sidebarScope = rememberCoroutineScope()

    val categories = remember(expandedRoute, guideCategories, movieCategories, seriesCategories) {
        when (expandedRoute) {
            AppRoute.Guide   -> guideCategories
            AppRoute.Movies  -> movieCategories
            AppRoute.Series  -> seriesCategories
            AppRoute.CatchUp -> emptyList()
            else             -> emptyList()
        }
    }
    val selectedCategory = when (expandedRoute) {
        AppRoute.Guide   -> selectedGuideCategory
        AppRoute.Movies  -> selectedMovieCategory
        AppRoute.Series  -> selectedSeriesCategory
        AppRoute.CatchUp -> selectedCatchUpDateKey
        else             -> null
    }
    val onCategorySelected: (String?) -> Unit = when (expandedRoute) {
        AppRoute.Guide   -> onGuideCategorySelected
        AppRoute.Movies  -> onMovieCategorySelected
        AppRoute.Series  -> onSeriesCategorySelected
        AppRoute.CatchUp -> onCatchUpDateSelected
        else             -> ({})
    }

    // Single Animatable drives all panel transitions: 0f=closed, 1f=open
    // Runs on render thread via graphicsLayer — zero recomposition during animation
    val panelProgress = remember { Animatable(if (panelExpanded) 1f else 0f) }
    LaunchedEffect(panelExpanded) {
        panelProgress.animateTo(
            targetValue   = if (panelExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = ANIM_PANEL_MS, easing = FastOutSlowInEasing)
        )
    }

    val density = LocalDensity.current
    val panelWidthPx = with(density) { PANEL_WIDTH.roundToPx() }
    val railWidthPx = with(density) { RAIL_WIDTH.roundToPx() }
    val fullWidthPx = with(density) { FULL_SIDEBAR_WIDTH.roundToPx() }


    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(sTheme.background)
    ) {
        // ── Rail ────────────────────────────────────────────────────────────
        MainMenu(
            modifier           = Modifier
                .width(168.dp)
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
            panelProgress      = panelProgress.value,
            onNavigate         = { route ->
                if (route.hasCategoryPanel) {
                    onPanelExpandedChange(true, route)
                    onNavigate(route)
                } else {
                    onPanelExpandedChange(false, null)
                    onNavigate(route)
                }
            },
            onEnterPanel     = onEnterPanel,
            onEnterContent   = onEnterContent,
            onExitPanelToRail = onExitPanelToRail,
            onDpadRight = {
                if (panelExpanded) {
                    // Panel is open — move focus into it
                    onEnterPanel()
                } else {
                    onEnterContent()
                }
            },
            onReopenPanel = { route ->
                onPanelExpandedChange(true, route)
                // Focus moves to panel when user explicitly reopens it via rail right-key
                onEnterPanel()
            },
            activeProfileName  = activeProfileName,
            activeProfileEmoji = activeProfileEmoji,
            onProfileClick     = onProfileClick,
            xtreamUsername   = xtreamUsername,
            xtreamExpiry     = xtreamExpiry,
            isLicensed       = isLicensed,
            trialDaysLeft    = trialDaysLeft
        )

        // ── Panel ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(with(density) { (PANEL_WIDTH.toPx() * panelProgress.value).toDp() })
                .fillMaxHeight()
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .width(PANEL_WIDTH)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = -panelWidthPx * (1f - panelProgress.value)
                    }
            ) {
                when {
                    expandedRoute?.isSettings == true -> SettingsPanel(
                        selectedRoute         = selectedSettingsRoute,
                        onItemSelected        = onSettingsItemSelected,
                        panelFR               = panelFR,
                        onPanelFocusChanged   = onPanelFocusChanged,
                        focusTick             = panelFocusTick,
                        onRequestContentFocus = { onEnterContent() },
                        onRequestRailFocus    = { onExitPanelToRail() },
                        modifier              = Modifier.width(PANEL_WIDTH)
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
                        modifier              = Modifier.width(PANEL_WIDTH)
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
                        showSearch            = expandedRoute != AppRoute.Guide,
                        modifier              = Modifier.width(PANEL_WIDTH)
                    )
                }
            }
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
    panelFocusTick: Int = 0,      // increment to focus selected category item
    showLabels: Boolean,
    panelProgress: Float = 0f,
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
    trialDaysLeft: Int
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val gTheme  = nsTheme.global
    val recentFocus   = remember { FocusRequester() }
    val guideFocus    = remember { FocusRequester() }
    val moviesFocus   = remember { FocusRequester() }
    val seriesFocus   = remember { FocusRequester() }
    val catchupFocus  = remember { FocusRequester() }
    val searchFocus   = remember { FocusRequester() }
    val mylistFocus   = remember { FocusRequester() }
    val downloadsFocus  = remember { FocusRequester() }
    val settingsFocus   = remember { FocusRequester() }
    val profileInfoFocus = remember { FocusRequester() }

    val menuScope = rememberCoroutineScope()

    fun focusForRoute(route: AppRoute) {
        try {
            when (route.rootSection()) {
                AppRoute.Recent      -> recentFocus.requestFocus()
                AppRoute.Guide       -> guideFocus.requestFocus()
                AppRoute.Movies      -> moviesFocus.requestFocus()
                AppRoute.Series      -> seriesFocus.requestFocus()
                AppRoute.CatchUp     -> catchupFocus.requestFocus()
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

    val menuItems = remember(isLoadingEPG, isLoadingVOD, isLoadingSeries) {
        listOf(
            MenuEntry(Icons.Default.History,       "Recent",    AppRoute.Recent,     recentFocus),
            MenuEntry(Icons.Default.CalendarToday, "Guide",     AppRoute.Guide,      guideFocus,     isLoading = isLoadingEPG,    onReopenPanel = { onReopenPanel(AppRoute.Guide) },   hasSubPanel = true),
            MenuEntry(Icons.Default.Movie,         "Movies",    AppRoute.Movies,     moviesFocus,    isLoading = isLoadingVOD,    onReopenPanel = { onReopenPanel(AppRoute.Movies) },  hasSubPanel = true),
            MenuEntry(Icons.Default.VideoLibrary,  "Series",    AppRoute.Series,     seriesFocus,    isLoading = isLoadingSeries, onReopenPanel = { onReopenPanel(AppRoute.Series) },  hasSubPanel = true),
            MenuEntry(Icons.Default.Replay,        "Catch Up",  AppRoute.CatchUp,    catchupFocus),
            MenuEntry(Icons.Default.Search,        "Search",    AppRoute.Search,     searchFocus),
            MenuEntry(Icons.Default.Bookmark,      "My List",   AppRoute.MyList,     mylistFocus),
            MenuEntry(Icons.Default.Download,      "Downloads", AppRoute.Downloads,  downloadsFocus),
            MenuEntry(Icons.Default.Settings,      "Settings",  AppRoute.Settings,   settingsFocus,
            ),
        )
    }

    // Explicit tick-based redirect — fires only when MainScreen increments it
    // Replaces onFocusChanged redirect which fired spuriously during normal traversal
    LaunchedEffect(sidebarRefocusTick) {
        if (sidebarRefocusTick > 0) {
            kotlinx.coroutines.delay(16)
            focusForRoute(expandedRoute ?: currentRoute)
        }
    }

    Column(
        modifier = modifier
            .background(sTheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
        val headerHeight = (56 * textScale).dp
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text  = nsTheme.identity.appName,
                style = MaterialTheme.typography.headlineSmall,
                color = gTheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }

        HorizontalDivider(color = sTheme.divider)

        val menuScrollState = rememberScrollState()
        Column(
            modifier            = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(menuScrollState)
                .padding(vertical = 8.dp, horizontal = if (showLabels) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Wrap-around: top ↔ bottom
            val firstRailFR = menuItems.first().focusRequester
            val lastRailFR  = menuItems.last().focusRequester
            menuItems.forEachIndexed { idx, entry ->
                val isFirst = idx == 0
                val isLast  = idx == menuItems.lastIndex
                val prevFR  = if (isFirst) lastRailFR  else menuItems[idx - 1].focusRequester
                val nextFR  = if (isLast)  firstRailFR else menuItems[idx + 1].focusRequester
                // Right focus: panel open → panel; panel closed → content (via onDpadRight)
                // We keep onDpadRight for right because it needs to handle the open/close logic
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

        ProfileInfo(
            showLabels         = showLabels,
            activeProfileName  = activeProfileName,
            activeProfileEmoji = activeProfileEmoji,
            onProfileClick     = onProfileClick,
            focusRequester     = profileInfoFocus,
            upFR               = settingsFocus,
            downFR             = recentFocus,
        )
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
        isBright && isFocused -> sTheme.railItemActiveBg  // selected + focused: slightly brighter border shows distinction
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
                fontWeight = if (isBright) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Normal,
                color      = textColor,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            if (hasSubPanel) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = if (isBright || isFocused) 1f else 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
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
    focusTick: Int = 0,  // increment to focus the selected item
    onRequestContentFocus: () -> Unit = {},
    onRequestRailFocus: () -> Unit = {},
    onSearchRequest: () -> Unit = {},
    onFavouritesSelected: () -> Unit = {},
    showSearch: Boolean = true,
    modifier: Modifier = Modifier
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    val headerHeight = (56 * textScale).dp

    val panelScope   = rememberCoroutineScope()

    // FocusRequesters — fixed items + map for categories (all always composed now)
    val allFR           = remember { FocusRequester() }
    val searchFR        = remember { FocusRequester() }
    val favouritesFR    = remember { FocusRequester() }
    val categoryFocusMap = remember { androidx.compose.runtime.mutableStateMapOf<String, FocusRequester>() }

    // isFocused on the panel container is NEVER true (Compose routes directly to
    // first child). Drive redirect from focusTick instead of onFocusChanged.
    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        val target = when (selectedCategory) {
            "__search__"     -> searchFR
            "__favourites__" -> favouritesFR
            null             -> allFR
            else             -> categoryFocusMap[selectedCategory] ?: allFR
        }
        val targetName = when (target) {
            searchFR     -> "searchFR"
            favouritesFR -> "favouritesFR"
            allFR        -> "allFR"
            else         -> "categoryFR[$selectedCategory]"
        }
        android.util.Log.d("NexStreamPanel", "focusTick=$focusTick → $targetName (selectedCategory=$selectedCategory)")
        try { target.requestFocus(); android.util.Log.d("NexStreamPanel", "requestFocus succeeded on $targetName") }
        catch (e: Exception) { android.util.Log.e("NexStreamPanel", "requestFocus FAILED: ${e.message}") }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(sTheme.panelBackground)
            .focusRequester(panelFR)
            .onFocusChanged { fs ->
                android.util.Log.d("NexStreamPanel", "panelContainer onFocusChanged: isFocused=${fs.isFocused} hasFocus=${fs.hasFocus}")
                onPanelFocusChanged(fs.hasFocus)
            }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text     = "Categories",
                style    = MaterialTheme.typography.bodyMedium,
                color    = sTheme.categoryText,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }

        HorizontalDivider(color = sTheme.divider)

        // ── Single flat scrollable Column — all panel items in one list ────────
        // Build the complete item list inline so every FR is created in composition
        // order and index arithmetic is always accurate
        data class PanelEntry(
            val label: String,
            val categoryKey: String?,   // null = All, "__search__", "__favourites__", or category name
            val fr: FocusRequester,
            val isDividerAbove: Boolean = false,
            val onClick: () -> Unit
        )

        // Create FRs for dynamic categories inline so they are always composed
        val catFRs = categories.map { remember(it) { FocusRequester() } }
        catFRs.forEachIndexed { i, fr -> SideEffect { categoryFocusMap[categories[i]] = fr } }

        val entries = buildList {
            if (showSearch) add(PanelEntry("Search", "__search__", searchFR) { onSearchRequest() })
            add(PanelEntry("Favourites", "__favourites__", favouritesFR) { onFavouritesSelected() })
            add(PanelEntry("All", null, allFR, isDividerAbove = true) { onCategorySelected(null) })
            categories.forEachIndexed { i, cat ->
                add(PanelEntry(cat, cat, catFRs[i]) { onCategorySelected(cat) })
            }
        }
        android.util.Log.d("NexStreamPanel", "entries built: ${entries.size} items — ${entries.map { it.label }}")
        android.util.Log.d("NexStreamPanel", "  allFR hashCode=${allFR.hashCode()} favouritesFR=${favouritesFR.hashCode()} searchFR=${searchFR.hashCode()}")
        android.util.Log.d("NexStreamPanel", "  catFRs=${catFRs.map { it.hashCode() }}")

        val scrollState = androidx.compose.foundation.rememberScrollState(0)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 4.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                val prevFR = if (index > 0) entries[index - 1].fr else null
                val nextFR = if (index < entries.lastIndex) entries[index + 1].fr else null

                if (entry.isDividerAbove) {
                    HorizontalDivider(
                        color = sTheme.divider,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                PanelItem(
                    text           = entry.label,
                    isSelected     = selectedCategory == entry.categoryKey,
                    focusRequester = entry.fr,
                    prevFR         = prevFR,
                    nextFR         = nextFR,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onClick   = {
                        android.util.Log.d("NexStreamPanel", "PanelItem clicked: '${entry.label}' (key=${entry.categoryKey})")
                        entry.onClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun PanelItem(
    text: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onRequestContentFocus: () -> Unit,
    onRequestRailFocus: () -> Unit,
    onClick: () -> Unit,
    prevFR: FocusRequester? = null,
    nextFR: FocusRequester? = null,
    maxLines: Int = 2
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
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

    val bgColor = when {
        isSelected -> sTheme.categorySelectedBg
        isFocused  -> sTheme.categoryFocusedBg
        else       -> androidx.compose.ui.graphics.Color.Transparent
    }
    val textColor = when {
        isSelected -> sTheme.categoryTextSelected
        isFocused  -> sTheme.categoryTextFocused
        else       -> sTheme.categoryText
    }
    val textWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
    else androidx.compose.ui.text.font.FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = itemScale; scaleY = itemScale }
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isSelected && isFocused) Modifier.border(2.dp, sTheme.categoryTextSelected, RoundedCornerShape(8.dp))
                else if (isSelected) Modifier.border(1.5.dp, sTheme.categoryTextSelected.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                // Declare explicit focus graph — no onKeyEvent needed for traversal
                if (prevFR != null) up   = prevFR
                if (nextFR != null) down = nextFR
            }
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionRight -> { onRequestContentFocus(); true }
                    Key.DirectionLeft  -> { onRequestRailFocus(); true }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                } else false
            }
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text       = text,
            style      = MaterialTheme.typography.bodyMedium,
            color      = textColor,
            fontWeight = textWeight,
            maxLines   = maxLines,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
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

    HorizontalDivider(color = sTheme.divider)

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
    modifier: Modifier = Modifier
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val textScale = nsTheme.typography.scale.coerceIn(0.85f, 1.5f)
    val headerHeight = (56 * textScale).dp

    data class SettingsEntry(val label: String, val route: AppRoute, val fr: FocusRequester)

    val playlistsFR   = remember { FocusRequester() }
    val appearanceFR  = remember { FocusRequester() }
    val playerFR      = remember { FocusRequester() }
    val licenceFR     = remember { FocusRequester() }
    val accountFR     = remember { FocusRequester() }
    val profilesFR    = remember { FocusRequester() }
    val aboutFR       = remember { FocusRequester() }

    val entries = remember {
        listOf(
            SettingsEntry("Playlists",   AppRoute.SettingsPlaylists,  playlistsFR),
            SettingsEntry("Appearance",  AppRoute.SettingsAppearance, appearanceFR),
            SettingsEntry("Player",      AppRoute.SettingsPlayer,     playerFR),
            SettingsEntry("Licence",     AppRoute.SettingsLicence,    licenceFR),
            SettingsEntry("Account",     AppRoute.SettingsAccount,    accountFR),
            SettingsEntry("Profiles",    AppRoute.SettingsProfiles,   profilesFR),
            SettingsEntry("About",       AppRoute.SettingsAbout,      aboutFR),
        )
    }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        val target = entries.firstOrNull { it.route == selectedRoute }?.fr ?: entries.first().fr
        try { target.requestFocus() } catch (_: Exception) {}
    }

    val scrollState = androidx.compose.foundation.rememberScrollState(0)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(sTheme.panelBackground)
            .focusRequester(panelFR)
            .onFocusChanged { fs -> onPanelFocusChanged(fs.hasFocus) }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text     = "Settings",
                style    = MaterialTheme.typography.bodyMedium,
                color    = sTheme.categoryText,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
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
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight), contentAlignment = Alignment.CenterStart) {
            Text("Dates", style = MaterialTheme.typography.bodyMedium, color = sTheme.categoryText,
                modifier = Modifier.padding(horizontal = 10.dp))
        }
        HorizontalDivider(color = sTheme.divider)


        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 4.dp)
        ) {
            allEntries.forEachIndexed { index, (label, fr) ->
                val categoryKey = entryKeys[index]
                val isSelected  = selectedDateKey == categoryKey
                // Wrap around: last item's next = first item; first item's prev = last item
                val prevFR = if (index > 0) allEntries[index - 1].second else allEntries.last().second
                val nextFR = if (index < allEntries.lastIndex) allEntries[index + 1].second else allEntries.first().second

                if (index == 1) HorizontalDivider(color = sTheme.divider,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

                PanelItem(
                    text           = label,
                    isSelected     = isSelected,
                    focusRequester = fr,
                    prevFR         = prevFR,
                    nextFR         = nextFR,
                    onRequestContentFocus = onRequestContentFocus,
                    onRequestRailFocus    = onRequestRailFocus,
                    onClick = { onDateSelected(categoryKey) }
                )
            }
        }
    }
}