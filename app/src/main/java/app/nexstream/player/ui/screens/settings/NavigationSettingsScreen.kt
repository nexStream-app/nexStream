package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nexstream.player.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.ui.screens.main.AppRoute
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getRailHiddenFlow
import app.nexstream.player.ui.theme.getRailOrderFlow
import app.nexstream.player.ui.theme.getStartRouteFlow
import app.nexstream.player.ui.theme.saveRailHidden
import app.nexstream.player.ui.theme.saveRailOrder
import app.nexstream.player.ui.theme.saveStartRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Default rail item order (route name → display label + icon)
data class RailItem(val routeName: String, val label: String, val icon: ImageVector)

val DEFAULT_RAIL_ITEMS = listOf(
    RailItem("Home",     "Sports Today", Icons.Default.SportsSoccer),
    RailItem("Recent",   "Recent",       Icons.Default.History),
    RailItem("Guide",    "Guide",        Icons.Default.CalendarToday),
    RailItem("Movies",   "Movies",       Icons.Default.Movie),
    RailItem("Series",   "Series",       Icons.Default.VideoLibrary),
    RailItem("CatchUp",  "Catch Up",     Icons.Default.Replay),
    RailItem("Picks",    "Picks",        Icons.Default.Stars),
    RailItem("Music",    "Music",        Icons.Default.MusicNote),
    RailItem("Search",   "Search",       Icons.Default.Search),
    RailItem("MyList",   "My List",      Icons.Default.Bookmark),
    RailItem("Settings", "Settings",     Icons.Default.Settings),
)

fun parseRailOrder(stored: String?): List<RailItem> {
    if (stored.isNullOrBlank()) return DEFAULT_RAIL_ITEMS
    val names = stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val byName = DEFAULT_RAIL_ITEMS.associateBy { it.routeName }
    val ordered = names.mapNotNull { byName[it] }
    // append any new items not in stored order
    val missing = DEFAULT_RAIL_ITEMS.filter { it.routeName !in names }
    return ordered + missing
}

fun routeNameToAppRoute(name: String): AppRoute = when (name) {
    "Home"      -> AppRoute.Home
    "Recent"    -> AppRoute.Recent
    "Guide"     -> AppRoute.Guide
    "Movies"    -> AppRoute.Movies
    "Series"    -> AppRoute.Series
    "CatchUp"   -> AppRoute.CatchUp
    "Picks"     -> AppRoute.Picks
    "Music"     -> AppRoute.Music
    "Search"    -> AppRoute.Search
    "MyList"    -> AppRoute.MyList
    "Downloads" -> AppRoute.Downloads
    "Settings"  -> AppRoute.Settings
    else        -> AppRoute.Guide
}

