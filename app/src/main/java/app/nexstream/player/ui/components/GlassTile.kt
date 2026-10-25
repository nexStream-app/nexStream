package app.nexstream.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.GlassTokens
import coil.compose.AsyncImage

// 16:9 landscape tile — channels, live sport, continue watching
@Composable
fun GlassTile(
    imageUrl: String?,
    title: String,
    progressFraction: Float = 0f,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) GlassTokens.FocusScaleMax else 1f,
        animationSpec = tween(GlassTokens.FocusScaleDurationMs),
        label = "tileScale",
    )
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(GlassTokens.RadiusTile))
            .then(
                if (focused) Modifier.border(
                    GlassTokens.FocusOutlineWidth,
                    GlassTokens.AccentDefault,
                    RoundedCornerShape(GlassTokens.RadiusTile),
                ) else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter)
                ) { onClick(); true } else false
            }
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom scrim + progress bar + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(GlassTokens.AccentDefault),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = title,
                    color = GlassTokens.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GlassTokens.LiveBadge)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 2:3 portrait poster tile — movies, series, picks
@Composable
fun GlasPosterTile(
    imageUrl: String?,
    title: String,
    rating: String? = null,
    ageRating: String? = null,
    hasNewEpisode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) GlassTokens.FocusScaleMax else 1f,
        animationSpec = tween(GlassTokens.FocusScaleDurationMs),
        label = "posterScale",
    )
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(GlassTokens.RadiusTile))
            .then(
                if (focused) Modifier.border(
                    GlassTokens.FocusOutlineWidth,
                    GlassTokens.AccentDefault,
                    RoundedCornerShape(GlassTokens.RadiusTile),
                ) else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter || ev.key == Key.DirectionCenter)
                ) { onClick(); true } else false
            }
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))),
        ) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rating != null) {
                        Text(
                            text = rating,
                            color = GlassTokens.AccentDefault,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (ageRating != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(GlassTokens.GlassSmallSolid)
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        ) {
                            Text(ageRating, color = GlassTokens.TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
                Text(
                    text = title,
                    color = GlassTokens.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasNewEpisode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(GlassTokens.AccentDefault),
            )
        }
    }
}
