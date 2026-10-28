package app.nexstream.player.ui.screens.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun GlassSeriesDetailScreen(
    series: SeriesEntity,
    episodes: List<EpisodeEntity>,
    seasons: List<Int>,
    moreLikeThis: List<SeriesGridItem> = emptyList(),
    isBookmarked: Boolean = false,
    onBack: () -> Unit,
    onPlayEpisode: (streamUrl: String, episodeId: String, startPosition: Long, seriesId: String, seriesName: String, seasonNum: Int, episodeNum: Int, episodeName: String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSeriesClick: (SeriesGridItem) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    var selectedSeason by remember(seasons) {
        val lastWatched = episodes
            .filter { it.lastPlayedPosition > 0L }
            .maxWithOrNull(compareBy({ it.seasonNum }, { it.episodeNum }))
            ?.seasonNum
        mutableStateOf(lastWatched?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1)
    }

    val episodesForSeason = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNum == selectedSeason }.sortedBy { it.episodeNum }
    }

    val playEpisode = remember(episodesForSeason) {
        episodesForSeason.firstOrNull { it.lastPlayedPosition > 0L }
            ?: episodesForSeason.firstOrNull()
    }

    val hasProgress  = (playEpisode?.lastPlayedPosition ?: 0L) > 0L
    val multiSeason  = seasons.size > 1
    val hasSim       = moreLikeThis.isNotEmpty()
    val hasEpisodes  = episodesForSeason.isNotEmpty()

    val playFR        = remember { FocusRequester() }
    val firstSeasonFR = remember { FocusRequester() }
    val firstEpFR     = remember { FocusRequester() }
    val firstSimFR    = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        delay(120)
        try { playFR.requestFocus() } catch (_: Exception) {}
    }

    val onFocusDownFromButtons: () -> Unit = {
        try {
            if (multiSeason) firstSeasonFR.requestFocus()
            else if (hasEpisodes) firstEpFR.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.Background),
    ) {

        // ── Hero backdrop ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AsyncImage(
                model              = series.backdropUrl ?: series.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f    to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.30f),
                            1f    to Color.Black.copy(alpha = 0.92f),
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.65f)
                    .padding(start = GlassTokens.ScreenGutter, bottom = 20.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text       = series.name,
                    color      = GlassTokens.TextPrimary,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 30.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    series.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
                        ?.let { SeriesDetailChip(it) }
                    series.certification?.takeIf { it.isNotBlank() }
                        ?.let { SeriesDetailChip(it, accent = true) }
                    (series.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: series.rating)
                        ?.takeIf { it.isNotBlank() }?.let { SeriesDetailChip("★ $it") }
                    if (seasons.isNotEmpty())
                        SeriesDetailChip("${seasons.size} Season${if (seasons.size > 1) "s" else ""}")
                }
                series.genre?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = GlassTokens.AccentBlue, fontSize = 11.sp)
                }
                series.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text       = it,
                        color      = GlassTokens.TextSecondary,
                        fontSize   = 12.sp,
                        maxLines   = 4,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        // ── Action buttons ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.ScreenGutter, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeriesDetailButton(
                icon        = Icons.Default.ArrowBack,
                label       = "Back",
                primary     = false,
                modifier    = Modifier.width(110.dp),
                onFocusDown = onFocusDownFromButtons,
                onClick     = onBack,
            )
            SeriesDetailButton(
                icon           = Icons.Default.PlayArrow,
                label          = if (hasProgress) "Resume" else "Play",
                primary        = true,
                modifier       = Modifier.weight(1f),
                focusRequester = playFR,
                onFocusDown    = onFocusDownFromButtons,
                onClick        = {
                    playEpisode?.let { ep ->
                        onPlayEpisode(
                            ep.streamUrl, ep.id,
                            if (hasProgress) ep.lastPlayedPosition else 0L,
                            series.id, series.name,
                            ep.seasonNum, ep.episodeNum, ep.name,
                        )
                    }
                },
            )
            SeriesDetailButton(
                icon        = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label       = if (isBookmarked) "In My List" else "My List",
                primary     = false,
                modifier    = Modifier.weight(1f),
                onFocusDown = onFocusDownFromButtons,
                onClick     = onToggleWatchlist,
            )
        }

        // ── Season pills ───────────────────────────────────────────────────────
        if (multiSeason) {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = GlassTokens.ScreenGutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(seasons) { idx, season ->
                    SeasonPillGlass(
                        label       = "Season $season",
                        selected    = season == selectedSeason,
                        modifier    = if (idx == 0) Modifier.focusRequester(firstSeasonFR) else Modifier,
                        onFocusUp   = { try { playFR.requestFocus() } catch (_: Exception) {} },
                        onFocusDown = { try { if (hasEpisodes) firstEpFR.requestFocus() } catch (_: Exception) {} },
                        onClick     = { selectedSeason = season },
                    )
                }
            }
        }

        // ── Episodes ───────────────────────────────────────────────────────────
        if (hasEpisodes) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text     = "Episodes",
                    color    = GlassTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        start   = GlassTokens.ScreenGutter,
                        bottom  = 8.dp,
                        top     = if (!multiSeason) 4.dp else 0.dp,
                    ),
                )
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = GlassTokens.ScreenGutter),
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
                ) {
                    itemsIndexed(episodesForSeason) { idx, ep ->
                        val epMod = if (idx == 0)
                            Modifier
                                .focusRequester(firstEpFR)
                                .onKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionUp -> {
                                            try {
                                                if (multiSeason) firstSeasonFR.requestFocus()
                                                else playFR.requestFocus()
                                            } catch (_: Exception) {}
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (hasSim) try { firstSimFR.requestFocus() } catch (_: Exception) {}
                                            hasSim
                                        }
                                        else -> false
                                    }
                                }
                        else
                            Modifier.onKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                    if (hasSim) try { firstSimFR.requestFocus() } catch (_: Exception) {}
                                    hasSim
                                } else false
                            }
                        EpisodeTileGlass(
                            episode  = ep,
                            modifier = epMod,
                            onPlay   = {
                                onPlayEpisode(
                                    ep.streamUrl, ep.id, ep.lastPlayedPosition,
                                    series.id, series.name,
                                    ep.seasonNum, ep.episodeNum, ep.name,
                                )
                            },
                        )
                    }
                }
            }
        }

        // ── Similar Series ──────────────────────────────────────────────────────
        if (hasSim) {
            Column(
                modifier = Modifier
                    .padding(horizontal = GlassTokens.ScreenGutter)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text       = "Similar Series",
                    color      = GlassTokens.TextPrimary,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(bottom = 10.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
                    contentPadding        = PaddingValues(end = GlassTokens.ScreenGutter),
                ) {
                    itemsIndexed(moreLikeThis) { idx, s ->
                        GlasPosterTile(
                            imageUrl      = s.posterUrl,
                            title         = s.name,
                            rating        = s.rating?.toDoubleOrNull()?.let { "%.1f".format(it) }
                                ?: s.rating,
                            hasNewEpisode = s.hasNewEpisodes,
                            modifier      = if (idx == 0)
                                Modifier
                                    .width(120.dp)
                                    .focusRequester(firstSimFR)
                                    .onKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                            try { firstEpFR.requestFocus() } catch (_: Exception) {}
                                            true
                                        } else false
                                    }
                            else Modifier.width(120.dp),
                            onClick       = { onSeriesClick(s) },
                        )
                    }
                }
            }
        }
    }
}