@Composable
fun NavigationSettingsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    hasJellyfinPlaylist: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val scope   = rememberCoroutineScope()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()

    val storedOrder  by context.getRailOrderFlow().collectAsState(initial = null)
    val storedHidden by context.getRailHiddenFlow().collectAsState(initial = null)
    val storedStart  by context.getStartRouteFlow().collectAsState(initial = null)

    val railItems   = remember(storedOrder) { parseRailOrder(storedOrder) }
    val hiddenItems = remember(storedHidden) {
        storedHidden?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }
    val startRoute  = storedStart ?: "Guide"

    val scrollState = rememberScrollState()

    // Start screen dropdown state
    var startDropExpanded  by remember { mutableStateOf(false) }
    var startTriggerFocused by remember { mutableStateOf(false) }
    val startTriggerFR = if (firstItemFocusRequester != null) firstItemFocusRequester else remember { FocusRequester() }

    // Items displayed (Music only when Jellyfin exists)
    val displayItems = remember(railItems, hasJellyfinPlaylist) {
        railItems.filter { it.routeName != "Music" || hasJellyfinPlaylist }
    }

    // Locked items that cannot be moved or hidden
    val settingsIdx = remember(railItems) { railItems.indexOfFirst { it.routeName == "Settings" } }
    val moveLimit   = if (settingsIdx >= 0) settingsIdx - 1 else railItems.lastIndex

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.nav_settings_title), style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Start-on picker ──────────────────────────────────────────────
            Box(modifier = Modifier.onFocusChanged { fs ->
                if (fs.hasFocus) scope.launch { scrollState.animateScrollTo(0) }
            }) {
            SettingsSectionContainer(
                title = stringResource(R.string.nav_settings_start_screen_section),
                icon = Icons.Default.Home,
                uiStyle = uiStyle
            ) {
                Column(modifier = Modifier.padding(
                    horizontal = if (uiStyle == UiStyle.MODERN) 16.dp else 0.dp,
                    vertical = 8.dp
                )) {
                    if (uiStyle == UiStyle.CLASSIC) {
                        Text(
                            stringResource(R.string.nav_settings_start_screen_label),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        stringResource(R.string.nav_settings_start_screen_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    val currentStartItem = railItems.firstOrNull { it.routeName == startRoute }
                        ?: railItems.firstOrNull { it.routeName == "Guide" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (startTriggerFocused || startDropExpanded)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(startTriggerFR)
                                .onFocusChanged { startTriggerFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && (
                                        ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                    )) { startDropExpanded = !startDropExpanded; true } else false
                                }
                                .clickable { startDropExpanded = !startDropExpanded }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (currentStartItem != null) {
                                Icon(currentStartItem.icon, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                currentStartItem?.label ?: "Guide",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (startDropExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (startDropExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            displayItems.filter { it.routeName != "Settings" }.forEach { item ->
                                val isSelected = item.routeName == startRoute
                                var optFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                optFocused -> MaterialTheme.colorScheme.surfaceVariant
                                                else       -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .onFocusChanged { optFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && (
                                                ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                                            )) {
                                                scope.launch { context.saveStartRoute(item.routeName) }
                                                startDropExpanded = false; true
                                            } else false
                                        }
                                        .clickable {
                                            scope.launch { context.saveStartRoute(item.routeName) }
                                            startDropExpanded = false
                                        }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(item.icon, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                               else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end Box (scroll-to-top on Start Screen focus)

            // ── Rail order ───────────────────────────────────────────────────
            SettingsSectionContainer(
                title = stringResource(R.string.nav_settings_rail_order_section),
                icon = Icons.Default.Reorder,
                uiStyle = uiStyle
            ) {
                Column(modifier = Modifier.padding(
                    horizontal = if (uiStyle == UiStyle.MODERN) 16.dp else 0.dp,
                    vertical = 8.dp
                )) {
                    if (uiStyle == UiStyle.CLASSIC) {
                        Text(
                            stringResource(R.string.nav_settings_rail_order_label),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        stringResource(R.string.nav_settings_rail_order_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    var focusedItemRoute by remember { mutableStateOf<String?>(null) }
                    var focusedItemButton by remember { mutableStateOf<String?>(null) } // "up" or "down"
                    val itemFocusRequesters = remember { mutableMapOf<String, Pair<FocusRequester, FocusRequester>>() }

                    displayItems.forEach { item ->
                        val realIdx    = railItems.indexOf(item)
                        val isSettings = item.routeName == "Settings"
                        val isGuide    = item.routeName == "Guide"
                        val isHidden   = item.routeName in hiddenItems
                        val canMoveUp   = !isSettings && realIdx > 0
                        val canMoveDown = !isSettings && realIdx < moveLimit
                        var rowHasFocus by remember { mutableStateOf(false) }

                        val (upFR, downFR) = remember(item.routeName) {
                            val pair = itemFocusRequesters.getOrPut(item.routeName) {
                                Pair(FocusRequester(), FocusRequester())
                            }
                            pair
                        }

                        // Request focus on this item's button after it moved
                        LaunchedEffect(focusedItemRoute, focusedItemButton, railItems) {
                            if (focusedItemRoute == item.routeName) {
                                delay(50)
                                runCatching {
                                    when (focusedItemButton) {
                                        "up"   -> upFR.requestFocus()
                                        "down" -> downFR.requestFocus()
                                        else   -> {}
                                    }
                                }
                                focusedItemRoute = null
                                focusedItemButton = null
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isHidden    -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        rowHasFocus -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        else        -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    width = if (rowHasFocus) 2.dp else 1.dp,
                                    color = if (rowHasFocus) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { rowHasFocus = it.hasFocus }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(item.icon, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.label,
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSettings) {
                                Text(stringResource(R.string.common_locked), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Hide/Show toggle — Guide cannot be hidden
                                    if (!isGuide) {
                                        RailOrderIconButton(
                                            icon = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isHidden) stringResource(R.string.common_show) else stringResource(R.string.common_hide),
                                            enabled = true,
                                            onClick = {
                                                val newHidden = if (isHidden) hiddenItems - item.routeName
                                                               else hiddenItems + item.routeName
                                                scope.launch { context.saveRailHidden(newHidden.joinToString(",")) }
                                            }
                                        )
                                    }
                                    // Move up
                                    RailOrderIconButton(
                                        icon = Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.nav_settings_move_up),
                                        enabled = canMoveUp,
                                        focusRequester = upFR,
                                        onClick = {
                                            if (canMoveUp) {
                                                val newOrder = railItems.toMutableList().apply { add(realIdx - 1, removeAt(realIdx)) }
                                                scope.launch { context.saveRailOrder(newOrder.joinToString(",") { it.routeName }) }
                                                focusedItemRoute = item.routeName
                                                focusedItemButton = "up"
                                            }
                                        }
                                    )
                                    // Move down
                                    RailOrderIconButton(
                                        icon = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.nav_settings_move_down),
                                        enabled = canMoveDown,
                                        focusRequester = downFR,
                                        onClick = {
                                            if (canMoveDown) {
                                                val newOrder = railItems.toMutableList().apply { add(realIdx + 1, removeAt(realIdx)) }
                                                scope.launch { context.saveRailOrder(newOrder.joinToString(",") { it.routeName }) }
                                                focusedItemRoute = item.routeName
                                                focusedItemButton = "down"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun RailOrderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(32.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (isFocused) Modifier.border(2.dp, primary, RoundedCornerShape(6.dp)) else Modifier)
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (
                    ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter
                )) {
                    if (enabled) onClick()
                    true
                } else false
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        )
    }
}
