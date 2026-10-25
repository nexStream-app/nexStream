package app.nexstream.player.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.components.GlassRail
import app.nexstream.player.ui.components.GlassTile
import app.nexstream.player.ui.screens.picks.PickItem
import app.nexstream.player.ui.screens.picks.PicksViewModel
import app.nexstream.player.ui.screens.recentlywatched.RecentlyWatchedViewModel
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun GlassHomeScreen(
    firstItemFocusRequester: FocusRequester? = null,
    homeRestoreTick: Int = 0,
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
    onGoToEpg: (channelName: String) -> Unit = {},
    onFocusNavBar: () -> Unit = {},
    sportsEditTick: Int = 0,
    recentlyWatchedViewModel: RecentlyWatchedViewModel = hiltViewModel(),
    homePageViewModel: HomePageViewModel = hiltViewModel(),
    picksViewModel: PicksViewModel = hiltViewModel(),
) {
    val recentlyWatched by recentlyWatchedViewModel.recentlyWatched.collectAsState()
    val filteredSportsEvents by homePageViewModel.filteredSportsEvents.collectAsState()
    val picksGroups by picksViewModel.groups.collectAsState()

    val continueWatching = remember(recentlyWatched) {
        recentlyWatched.filter {
            it.type == RecentlyWatchedType.MOVIE || it.type == RecentlyWatchedType.EPISODE
        }
    }
    val liveEvents = remember(filteredSportsEvents) {
        filteredSportsEvents.filter { !it.isReplay && it.matchedChannels.isNotEmpty() }.take(8)
    }
    val heroItem = picksGroups.firstOrNull()?.items?.firstOrNull()
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
        GlassHeroBanner(
            heroItem = heroItem,
            heroFR = heroFR,
            onFocusNavBar = onFocusNavBar,
            onPlay = { heroItem?.let { onPickSelected(it) } },
        )

        Column(
            modifier = Modifier.padding(
                start = GlassTokens.ScreenGutter,
                end = GlassTokens.ScreenGutter,
                top = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (continueWatching.isNotEmpty()) {
                GlassRail(
                    title = "Continue Watching",
                    items = continueWatching,
                    tileWidth = 280.dp,
                ) { _, item: RecentlyWatchedEntity, tileMod ->
                    GlassTile(
                        imageUrl = item.logoUrl,
                        title = item.name,
                        modifier = tileMod,
                        onClick = {
                            onPlayerLaunch(
                                item.streamUrl,
                                item.movieId,
                                item.episodeId,
                                item.seriesId,
                                0L,
                                item.name,
                                item.subtitle,
                                null,
                            )
                        },
                    )
                }
            }

            if (liveEvents.isNotEmpty()) {
                GlassRail(
                    title = "Live Now",
                    items = liveEvents,
                    tileWidth = 280.dp,
                ) { _, event: MatchedSportEvent, tileMod ->
                    GlassTile(
                        imageUrl = event.matchedChannels.firstOrNull()?.logoUrl,
                        title = event.eventName,
                        isLive = true,
                        modifier = tileMod,
                        onClick = {
                            event.matchedChannels.firstOrNull()?.let { ch ->
                                onChannelPlay(ch.streamUrl, ch.channelName)
                            }
                        },
                    )
                }
            }

            if (firstPicksGroup != null && firstPicksGroup.items.isNotEmpty()) {
                GlassRail(
                    title = "Picks For You",
                    items = firstPicksGroup.items,
                    tileWidth = 160.dp,
                ) { _, pick: PickItem, tileMod ->
                    GlasPosterTile(
                        imageUrl = pick.posterUrl,
                        title = pick.title,
                        modifier = tileMod,
                        onClick = { onPickSelected(pick) },
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun GlassHeroBanner(
    heroItem: PickItem?,
    heroFR: FocusRequester,
    onFocusNavBar: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
    ) {
        AsyncImage(
            model = heroItem?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Left-to-right scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xCC000000),
                        0.6f to Color.Transparent,
                    )
                ),
        )
        // Bottom scrim (blends into background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, GlassTokens.Background))
                ),
        )
        // Title + buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = GlassTokens.ScreenGutter, bottom = 48.dp)
                .widthIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (heroItem != null) {
                Text(
                    text = heroItem.title,
                    color = GlassTokens.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = heroItem.overview,
                    color = GlassTokens.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(heroFR)
                        .onKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (ev.key == Key.DirectionUp) { onFocusNavBar(); true } else false
                        },
                    shape = RoundedCornerShape(GlassTokens.RadiusPill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.AccentDefault,
                        contentColor = GlassTokens.AccentOnDark,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = {},
                    shape = RoundedCornerShape(GlassTokens.RadiusPill),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassTokens.TextPrimary),
                    border = BorderStroke(1.dp, GlassTokens.GlassBorder),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("My List")
                }
            }
        }
    }
}
