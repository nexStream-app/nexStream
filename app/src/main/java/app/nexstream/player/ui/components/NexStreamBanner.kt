package app.nexstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import kotlinx.coroutines.delay

@Composable
fun NexStreamBanner(
    message: String?,
    icon: ImageVector? = null,
    durationMs: Long = 3000L,
    onDismiss: () -> Unit,
) {
    val nsTheme = LocalNexStreamTheme.current

    LaunchedEffect(message) {
        if (message != null) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit  = slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 20.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(nsTheme.sidebar.categorySelectedBg.copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = nsTheme.sidebar.categoryTextSelected,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = message ?: "",
                    color = nsTheme.sidebar.categoryTextSelected,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
