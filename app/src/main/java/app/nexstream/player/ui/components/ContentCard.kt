package app.nexstream.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ContentCard(
    name:           String,
    posterUrl:      String?,
    defaultIcon:    ImageVector,
    badge:           String?        = null,
    focusRequester:  FocusRequester? = null,
    onFocused:       () -> Unit     = {},
    onDirectionLeft: (() -> Unit)?  = null,
    onClick:         () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale"
    )

    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .border(if (isFocused) 3.dp else 0.dp, primary, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged { fs ->
                isFocused = fs.isFocused
                if (fs.isFocused) onFocused()
            }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionLeft -> if (onDirectionLeft != null) { onDirectionLeft(); true } else false
                    else -> false
                }
            }
            .clickable { onClick() }
            .focusable()
    ) {
        if (!posterUrl.isNullOrEmpty()) {
            AsyncImage(
                model              = posterUrl,
                contentDescription = name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector        = defaultIcon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier           = Modifier.align(Alignment.Center).size(32.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
        )
        Text(
            text     = name,
            style    = MaterialTheme.typography.labelSmall,
            color    = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
        if (badge != null) {
            androidx.compose.material3.Surface(
                modifier       = Modifier.align(Alignment.TopEnd).padding(4.dp),
                shape          = RoundedCornerShape(4.dp),
                color          = primary.copy(alpha = 0.85f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text  = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
