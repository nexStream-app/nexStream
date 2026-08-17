package app.nexstream.player.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.ui.theme.LocalNsAccent
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun SearchPreviewDialog(
    movie: MovieEntity? = null,
    series: SeriesEntity? = null,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onGoToLibrary: () -> Unit
) {
    val accent = LocalNsAccent.current

    val name        = movie?.name        ?: series?.name        ?: return
    val posterUrl   = movie?.posterUrl   ?: series?.posterUrl
    val backdropUrl = movie?.backdropUrl ?: series?.backdropUrl ?: posterUrl
    val plot        = movie?.plot        ?: series?.plot
    val cast        = movie?.cast        ?: series?.cast
    val rating      = movie?.rating      ?: series?.rating
    val cert        = movie?.certification ?: series?.certification
    val isSeries    = movie == null

    val playLabel    = if (isSeries) "View Series" else "Play"
    val libraryLabel = if (isSeries) "Go to Series" else "Go to Movies"

    // Button focus: 0 = play, 1 = go-to
    var selectedBtn by remember { mutableIntStateOf(0) }
    val playFR    = remember { FocusRequester() }
    val libraryFR = remember { FocusRequester() }
    val dialogFR  = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        try { playFR.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(14.dp))
        ) {
            // Backdrop
            AsyncImage(
                model              = backdropUrl,
                contentDescription = null,
                modifier           = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentScale       = ContentScale.Crop,
                alignment          = Alignment.TopCenter
            )

            // Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.2f),
                                0.5f to Color.Black.copy(alpha = 0.70f),
                                1.0f to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(dialogFR)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.Back, Key.Escape -> { onDismiss(); true }
                            Key.DirectionLeft -> {
                                if (selectedBtn == 1) {
                                    selectedBtn = 0
                                    try { playFR.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            Key.DirectionRight -> {
                                if (selectedBtn == 0) {
                                    selectedBtn = 1
                                    try { libraryFR.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Poster + info row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    // Poster
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(135.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(
                                model              = posterUrl,
                                contentDescription = name,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector        = if (isSeries) Icons.Default.VideoLibrary else Icons.Default.Movie,
                                contentDescription = null,
                                tint               = Color.White.copy(alpha = 0.4f),
                                modifier           = Modifier.size(40.dp).align(Alignment.Center)
                            )
                        }
                    }

                    // Info
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text       = name,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )

                        // Rating + cert row
                        if (rating != null || cert != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                rating?.let { r ->
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint     = Color(0xFFFFD700),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(r, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                                    }
                                }
                                cert?.let { c ->
                                    Text(
                                        text     = c,
                                        fontSize = 11.sp,
                                        color    = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Plot
                        if (plot?.isNotEmpty() == true) {
                            Text(
                                text       = plot,
                                fontSize   = 12.sp,
                                color      = Color.White.copy(alpha = 0.75f),
                                maxLines   = 4,
                                overflow   = TextOverflow.Ellipsis,
                                lineHeight = 17.sp
                            )
                        }

                        // Cast
                        if (cast?.isNotEmpty() == true) {
                            Text(
                                text       = "Cast: $cast",
                                fontSize   = 11.sp,
                                color      = Color.White.copy(alpha = 0.55f),
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    PreviewButton(
                        label          = playLabel,
                        icon           = if (isSeries) Icons.Default.VideoLibrary else Icons.Default.PlayArrow,
                        isPrimary      = true,
                        focusRequester = playFR,
                        accent         = accent,
                        onFocused      = { selectedBtn = 0 },
                        onClick        = { onPlay(); onDismiss() }
                    )
                    PreviewButton(
                        label          = libraryLabel,
                        icon           = Icons.Default.ChevronRight,
                        isPrimary      = false,
                        focusRequester = libraryFR,
                        accent         = accent,
                        onFocused      = { selectedBtn = 1 },
                        onClick        = { onGoToLibrary(); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    focusRequester: FocusRequester,
    accent: Color,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg     = when {
        focused && isPrimary  -> accent
        focused && !isPrimary -> Color.White.copy(alpha = 0.25f)
        isPrimary             -> accent.copy(alpha = 0.7f)
        else                  -> Color.White.copy(alpha = 0.12f)
    }
    val border = if (focused) accent else Color.White.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
