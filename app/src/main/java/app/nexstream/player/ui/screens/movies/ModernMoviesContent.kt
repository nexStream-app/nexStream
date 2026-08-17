package app.nexstream.player.ui.screens.movies

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
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernMoviesContent(
    movies: List<MovieGridItem>,
    progressItemIds: Set<String> = emptySet(),
    selectedCategory: String? = null,
    onMovieClick: (streamUrl: String, movieId: String, name: String) -> Unit = { _, _, _ -> },
    onMovieLongPress: (MovieGridItem) -> Unit = {},
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current

    val showCarousels = selectedCategory == null
    var popularMovies by remember { mutableStateOf<List<MovieGridItem>>(emptyList()) }
    var newMovies     by remember { mutableStateOf<List<MovieGridItem>>(emptyList()) }
    LaunchedEffect(movies) {
        val (popular, recent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            Pair(
                movies.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }.take(12),
                movies.sortedByDescending { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "0000-00-00" }.take(24)
            )
        }
        popularMovies = popular
        newMovies     = recent
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 130.dp),
        modifier              = Modifier.fillMaxSize().background(background),
        contentPadding        = PaddingValues(bottom = 32.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showCarousels && popularMovies.isNotEmpty()) {
            // ── Popular header ───────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                MoviesSectionHeader(title = "Top Rated", accent = accent, textPrimary = textPrimary)
            }
            // ── Popular carousel (Row + horizontalScroll) ────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    popularMovies.forEachIndexed { index, movie ->
                        RankedMoviePosterCard(
                            movie       = movie,
                            rank        = index + 1,
                            hasProgress = movie.id in progressItemIds,
                            accent      = accent,
                            surface     = surface,
                            textPrimary = textPrimary,
                            onClick     = { onMovieClick(movie.streamUrl, movie.id, movie.name) }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (newMovies.isNotEmpty()) {
                // ── New header ───────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MoviesSectionHeader(title = "New", accent = accent, textPrimary = textPrimary)
                }
                // ── New carousel ─────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        newMovies.forEach { movie ->
                            MoviesPortraitCard(
                                movie       = movie,
                                hasProgress = movie.id in progressItemIds,
                                accent      = accent,
                                surface     = surface,
                                textPrimary = textPrimary,
                                fixedWidth  = 120.dp,
                                fixedHeight = 180.dp,
                                onClick     = { onMovieClick(movie.streamUrl, movie.id, movie.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

        }

        // ── Grid ────────────────────────────────────────────────────────────
        if (movies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text     = if (selectedCategory != null) "No movies in this category" else "No movies found",
                        color    = textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            // First column item needs left padding to align with section headers
            val gridMovies = movies
            items(
                count = gridMovies.size,
                key   = { gridMovies[it].id },
            ) { index ->
                val movie = gridMovies[index]
                MoviesPortraitCard(
                    movie       = movie,
                    hasProgress = movie.id in progressItemIds,
                    accent      = accent,
                    surface     = surface,
                    textPrimary = textPrimary,
                    onClick     = { onMovieClick(movie.streamUrl, movie.id, movie.name) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoviesSectionHeader(
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
private fun MoviesGenreChip(
    label: String,
    selected: Boolean,
    accent: Color,
    textPrimary: Color,
    divider: Color,
    onClick: () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }

    val borderColor = if (selected || isFocused.value) accent else divider
    val bgColor     = when {
        selected        -> accent.copy(alpha = 0.30f)
        isFocused.value -> accent.copy(alpha = 0.20f)
        else            -> Color.Transparent
    }
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
// Ranked poster card — "Popular" carousel (120 × 180 dp + rank badge)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RankedMoviePosterCard(
    movie:       MovieGridItem,
    rank:        Int,
    hasProgress: Boolean,
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
                model              = movie.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
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
            if (!movie.certification.isNullOrBlank()) {
                val bgColor = LocalNsBackground.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = movie.certification,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = bgColor,
                    )
                }
            }
            if (hasProgress) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50.dp))
                        .background(accent)
                )
            }
        }
        Text(
            text     = movie.name,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Standard portrait poster card — grid and "New" carousel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MoviesPortraitCard(
    movie:       MovieGridItem,
    hasProgress: Boolean,
    accent:      Color,
    surface:     Color,
    textPrimary: Color,
    fixedWidth:  Dp? = null,
    fixedHeight: Dp? = null,
    onClick:     () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused.value) 1.05f else 1.0f, label = "posterScale")

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
                model              = movie.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            if (!movie.certification.isNullOrBlank()) {
                val bgColor = LocalNsBackground.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = movie.certification,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = bgColor,
                    )
                }
            }
            if (hasProgress) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50.dp))
                        .background(accent)
                )
            }
        }
        Text(
            text     = movie.name,
            fontSize = 11.sp,
            color    = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