// ── Season pill ─────────────────────────────────────────────────────────────

@Composable
private fun SeasonPillGlass(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusUp: () -> Unit = {},
    onFocusDown: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected && focused -> GlassTokens.AccentDefault
        selected            -> GlassTokens.AccentDefault.copy(alpha = 0.22f)
        focused             -> GlassTokens.GlassSmallSolid
        else                -> GlassTokens.GlassPanelSolid
    }
    val textColor = when {
        selected && focused -> GlassTokens.AccentOnDark
        selected            -> GlassTokens.AccentDefault
        focused             -> GlassTokens.TextPrimary
        else                -> GlassTokens.TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(GlassTokens.RadiusPill),
        color = bg,
        modifier = modifier
            .height(36.dp)
            .border(
                width = if (focused) GlassTokens.FocusOutlineWidth else 1.dp,
                color = if (focused || selected) GlassTokens.AccentDefault
                        else GlassTokens.GlassBorder,
                shape = RoundedCornerShape(GlassTokens.RadiusPill),
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionUp   -> { onFocusUp(); true }
                    Key.DirectionDown -> { onFocusDown(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Text(
            text       = label,
            color      = textColor,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ── Episode tile ─────────────────────────────────────────────────────────────

@Composable
private fun EpisodeTileGlass(
    episode: EpisodeEntity,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = episode.lastPlayedPosition
    val durationMs = parseDurationMs(episode.duration)
    val progressFraction = if (durationMs > 0L) (progress.toFloat() / durationMs).coerceIn(0f, 1f)
                           else if (progress > 0L) 0.5f else 0f

    Box(
        modifier = modifier
            .width(180.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(GlassTokens.RadiusCard))
            .border(
                width = if (focused) GlassTokens.FocusOutlineWidth else 1.dp,
                color = if (focused) GlassTokens.AccentDefault else GlassTokens.GlassBorder,
                shape = RoundedCornerShape(GlassTokens.RadiusCard),
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPlay(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable(onClick = onPlay),
    ) {
        AsyncImage(
            model              = episode.posterUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.5f to Color.Black.copy(alpha = 0.20f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    )
                )
        )
        // Episode number badge (top-left)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = GlassTokens.GlassSmallSolid,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        ) {
            Text(
                text     = "E${episode.episodeNum}",
                color    = GlassTokens.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        // Episode name + progress bar (bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text     = episode.name,
                color    = GlassTokens.TextPrimary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(GlassTokens.GlassBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .clip(RoundedCornerShape(99.dp))
                            .background(GlassTokens.AccentDefault),
                    )
                }
            }
        }
    }
}

private fun parseDurationMs(duration: String?): Long {
    if (duration.isNullOrBlank()) return 0L
    val parts = duration.trim().split(":")
    return try {
        when (parts.size) {
            3    -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000L
            2    -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000L
            else -> parts[0].toLong() * 1000L
        }
    } catch (_: NumberFormatException) { 0L }
}

// ── Shared button & chip ─────────────────────────────────────────────────────

@Composable
private fun SeriesDetailButton(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusDown: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }

    val bg = when {
        primary && focused  -> GlassTokens.AccentDefault
        primary && !focused -> GlassTokens.AccentDefault.copy(alpha = 0.16f)
        focused             -> GlassTokens.GlassSmallSolid
        else                -> GlassTokens.GlassPanelSolid
    }
    val contentColor = when {
        primary && focused  -> GlassTokens.AccentOnDark
        primary && !focused -> GlassTokens.AccentDefault
        focused             -> GlassTokens.TextPrimary
        else                -> GlassTokens.TextSecondary
    }

    Row(
        modifier = modifier
            .height(GlassTokens.ButtonHeight)
            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
            .background(bg)
            .border(
                width = if (focused) GlassTokens.FocusOutlineWidth else 1.dp,
                color = when {
                    focused -> GlassTokens.AccentDefault
                    primary -> GlassTokens.AccentDefault.copy(alpha = 0.35f)
                    else    -> GlassTokens.GlassBorder
                },
                shape = RoundedCornerShape(GlassTokens.RadiusPill),
            )
            .focusRequester(focusRequester ?: fr)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionDown -> { onFocusDown?.invoke(); onFocusDown != null }
                    else -> false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = contentColor,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = label,
            color      = contentColor,
            fontSize   = 13.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeriesDetailChip(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (accent) GlassTokens.AccentDefault.copy(alpha = 0.15f)
                else GlassTokens.GlassSmallSolid,
    ) {
        Text(
            text       = text,
            fontSize   = 11.sp,
            color      = if (accent) GlassTokens.AccentDefault else GlassTokens.TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
