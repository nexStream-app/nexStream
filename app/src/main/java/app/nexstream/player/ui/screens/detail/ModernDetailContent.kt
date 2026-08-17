package app.nexstream.player.ui.screens.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsGradientOverlay
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextOnAccent
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

// ─────────────────────────────────────────────────────────────────────────────
// Public data model — caller fills this from MovieEntity / SeriesEntity
// ─────────────────────────────────────────────────────────────────────────────

data class ModernDetailItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val duration: String?,
    val certification: String?,
    val cast: String?,
    val director: String?,
    val isSeries: Boolean = false,
    val seasonCount: Int = 0,
)

// ─────────────────────────────────────────────────────────────────────────────
// ModernDetailContent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModernDetailContent(
    item: ModernDetailItem,
    resumePositionMs: Long = 0L,
    isBookmarked: Boolean = false,
    onPlay: (startPositionMs: Long) -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onMarkWatched: () -> Unit = {},
) {
    val accent          = LocalNsAccent.current
    val background      = LocalNsBackground.current
    val surface         = LocalNsSurface.current
    val textPrimary     = LocalNsTextPrimary.current
    val textSecondary   = LocalNsTextSecondary.current
    val textOnAccent    = LocalNsTextOnAccent.current
    val gradientOverlay = LocalNsGradientOverlay.current
    val divider         = LocalNsDivider.current

    val hasResume = resumePositionMs > 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Full-bleed backdrop ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            AsyncImage(
                model              = item.backdropUrl ?: item.posterUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            // Gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, gradientOverlay)
                        )
                    )
            )
            // Title + metadata overlaid at bottom-left of backdrop
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text       = item.name,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                MetadataRow(item = item, textSecondary = Color.White.copy(alpha = 0.75f))
            }
        }

        // ── Content below backdrop ────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Synopsis
            if (!item.plot.isNullOrBlank()) {
                Text(
                    text     = item.plot,
                    fontSize = 14.sp,
                    color    = textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action buttons
            ActionButtonsRow(
                hasResume        = hasResume,
                resumePositionMs = resumePositionMs,
                isBookmarked     = isBookmarked,
                accent           = accent,
                textOnAccent     = textOnAccent,
                surface          = surface,
                divider          = divider,
                textSecondary    = textSecondary,
                onPlay           = onPlay,
                onToggleWatchlist = onToggleWatchlist,
                onMarkWatched    = onMarkWatched,
            )

            // Ratings row
            if (!item.rating.isNullOrBlank()) {
                RatingsRow(rating = item.rating, surface = surface, textPrimary = textPrimary)
            }

            HorizontalDivider(color = divider.copy(alpha = 0.4f))

            // Cast / director info
            if (!item.cast.isNullOrBlank()) {
                DetailInfoRow(label = "Cast", value = item.cast, textPrimary = textPrimary, textSecondary = textSecondary)
            }
            if (!item.director.isNullOrBlank()) {
                DetailInfoRow(label = "Director", value = item.director, textPrimary = textPrimary, textSecondary = textSecondary)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetadataRow(item: ModernDetailItem, textSecondary: Color) {
    val parts = buildList {
        if (!item.certification.isNullOrBlank()) add(item.certification)
        if (!item.genre.isNullOrBlank())         add(item.genre)
        if (!item.releaseDate.isNullOrBlank())   add(item.releaseDate)
        if (!item.duration.isNullOrBlank())      add(item.duration)
        if (item.isSeries && item.seasonCount > 0) add("${item.seasonCount} seasons")
    }
    if (parts.isEmpty()) return
    Text(
        text     = parts.joinToString(" • "),
        fontSize = 12.sp,
        color    = textSecondary,
    )
}

@Composable
private fun ActionButtonsRow(
    hasResume:        Boolean,
    resumePositionMs: Long,
    isBookmarked:     Boolean,
    accent:           Color,
    textOnAccent:     Color,
    surface:          Color,
    divider:          Color,
    textSecondary:    Color,
    onPlay:           (Long) -> Unit,
    onToggleWatchlist: () -> Unit,
    onMarkWatched:    () -> Unit,
) {
    // Primary row: Play + Watchlist
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment   = Alignment.CenterVertically
    ) {
        // Play / Resume — primary pill button
        ModernPillButton(
            label       = if (hasResume) "Resume" else "Play",
            icon        = Icons.Default.PlayArrow,
            filled      = true,
            accent      = accent,
            textOnAccent = textOnAccent,
            surface     = surface,
            divider     = divider,
            onClick     = { onPlay(if (hasResume) resumePositionMs else 0L)  }
        )

        // Start over — only shown when resume is available
        if (hasResume) {
            ModernPillButton(
                label   = "Start Over",
                filled  = false,
                accent  = accent,
                textOnAccent = textOnAccent,
                surface = surface,
                divider = divider,
                onClick = { onPlay(0L) }
            )
        }

        // Watchlist — secondary pill
        ModernPillButton(
            label  = if (isBookmarked) "Saved" else "My List",
            icon   = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            filled = false,
            accent = accent,
            textOnAccent = textOnAccent,
            surface = surface,
            divider = divider,
            onClick = onToggleWatchlist
        )
    }

    // Secondary row: Mark as Watched
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModernPillButton(
            label   = "Mark as Watched",
            icon    = Icons.Default.CheckCircle,
            filled  = false,
            accent  = accent,
            textOnAccent = textOnAccent,
            surface = surface,
            divider = divider,
            tertiary = true,
            textSecondary = textSecondary,
            onClick = onMarkWatched
        )
    }
}

@Composable
private fun ModernPillButton(
    label:        String,
    icon:         androidx.compose.ui.graphics.vector.ImageVector? = null,
    filled:       Boolean,
    tertiary:     Boolean = false,
    accent:       Color,
    textOnAccent: Color,
    surface:      Color,
    divider:      Color,
    textSecondary: Color = Color.Unspecified,
    onClick:      () -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused.value) 1.05f else 1.0f,
        label       = "btnScale"
    )

    val borderColor = when {
        isFocused.value -> accent
        tertiary        -> divider
        else            -> accent
    }
    val borderWidth = if (isFocused.value) 2.dp else if (filled) 0.dp else 1.dp
    val bgColor = when {
        filled          -> accent
        isFocused.value -> accent.copy(alpha = 0.15f)
        else            -> Color.Transparent
    }
    val contentColor = when {
        filled    -> textOnAccent
        tertiary  -> if (textSecondary != Color.Unspecified) textSecondary else accent
        else      -> accent
    }

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(50.dp))
            .onFocusChanged { isFocused.value = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = contentColor,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Text(
                text       = label,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = contentColor
            )
        }
    }
}

@Composable
private fun RatingsRow(rating: String, surface: Color, textPrimary: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // TMDB / generic score badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = surface,
        ) {
            Row(
                modifier            = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Star,
                    contentDescription = null,
                    tint               = Color(0xFFFFD700),
                    modifier           = Modifier.size(14.dp)
                )
                Text(
                    text     = rating,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = textPrimary
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String, textPrimary: Color, textSecondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text       = label,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = textSecondary
        )
        Text(
            text     = value,
            fontSize = 14.sp,
            color    = textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
