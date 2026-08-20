package app.nexstream.player.ui.screens.recentlywatched

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernRecentlyWatchedContent(
    allItems: List<RecentlyWatchedEntity>,
    filteredItems: List<RecentlyWatchedEntity>,
    selectedType: String?,
    progressMap: Map<String, Float>,
    firstItemFocusRequester: FocusRequester,
    onItemClick: (RecentlyWatchedEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    // ── Empty states ──────────────────────────────────────────────────────────
    if (allItems.isEmpty()) {
        Box(
            modifier         = modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.History,
                    contentDescription = null,
                    tint               = textSecondary.copy(alpha = 0.35f),
                    modifier           = Modifier.size(52.dp),
                )
                Text(
                    text       = "Nothing watched yet",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = textPrimary.copy(alpha = 0.7f),
                )
                Text(
                    text     = "Content you watch will appear here",
                    fontSize = 13.sp,
                    color    = textSecondary.copy(alpha = 0.55f),
                )
            }
        }
        return
    }

    if (filteredItems.isEmpty()) {
        Box(
            modifier         = modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text     = "No ${selectedType?.lowercase() ?: "items"} watched recently",
                fontSize = 14.sp,
                color    = textSecondary,
            )
        }
        return
    }

    // ── Grid ──────────────────────────────────────────────────────────────────
    if (selectedType == null) {
        val channels = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.CHANNEL } }
        val movies   = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.MOVIE } }
        val episodes = remember(filteredItems) { filteredItems.filter { it.type == RecentlyWatchedType.EPISODE } }

        LazyVerticalGrid(
            columns               = GridCells.Adaptive(minSize = 130.dp),
            modifier              = modifier.fillMaxSize().background(background),
            contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (channels.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecentSectionHeader("Live TV (${channels.size})", accent, divider)
                }
                itemsIndexed(channels, key = { _, it -> "ch_${it.id}" }) { idx, ch ->
                    RecentCard(
                        item             = ch,
                        defaultIcon      = Icons.Default.Tv,
                        hasProgress      = false,
                        progressFraction = 0f,
                        accent           = accent,
                        surface          = surface,
                        textPrimary      = textPrimary,
                        focusRequester   = if (idx == 0) firstItemFocusRequester else null,
                        onClick          = { onItemClick(ch) },
                    )
                }
            }
            if (movies.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecentSectionHeader("Movies (${movies.size})", accent, divider)
                }
                itemsIndexed(movies, key = { _, it -> "mov_${it.id}" }) { idx, movie ->
                    RecentCard(
                        item             = movie,
                        defaultIcon      = Icons.Default.Movie,
                        hasProgress      = movie.movieId != null && movie.movieId in progressMap,
                        progressFraction = progressMap[movie.movieId ?: ""] ?: 0f,
                        accent           = accent,
                        surface          = surface,
                        textPrimary      = textPrimary,
                        focusRequester   = if (idx == 0 && channels.isEmpty()) firstItemFocusRequester else null,
                        onClick          = { onItemClick(movie) },
                    )
                }
            }
            if (episodes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecentSectionHeader("Episodes (${episodes.size})", accent, divider)
                }
                itemsIndexed(episodes, key = { _, it -> "ep_${it.id}" }) { idx, ep ->
                    RecentCard(
                        item             = ep,
                        defaultIcon      = Icons.Default.VideoLibrary,
                        hasProgress      = ep.episodeId != null && ep.episodeId in progressMap,
                        progressFraction = progressMap[ep.episodeId ?: ""] ?: 0f,
                        accent           = accent,
                        surface          = surface,
                        textPrimary      = textPrimary,
                        focusRequester   = if (idx == 0 && channels.isEmpty() && movies.isEmpty()) firstItemFocusRequester else null,
                        onClick          = { onItemClick(ep) },
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns               = GridCells.Adaptive(minSize = 130.dp),
            modifier              = modifier.fillMaxSize().background(background),
            contentPadding        = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(filteredItems, key = { _, it -> it.id }) { idx, item ->
                val icon = when (item.type) {
                    RecentlyWatchedType.CHANNEL -> Icons.Default.Tv
                    RecentlyWatchedType.MOVIE   -> Icons.Default.Movie
                    RecentlyWatchedType.EPISODE -> Icons.Default.VideoLibrary
                }
                val hasProgress = when (item.type) {
                    RecentlyWatchedType.MOVIE   -> item.movieId != null && item.movieId in progressMap
                    RecentlyWatchedType.EPISODE -> item.episodeId != null && item.episodeId in progressMap
                    else                        -> false
                }
                val progressFraction = when (item.type) {
                    RecentlyWatchedType.MOVIE   -> progressMap[item.movieId ?: ""] ?: 0f
                    RecentlyWatchedType.EPISODE -> progressMap[item.episodeId ?: ""] ?: 0f
                    else                        -> 0f
                }
                RecentCard(
                    item             = item,
                    defaultIcon      = icon,
                    hasProgress      = hasProgress,
                    progressFraction = progressFraction,
                    accent           = accent,
                    surface          = surface,
                    textPrimary      = textPrimary,
                    focusRequester   = if (idx == 0) firstItemFocusRequester else null,
                    onClick          = { onItemClick(item) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait card with optional progress dot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentCard(
    item: RecentlyWatchedEntity,
    defaultIcon: ImageVector,
    hasProgress: Boolean,
    progressFraction: Float,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "recentScale")

    Column(
        modifier            = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused.value = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                        else -> false
                    }
                }
                .clickable { onClick() }
        ) {
            if (!item.logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model              = item.logoUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector        = defaultIcon,
                    contentDescription = null,
                    tint               = textPrimary.copy(alpha = 0.3f),
                    modifier           = Modifier.align(Alignment.Center).size(36.dp),
                )
            }
            // Bottom scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            // Episode subtitle badge — bottom-left
            if (item.subtitle != null) {
                Text(
                    text     = item.subtitle.take(12),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = accent,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp),
                )
            }
            // Progress bar — bottom of image
            if (hasProgress && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            }
        }
        Text(
            text     = item.name,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentSectionHeader(
    title: String,
    accent: Color,
    divider: Color,
) {
    Column(
        modifier            = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text       = title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = accent,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.4f)),
        )
    }
}
