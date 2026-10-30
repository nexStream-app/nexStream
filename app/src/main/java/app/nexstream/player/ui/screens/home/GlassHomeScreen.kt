package app.nexstream.player.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.components.GlassRail
import app.nexstream.player.ui.components.GlassTile
import app.nexstream.player.ui.screens.picks.PickItem
import app.nexstream.player.ui.screens.picks.PicksViewModel
import app.nexstream.player.ui.screens.recentlywatched.RecentlyWatchedViewModel
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlassHomeScreen(
    firstItemFocusRequester: FocusRequester? = null,
    homeRestoreTick: Int = 0,
    profileId: String = "default",
    watchlistIds: Set<String> = emptySet(),
    onPickSelected: (PickItem) -> Unit = {},
    onPlayerLaunch: (
        url: String,
        movieId: String?,
        episodeId: String?,
        seriesId: String?,
        startPos: Long,
        title: String?,
        subtitle: String?,
        description: String?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onChannelPlay: (streamUrl: String, channelName: String) -> Unit = { _, _ -> },
    onToggleChannelWatchlist: (channelId: String, channelName: String, logoUrl: String?, streamUrl: String) -> Unit = { _, _, _, _ -> },
    onSeriesOpen: (seriesId: String) -> Unit = {},
    onMovieSelected: (movieId: String) -> Unit = {},
    onGoToEpg: (channelName: String) -> Unit = {},
    onFocusNavBar: () -> Unit = {},
    sportsEditTick: Int = 0,
    recentlyWatchedViewModel: RecentlyWatchedViewModel = hiltViewModel(),
    homePageViewModel: HomePageViewModel = hiltViewModel(),
    picksViewModel: PicksViewModel = hiltViewModel(),
) {
    val recentlyWatched    by recentlyWatchedViewModel.recentlyWatched.collectAsState()
    val filteredSports     by homePageViewModel.filteredSportsEvents.collectAsState()
    val sportsCategories   by homePageViewModel.sportsCategories.collectAsState()
    val selectedSportCat   by homePageViewModel.selectedSportCategory.collectAsState()
    val picksGroups        by picksViewModel.groups.collectAsState()
    val liveHeroItems      by homePageViewModel.liveHeroItems.collectAsState()
    val recentMovies       by homePageViewModel.recentMovies.collectAsState()
    val newSeries          by homePageViewModel.newSeries.collectAsState()

    // Refresh live hero items on first composition and when profileId changes
    LaunchedEffect(profileId) {
        homePageViewModel.refreshLiveHeroItems(profileId)
    }

    // Auto-rotate hero index
    var heroIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(liveHeroItems.size) {
        heroIndex = 0
        if (liveHeroItems.size <= 1) return@LaunchedEffect
        var idx = 0
        while (true) {
            delay(7_000L)
            idx = (idx + 1) % liveHeroItems.size
            heroIndex = idx
        }
    }

    val continueWatchingSeries = remember(recentlyWatched) {
        recentlyWatched
            .filter { it.type == RecentlyWatchedType.EPISODE && it.seriesId != null }
            .distinctBy { it.seriesId }
            .take(10)
    }

    val liveEvents = remember(filteredSports) {
        filteredSports.filter { !it.isReplay && it.matchedChannels.isNotEmpty() }.take(8)
    }
    val firstPicksGroup = picksGroups.firstOrNull()

    val heroFR = firstItemFocusRequester ?: remember { FocusRequester() }

    LaunchedEffect(homeRestoreTick) {
        if (homeRestoreTick > 0) {
            delay(80)
            try { heroFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Live hero banner ───────────────────────────────────────────────────
        LiveHeroBanner(
            items        = liveHeroItems,
            heroIndex    = heroIndex,
            isBookmarked = liveHeroItems.getOrNull(heroIndex)?.channelId in watchlistIds,
            heroFR       = heroFR,
            onFocusNavBar      = onFocusNavBar,
            onWatchLive        = {
                liveHeroItems.getOrNull(heroIndex)?.let { item ->
                    onChannelPlay(item.streamUrl, item.channelName)
                }
            },
            onToggleWatchlist  = {
                liveHeroItems.getOrNull(heroIndex)?.let { item ->
                    onToggleChannelWatchlist(item.channelId, item.channelName, item.channelLogoUrl, item.streamUrl)
                }
            },
        )

        Column(
            modifier = Modifier.padding(
                start = GlassTokens.ScreenGutter,
                end   = GlassTokens.ScreenGutter,
                top   = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Continue Watching (series only) ───────────────────────────────
            if (continueWatchingSeries.isNotEmpty()) {
                GlassRail(
                    title     = "Continue Watching",
                    items     = continueWatchingSeries,
                    tileWidth = 160.dp,
                ) { _, item, tileMod ->
                    GlasPosterTile(
                        imageUrl      = item.logoUrl,
                        title         = item.name,
                        modifier      = tileMod,
                        onClick       = { item.seriesId?.let { onSeriesOpen(it) } },
                    )
                }
            }

            // ── New Series / New Episodes ──────────────────────────────────────
            if (newSeries.isNotEmpty()) {
                val newEpSeries = remember(newSeries) { newSeries.filter { it.hasNewEpisodes } }
                val recentSeries = remember(newSeries) { if (newEpSeries.size >= 10) emptyList() else newSeries.filter { !it.hasNewEpisodes } }
                val rowTitle = if (newEpSeries.isNotEmpty()) "New Episodes" else "New Series"
                val rowItems = if (newEpSeries.isNotEmpty()) newEpSeries else newSeries
                GlassRail(
                    title     = rowTitle,
                    items     = rowItems,
                    tileWidth = 160.dp,
                ) { _, item: SeriesGridItem, tileMod ->
                    GlasPosterTile(
                        imageUrl = item.posterUrl,
                        title    = item.name,
                        modifier = tileMod,
                        onClick  = { onSeriesOpen(item.id) },
                    )
                }
                if (newEpSeries.isNotEmpty() && recentSeries.isNotEmpty()) {
                    GlassRail(
                        title     = "New Series",
                        items     = recentSeries,
                        tileWidth = 160.dp,
                    ) { _, item: SeriesGridItem, tileMod ->
                        GlasPosterTile(
                            imageUrl = item.posterUrl,
                            title    = item.name,
                            modifier = tileMod,
                            onClick  = { onSeriesOpen(item.id) },
                        )
                    }
                }
            }

            // ── New Movies ────────────────────────────────────────────────────
            if (recentMovies.isNotEmpty()) {
                GlassRail(
                    title     = "New Movies",
                    items     = recentMovies,
                    tileWidth = 160.dp,
                ) { _, item: MovieGridItem, tileMod ->
                    GlasPosterTile(
                        imageUrl = item.posterUrl,
                        title    = item.name,
                        modifier = tileMod,
                        onClick  = { onMovieSelected(item.id) },
                    )
                }
            }

            // ── Live sports ───────────────────────────────────────────────────
            if (liveEvents.isNotEmpty()) {
                GlassSportsScreen(
                    events           = liveEvents,
                    categories       = sportsCategories,
                    selectedCategory = selectedSportCat,
                    onCategorySelect = { homePageViewModel.selectSportCategory(it) },
                    onChannelPlay    = onChannelPlay,
                    onFocusNavBar    = onFocusNavBar,
                )
            }

            // ── Picks For You ─────────────────────────────────────────────────
            if (firstPicksGroup != null && firstPicksGroup.items.isNotEmpty()) {
                GlassRail(
                    title     = "Picks For You",
                    items     = firstPicksGroup.items,
                    tileWidth = 160.dp,
                ) { _, pick: PickItem, tileMod ->
                    GlasPosterTile(
                        imageUrl = pick.posterUrl,
                        title    = pick.title,
                        modifier = tileMod,
                        onClick  = { onPickSelected(pick) },
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Live hero banner ──────────────────────────────────────────────────────────

@Composable
private fun LiveHeroBanner(
    items: List<LiveHeroItem>,
    heroIndex: Int,
    isBookmarked: Boolean,
    heroFR: FocusRequester,
    onFocusNavBar: () -> Unit,
    onWatchLive: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val item = items.getOrNull(heroIndex)
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        AsyncImage(
            model              = item?.artworkUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            alignment          = Alignment.TopCenter,
            modifier           = Modifier.fillMaxSize(),
        )
        // Left-to-right gradient so text is readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f    to Color(0xDD000000),
                        0.65f to Color.Transparent,
                    )
                ),
        )
        // Bottom fade into background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, GlassTokens.Background))
                ),
        )

        // Rotation indicator dots (top-right)
        if (items.size > 1) {
            Row(
                modifier              = Modifier.align(Alignment.TopEnd).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                repeat(items.size) { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == heroIndex) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == heroIndex) GlassTokens.AccentDefault
                                else GlassTokens.TextSecondary.copy(alpha = 0.45f)
                            )
                    )
                }
            }
        }

        // Content overlaid at bottom-left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = GlassTokens.ScreenGutter, bottom = 20.dp)
                .widthIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item != null) {
                // LIVE badge + channel + time
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = GlassTokens.LiveBadge,
                    ) {
                        Text(
                            text       = "LIVE",
                            color      = Color.White,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text  = "${item.channelName} · ${timeFmt.format(Date(item.programStartMs))} – ${timeFmt.format(Date(item.programEndMs))}",
                        color = GlassTokens.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Programme title
                Text(
                    text       = item.programTitle,
                    color      = GlassTokens.TextPrimary,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 28.sp,
                )
                // Programme description
                item.programDescription?.let {
                    Text(
                        text     = it,
                        color    = GlassTokens.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick  = onWatchLive,
                    modifier = Modifier
                        .focusRequester(heroFR)
                        .onKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (ev.key == Key.DirectionUp) { onFocusNavBar(); true } else false
                        },
                    shape  = RoundedCornerShape(GlassTokens.RadiusPill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.AccentDefault,
                        contentColor   = GlassTokens.AccentOnDark,
                    ),
                    enabled = item != null,
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch Live", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onToggleWatchlist,
                    shape   = RoundedCornerShape(GlassTokens.RadiusPill),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = GlassTokens.TextPrimary),
                    border  = BorderStroke(
                        1.dp,
                        if (isBookmarked) GlassTokens.AccentDefault else GlassTokens.GlassBorder,
                    ),
                    enabled = item != null,
                ) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.Add,
                        null, Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isBookmarked) "In My List" else "My List")
                }
            }
        }
    }
}
