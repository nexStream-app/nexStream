package app.nexstream.player.ui.screens.movies

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage

private data class SheetAction(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
)

@Composable
fun GlassDetailsSheet(
    movie: MovieEntity,
    resumePosition: Long = 0L,
    isBookmarked: Boolean = false,
    moreLikeThis: List<MovieGridItem> = emptyList(),
    onDismiss: () -> Unit,
    onPlay: (startPosition: Long) -> Unit,
    onTrailer: (() -> Unit)? = null,
    onToggleWatchlist: () -> Unit = {},
    onDownload: () -> Unit = {},
) {
    val hasProgress = resumePosition > 0L
    val hasTrailer  = !movie.trailerUrl.isNullOrBlank() && onTrailer != null

    val actions = buildList {
        add(SheetAction("play",      Icons.Default.PlayArrow,  if (hasProgress) "Resume" else "Play", primary = true))
        if (hasProgress) add(SheetAction("restart",  Icons.Default.Replay,      "Start Over"))
        if (hasTrailer)  add(SheetAction("trailer",  Icons.Default.Movie,       "Trailer"))
        add(SheetAction("watchlist", if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (isBookmarked) "In My List" else "My List"))
        add(SheetAction("download",  Icons.Default.Download,   "Download"))
        add(SheetAction("close",     Icons.Default.Close,      "Close"))
    }

    var selectedIdx by remember { mutableStateOf(0) }
    val playFR      = remember { FocusRequester() }
    val scope       = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { playFR.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Full-screen backdrop ──────────────────────────────────────────
            val backdropSrc = movie.backdropUrl ?: movie.posterUrl
            AsyncImage(
                model = backdropSrc,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient scrim — dark at right and bottom
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f   to Color.Black.copy(alpha = 0.10f),
                        0.35f to Color.Black.copy(alpha = 0.60f),
                        1f   to Color.Black.copy(alpha = 0.95f),
                    )
                )
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.6f to Color.Black.copy(alpha = 0.20f),
                        1f   to Color.Black.copy(alpha = 0.70f),
                    )
                )
            )

            // ── Right glass panel ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.50f)
                    .background(
                        GlassTokens.GlassPanelSolid,
                        RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel)
                    )
                    .border(
                        1.dp, GlassTokens.GlassBorder,
                        RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel)
                    )
                    .clip(RoundedCornerShape(topStart = GlassTokens.RadiusPanel, bottomStart = GlassTokens.RadiusPanel))
                    .padding(GlassTokens.PanelPadding)
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft  -> { onDismiss(); true }
                            Key.DirectionUp    -> { selectedIdx = (selectedIdx - 1 + actions.size) % actions.size; true }
                            Key.DirectionDown  -> { selectedIdx = (selectedIdx + 1) % actions.size; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                activateAction(actions[selectedIdx], hasProgress, resumePosition, onPlay, onTrailer, onToggleWatchlist, onDownload, onDismiss)
                                true
                            }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Metadata section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // Title
                        Text(
                            text = movie.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = GlassTokens.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Metadata chips row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val year = movie.releaseDate?.take(4)
                            if (!year.isNullOrBlank()) MetaChip(year)
                            val cert = movie.certification
                            if (!cert.isNullOrBlank()) MetaChip(cert, accent = true)
                            val rating = movie.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: movie.rating
                            if (!rating.isNullOrBlank()) MetaChip("★ $rating")
                            val dur = movie.duration
                            if (!dur.isNullOrBlank()) MetaChip(dur)
                        }

                        // Genre
                        if (!movie.genre.isNullOrBlank()) {
                            Text(
                                text = movie.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.AccentBlue,
                            )
                        }

                        // Plot
                        if (!movie.plot.isNullOrBlank()) {
                            Text(
                                text = movie.plot,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassTokens.TextSecondary,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        // Director + Cast
                        if (!movie.director.isNullOrBlank()) {
                            MetaRow("Director", movie.director)
                        }
                        if (!movie.cast.isNullOrBlank()) {
                            MetaRow("Cast", movie.cast)
                        }
                    }

                    // ── Action buttons ────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        actions.forEachIndexed { i, action ->
                            val focused = i == selectedIdx
                            SheetActionButton(
                                action = action,
                                focused = focused,
                                modifier = if (action.key == "play") Modifier.focusRequester(playFR)
                                           else Modifier,
                                onFocusChanged = { if (it) selectedIdx = i },
                                onClick = {
                                    activateAction(action, hasProgress, resumePosition, onPlay, onTrailer, onToggleWatchlist, onDownload, onDismiss)
                                },
                            )
                        }
                    }
                }
            }

            // ── More like this (bottom strip) ─────────────────────────────────
            if (moreLikeThis.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.50f)
                        .padding(start = GlassTokens.ScreenGutter, bottom = 24.dp),
                ) {
                    Text(
                        text = "More Like This",
                        style = MaterialTheme.typography.titleSmall,
                        color = GlassTokens.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp),
                    ) {
                        itemsIndexed(moreLikeThis.take(8)) { _, m ->
                            var tFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(
                                if (tFocused) GlassTokens.FocusScaleMin else 1f,
                                animationSpec = tween(GlassTokens.FocusScaleDurationMs)
                            )
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(150.dp)
                                    .scale(scale)
                                    .clip(RoundedCornerShape(GlassTokens.RadiusTile))
                                    .border(
                                        width = if (tFocused) GlassTokens.FocusOutlineWidth else 0.dp,
                                        color = if (tFocused) GlassTokens.AccentDefault else Color.Transparent,
                                        shape = RoundedCornerShape(GlassTokens.RadiusTile)
                                    )
                                    .onFocusChanged { tFocused = it.isFocused }
                                    .focusable()
                                    .clickable { /* handled by parent screen */ }
                            ) {
                                AsyncImage(
                                    model = m.posterUrl,
                                    contentDescription = m.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.80f)
                                            )
                                        )
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = m.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GlassTokens.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun activateAction(
    action: SheetAction,
    hasProgress: Boolean,
    resumePosition: Long,
    onPlay: (Long) -> Unit,
    onTrailer: (() -> Unit)?,
    onToggleWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (action.key) {
        "play"      -> onPlay(if (hasProgress) resumePosition else 0L)
        "restart"   -> onPlay(0L)
        "trailer"   -> onTrailer?.invoke()
        "watchlist" -> onToggleWatchlist()
        "download"  -> onDownload()
        "close"     -> onDismiss()
    }
}

@Composable
private fun SheetActionButton(
    action: SheetAction,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val bg = when {
        action.primary && focused -> GlassTokens.AccentDefault
        focused                   -> GlassTokens.AccentDefault.copy(alpha = 0.18f)
        else                      -> GlassTokens.GlassSmallSolid
    }
    val textColor = when {
        action.primary && focused -> GlassTokens.AccentOnDark
        focused                   -> GlassTokens.AccentDefault
        else                      -> GlassTokens.TextPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTokens.ButtonHeight)
            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
            .background(bg)
            .border(
                width = if (focused) GlassTokens.FocusOutlineWidth else 1.dp,
                color = if (focused) GlassTokens.AccentDefault else GlassTokens.GlassBorder,
                shape = RoundedCornerShape(GlassTokens.RadiusPill)
            )
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (action.primary) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MetaChip(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (accent) GlassTokens.AccentDefault.copy(alpha = 0.15f)
                else GlassTokens.GlassSmallSolid,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) GlassTokens.AccentDefault else GlassTokens.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.AccentBlue,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTokens.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
