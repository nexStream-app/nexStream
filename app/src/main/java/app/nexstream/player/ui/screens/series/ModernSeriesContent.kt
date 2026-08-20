package app.nexstream.player.ui.screens.series

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernSeriesContent(
    seriesList: List<SeriesGridItem>,
    continueWatching: List<RecentlyWatchedEntity> = emptyList(),
    progressItemIds: Set<String> = emptySet(),
    watchedCounts: Map<String, Int> = emptyMap(),
    selectedCategory: String? = null,
    onSeriesClick: (SeriesGridItem) -> Unit = {},
    onContinueWatchingClick: (RecentlyWatchedEntity) -> Unit = {},
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current

    val showCarousels = selectedCategory == null
    var popularSeries     by remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
    var newSeries         by remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
    var newEpisodesSeries by remember { mutableStateOf<List<SeriesGridItem>>(emptyList()) }
    val continueEpisodes = remember(continueWatching) {
        continueWatching.filter { it.type == RecentlyWatchedType.EPISODE }
    }
    LaunchedEffect(seriesList, continueEpisodes) {
        val (popular, recent, newEps) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val inProgress = continueEpisodes.mapNotNull { it.seriesId }.toSet()
            Triple(
                seriesList.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }.take(12),
                seriesList.sortedByDescending { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "0000-00-00" }.take(24),
                seriesList.filter { it.id in inProgress }.take(16)
            )
        }
        popularSeries     = popular
        newSeries         = recent
        newEpisodesSeries = newEps
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 130.dp),
        modifier              = Modifier.fillMaxSize().background(background),
        contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showCarousels) {
            // ── Continue Watching ──────────────────────────────────────────
            if (continueEpisodes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesSectionHeader(
                        title       = "Continue Watching",
                        accent      = accent,
                        textPrimary = textPrimary,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        continueEpisodes.take(12).forEach { item ->
                            EpisodeContinueCard(
                                item    = item,
                                accent  = accent,
                                surface = surface,
                                onClick = { onContinueWatchingClick(item) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── New Episodes ───────────────────────────────────────────────
            if (newEpisodesSeries.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesSectionHeader(
                        title       = "New Episodes",
                        accent      = accent,
                        textPrimary = textPrimary,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        newEpisodesSeries.forEach { series ->
                            SeriesPortraitCard(
                                series       = series,
                                hasProgress  = progressItemIds.any { it.startsWith(series.id) },
                                watchedCount = watchedCounts[series.id] ?: 0,
                                accent       = accent,
                                surface      = surface,
                                textPrimary  = textPrimary,
                                fixedWidth   = 120.dp,
                                fixedHeight  = 180.dp,
                                onClick      = { onSeriesClick(series) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── Popular carousel ─────────────────────────────────────────
            if (popularSeries.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesSectionHeader(
                        title       = "Top Rated",
                        accent      = accent,
                        textPrimary = textPrimary,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        popularSeries.forEachIndexed { index, series ->
                            RankedSeriesPosterCard(
                                series       = series,
                                rank         = index + 1,
                                hasProgress  = progressItemIds.any { it.startsWith(series.id) },
                                watchedCount = watchedCounts[series.id] ?: 0,
                                accent       = accent,
                                surface      = surface,
                                textPrimary  = textPrimary,
                                onClick      = { onSeriesClick(series) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── New series carousel ──────────────────────────────────────
            if (newSeries.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesSectionHeader(
                        title       = "New",
                        accent      = accent,
                        textPrimary = textPrimary,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        newSeries.forEach { series ->
                            SeriesPortraitCard(
                                series       = series,
                                hasProgress  = progressItemIds.any { it.startsWith(series.id) },
                                watchedCount = watchedCounts[series.id] ?: 0,
                                accent       = accent,
                                surface      = surface,
                                textPrimary  = textPrimary,
                                onClick      = { onSeriesClick(series) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

        }

        // ── Grid ────────────────────────────────────────────────────────────
        if (seriesList.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text     = if (selectedCategory != null) "No series in this category" else "No series found",
                        color    = textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            items(
                count = seriesList.size,
                key   = { seriesList[it].id },
            ) { index ->
                val series = seriesList[index]
                SeriesPortraitCard(
                    series       = series,
                    hasProgress  = progressItemIds.any { it.startsWith(series.id) },
                    watchedCount = watchedCounts[series.id] ?: 0,
                    accent       = accent,
                    surface      = surface,
                    textPrimary  = textPrimary,
                    onClick      = { onSeriesClick(series) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeriesSectionHeader(
    title: String,
    accent: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = title,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Medium,
            color      = textPrimary,
        )
        Text(
            text     = "See All >",
            fontSize = 13.sp,
            color    = accent,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Genre chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeriesGenreChip(
    label: String,
    selected: Boolean,
    accent: Color,
    textPrimary: Color,
    divider: Color,
    onClick: () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }

    val borderColor = if (selected || isFocused.value) accent else divider
    val bgColor     = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor   = if (selected || isFocused.value) accent else textPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50.dp))
            .onFocusChanged { isFocused.value = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, color = textColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Episode continue watching card — 16:9 landscape (200 × 112 dp)
// Shows episode thumbnail + S01E04 subtitle overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EpisodeContinueCard(
    item:    RecentlyWatchedEntity,
    accent:  Color,
    surface: Color,
    onClick: () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "epScale")

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = 200.dp, height = 112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(surface)
            .border(
                width = if (isFocused.value) 2.dp else 0.dp,
                color = if (isFocused.value) accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
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
        AsyncImage(
            model              = item.logoUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        // Gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
        )
        // Series name + episode subtitle
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
        ) {
            Text(
                text       = item.name,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text     = item.subtitle,
                    fontSize = 10.sp,
                    color    = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ranked series poster card — Popular carousel (120 × 180 dp + rank + seasons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RankedSeriesPosterCard(
    series:      SeriesGridItem,
    rank:        Int,
    hasProgress: Boolean,
    watchedCount: Int,
    accent:      Color,
    surface:     Color,
    textPrimary: Color,
    onClick:     () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "rankScale")

    Column(
        modifier            = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .width(120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
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
            AsyncImage(
                model              = series.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            // Gradient + rank number at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Text(
                text       = rank.toString(),
                fontSize   = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White.copy(alpha = 0.85f),
                lineHeight = 44.sp,
                modifier   = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 2.dp),
            )
            // Season count badge — top-right
            if (series.seasonCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text     = "S${series.seasonCount}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color    = Color.White,
                    )
                }
            }
            // In-progress dot
            if (hasProgress) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50.dp))
                        .background(accent)
                )
            }
        }
        Text(
            text     = series.name,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Standard series portrait card — grids and "New" carousel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SeriesPortraitCard(
    series:      SeriesGridItem,
    hasProgress: Boolean,
    watchedCount: Int,
    accent:      Color,
    surface:     Color,
    textPrimary: Color,
    fixedWidth:  Dp? = null,
    fixedHeight: Dp? = null,
    onClick:     () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "seriesScale")

    val imageModifier = if (fixedWidth != null && fixedHeight != null)
        Modifier.size(width = fixedWidth, height = fixedHeight)
    else
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)

    Column(
        modifier            = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = imageModifier
                .clip(RoundedCornerShape(8.dp))
                .background(surface)
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
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
            AsyncImage(
                model              = series.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            // Season count badge — top-right
            if (series.seasonCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "S${series.seasonCount}",
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                    )
                }
            }
            // In-progress dot — top-left
            if (hasProgress) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50.dp))
                        .background(accent)
                )
            }
        }
        Text(
            text     = series.name,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
