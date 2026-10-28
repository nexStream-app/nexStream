package app.nexstream.player.ui.screens.movies

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
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun GlassMovieDetailScreen(
    movie: MovieEntity,
    resumePosition: Long = 0L,
    isBookmarked: Boolean = false,
    moreLikeThis: List<MovieGridItem> = emptyList(),
    onBack: () -> Unit,
    onPlay: (startPosition: Long) -> Unit,
    onToggleWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onMovieClick: (MovieGridItem) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val hasProgress = resumePosition > 0L
    val playFR     = remember { FocusRequester() }
    val firstSimFR = remember { FocusRequester() }
    val hasSim     = moreLikeThis.isNotEmpty()

    LaunchedEffect(movie.id) {
        delay(120)
        try { playFR.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassTokens.Background),
    ) {

        // ── Hero backdrop ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AsyncImage(
                model              = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f   to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.30f),
                            1f   to Color.Black.copy(alpha = 0.92f),
                        )
                    )
            )
            // Metadata overlaid at bottom-left of hero
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.65f)
                    .padding(start = GlassTokens.ScreenGutter, bottom = 20.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text       = movie.name,
                    color      = GlassTokens.TextPrimary,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 30.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    movie.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
                        ?.let { DetailChipMD(it) }
                    movie.certification?.takeIf { it.isNotBlank() }
                        ?.let { DetailChipMD(it, accent = true) }
                    (movie.rating?.toDoubleOrNull()?.let { "%.1f".format(it) } ?: movie.rating)
                        ?.takeIf { it.isNotBlank() }?.let { DetailChipMD("★ $it") }
                    movie.duration?.takeIf { it.isNotBlank() }?.let { DetailChipMD(it) }
                }
                movie.genre?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = GlassTokens.AccentBlue, fontSize = 11.sp)
                }
                movie.plot?.takeIf { it.isNotBlank() }?.let {
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

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.ScreenGutter, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailButtonMD(
                icon     = Icons.Default.ArrowBack,
                label    = "Back",
                primary  = false,
                modifier = Modifier.width(110.dp),
                onFocusDown = if (hasSim) {
                    { try { firstSimFR.requestFocus() } catch (_: Exception) {} }
                } else null,
                onClick  = onBack,
            )
            DetailButtonMD(
                icon           = Icons.Default.PlayArrow,
                label          = if (hasProgress) "Resume" else "Play",
                primary        = true,
                modifier       = Modifier.weight(1f),
                focusRequester = playFR,
                onFocusDown    = if (hasSim) {
                    { try { firstSimFR.requestFocus() } catch (_: Exception) {} }
                } else null,
                onClick        = { onPlay(if (hasProgress) resumePosition else 0L) },
            )
            DetailButtonMD(
                icon     = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label    = if (isBookmarked) "In My List" else "My List",
                primary  = false,
                modifier = Modifier.weight(1f),
                onFocusDown = if (hasSim) {
                    { try { firstSimFR.requestFocus() } catch (_: Exception) {} }
                } else null,
                onClick  = onToggleWatchlist,
            )
            DetailButtonMD(
                icon     = Icons.Default.Download,
                label    = "Download",
                primary  = false,
                modifier = Modifier.weight(1f),
                onFocusDown = if (hasSim) {
                    { try { firstSimFR.requestFocus() } catch (_: Exception) {} }
                } else null,
                onClick  = onDownload,
            )
        }

        // ── Similar Movies ────────────────────────────────────────────────────
        if (hasSim) {
            Column(
                modifier = Modifier
                    .padding(horizontal = GlassTokens.ScreenGutter)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text       = "Similar Movies",
                    color      = GlassTokens.TextPrimary,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(bottom = 10.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
                    contentPadding        = PaddingValues(end = GlassTokens.ScreenGutter),
                ) {
                    itemsIndexed(moreLikeThis) { idx, m ->
                        GlasPosterTile(
                            imageUrl = m.posterUrl,
                            title    = m.name,
                            rating   = m.rating?.toDoubleOrNull()?.let { "%.1f".format(it) }
                                ?: m.rating,
                            modifier = if (idx == 0)
                                Modifier
                                    .width(120.dp)
                                    .focusRequester(firstSimFR)
                                    .onKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                            try { playFR.requestFocus() } catch (_: Exception) {}
                                            true
                                        } else false
                                    }
                            else Modifier.width(120.dp),
                            onClick  = { onMovieClick(m) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailButtonMD(
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
                    focused  -> GlassTokens.AccentDefault
                    primary  -> GlassTokens.AccentDefault.copy(alpha = 0.35f)
                    else     -> GlassTokens.GlassBorder
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector    = icon,
            contentDescription = null,
            tint           = contentColor,
            modifier       = Modifier.size(20.dp),
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
private fun DetailChipMD(text: String, accent: Boolean = false) {
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
