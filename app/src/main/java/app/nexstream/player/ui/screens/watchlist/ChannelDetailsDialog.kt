package app.nexstream.player.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.ui.theme.LocalNsAccent
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChannelDetailsDialog(
    channel: ChannelEntity,
    currentProgram: ProgramEntity? = null,
    nextProgram: ProgramEntity? = null,
    isBookmarked: Boolean = true,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onToggleWatchlist: () -> Unit = {},
    onGoToEpg: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val accent = LocalNsAccent.current

    // Button indices: 0=Close, 1=MyList, 2=Watch, 3=GoToEpg
    val buttonCount = if (onGoToEpg != null) 4 else 3
    var selectedButton by remember { mutableStateOf(2) }
    val dialogFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft  -> { selectedButton = (selectedButton - 1 + buttonCount) % buttonCount; true }
                            Key.DirectionRight -> { selectedButton = (selectedButton + 1) % buttonCount; true }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                when (selectedButton) {
                                    0 -> onDismiss()
                                    1 -> onToggleWatchlist()
                                    2 -> onWatch()
                                    3 -> onGoToEpg?.invoke()
                                }
                                true
                            }
                            Key.Back -> { onDismiss(); true }
                            else -> false
                        }
                    }
            ) {
                // ── Backdrop — programme image preferred over channel logo ────
                val backdropUrl = currentProgram?.icon?.takeIf { it.isNotEmpty() } ?: channel.logoUrl
                if (!backdropUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model              = backdropUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                        alignment          = Alignment.Center
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
                }
                // Channel logo inset — top-left when programme image is backdrop
                if (currentProgram?.icon?.isNotEmpty() == true && !channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model              = channel.logoUrl,
                        contentDescription = null,
                        modifier           = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentScale       = ContentScale.Fit
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f  to Color.Black.copy(alpha = 0.30f),
                                0.35f to Color.Black.copy(alpha = 0.55f),
                                0.70f to Color.Black.copy(alpha = 0.88f),
                                1.0f  to Color.Black.copy(alpha = 0.97f),
                            )
                        )
                    )
                )

                // ── Close — top-right (index 0) ───────────────────────────────
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedButton == 0) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.clickable(onClick = onDismiss)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = Color.White)
                            Text("Close", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }

                // ── Bottom content ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Channel name
                    Text(
                        text       = channel.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )

                    // Current programme
                    if (currentProgram != null) {
                        ChannelProgramRow(
                            label   = "Now",
                            program = currentProgram,
                            accent  = accent,
                            tf      = timeFormat,
                        )
                    }

                    // Next programme
                    if (nextProgram != null) {
                        ChannelProgramRow(
                            label   = "Next",
                            program = nextProgram,
                            accent  = null,
                            tf      = timeFormat,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!channel.groupTitle.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    channel.groupTitle!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }
                        if (channel.tvArchive != 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1565C0).copy(alpha = 0.85f)
                            ) {
                                Text(
                                    "CatchUp ${if (channel.tvArchiveDuration > 0) "${channel.tvArchiveDuration}d" else ""}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── Action buttons ────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1 = My List
                        ChannelActionPill(
                            icon       = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label      = if (isBookmarked) "Remove" else "My List",
                            isSelected = selectedButton == 1,
                            accent     = accent,
                            onClick    = onToggleWatchlist
                        )
                        // 3 = Go to EPG
                        if (onGoToEpg != null) {
                            ChannelActionPill(
                                icon       = Icons.Default.CalendarToday,
                                label      = "Go to EPG",
                                isSelected = selectedButton == 3,
                                accent     = accent,
                                onClick    = onGoToEpg
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // 2 = Watch Now (default, right-aligned)
                        ChannelActionPill(
                            icon       = Icons.Default.PlayArrow,
                            label      = "Watch Now",
                            isSelected = selectedButton == 2,
                            accent     = accent,
                            onClick    = onWatch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelProgramRow(
    label: String,
    program: ProgramEntity,
    accent: androidx.compose.ui.graphics.Color?,
    tf: SimpleDateFormat,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = (accent ?: Color.White.copy(alpha = 0.2f)).let { if (accent != null) it.copy(alpha = 0.85f) else it }
        ) {
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text     = "${tf.format(Date(program.startTime))}–${tf.format(Date(program.endTime))}  ${program.title}",
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White.copy(alpha = if (accent != null) 0.95f else 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelActionPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val bg      = if (isSelected) accent else Color.White.copy(alpha = 0.15f)
    val content = Color.White
    Surface(
        shape   = RoundedCornerShape(50.dp),
        color   = bg,
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = content)
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.Medium)
        }
    }
}
