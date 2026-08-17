package app.nexstream.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import coil.compose.AsyncImage

@Composable
fun ContentActionDialog(
    name: String,
    posterUrl: String? = null,
    subtitle: String? = null,
    goToLabel: String,
    removeLabel: String,
    removeIcon: ImageVector = Icons.Default.Delete,
    removeIsDestructive: Boolean = true,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onGoTo: () -> Unit
) {
    // 0=Close, 1=Remove, 2=GoTo(default)
    var selectedButton by remember { mutableStateOf(2) }
    val dialogFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth  = false,
            dismissOnBackPress       = true,
            dismissOnClickOutside    = true
        )
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f),
            shape          = RoundedCornerShape(16.dp),
            color          = Color.Black,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + 3) % 3; true }
                            Key.DirectionRight -> { selectedButton = (selectedButton + 1) % 3; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                when (selectedButton) { 0 -> onDismiss(); 1 -> onRemove(); 2 -> onGoTo() }
                                true
                            }
                            Key.Back -> { onDismiss(); true }
                            else     -> false
                        }
                    }
            ) {
                // Backdrop
                if (!posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model              = posterUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                        alignment          = Alignment.Center,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.92f),
                                )
                            )
                        )
                )

                // Close pill — top-right
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    ContentDialogPill(
                        icon       = Icons.Default.Close,
                        label      = "Close",
                        isSelected = selectedButton == 0,
                        accent     = MaterialTheme.colorScheme.primary,
                        onClick    = onDismiss,
                    )
                }

                // Bottom content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = name,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text     = subtitle,
                            fontSize = 13.sp,
                            color    = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // Remove / action button (index 1)
                        ContentDialogPill(
                            icon       = removeIcon,
                            label      = removeLabel,
                            isSelected = selectedButton == 1,
                            accent     = if (removeIsDestructive) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                            onClick    = onRemove,
                        )
                        // Go To button (index 2 / default)
                        ContentDialogPill(
                            icon       = Icons.Default.PlayArrow,
                            label      = goToLabel,
                            isSelected = selectedButton == 2,
                            accent     = MaterialTheme.colorScheme.primary,
                            onClick    = onGoTo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentDialogPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg      = if (isSelected) accent else Color.White.copy(alpha = 0.15f)
    val content = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = content)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = content)
    }
}
