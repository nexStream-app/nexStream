package app.nexstream.player.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.ui.theme.GlassTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// focusedZone strings — must match DpadZone.name values from PlayerScreen
private const val ZONE_CONTROLS = "CONTROLS"
private const val ZONE_SLIDER   = "SLIDER"
private const val ZONE_OPTIONS  = "OPTIONS"

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class BarButton(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val tint: Color = Color.Unspecified,
)

@Composable
fun GlassPlayerBar(
    isPlaying: Boolean,
    sliderPosition: Float,          // 0..1
    duration: Long,                 // ms; ≤0 means live/unknown
    hasScrubbing: Boolean,
    title: String?,
    subtitle: String?,
    nextProgramme: String?,
    nextEpisode: EpisodeEntity?,
    centreButtons: List<String>,
    centreIndex: Int,
    focusedZone: String,            // "CONTROLS" | "SLIDER" | "OPTIONS"
    showOptionsPanel: Boolean,
    optionsButtons: List<String>,
    optionsIndex: Int,
    videoQualityLabel: String?,
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onBack: () -> Unit = {},
    onSubtitles: () -> Unit = {},
    onSpeed: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onAspect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = GlassTokens.AccentDefault
    val onAccent = GlassTokens.AccentOnDark
    val textPrimary = GlassTokens.TextPrimary
    val textSecondary = GlassTokens.TextSecondary

    // Map centreButtons strings → display icons
    @Composable
    fun buttonDef(key: String): BarButton? = when (key) {
        "back"         -> BarButton(key, Icons.Default.Close, "Exit")
        "playpause"    -> BarButton(key, if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play")
        "rewind"       -> BarButton(key, Icons.Default.Replay, "Rewind")
        "forward"      -> BarButton(key, Icons.Default.Forward, "Skip")
        "prev"         -> BarButton(key, Icons.Default.SkipPrevious, "Prev")
        "next"         -> BarButton(key, Icons.Default.SkipNext, "Next")
        "ch_prev"      -> BarButton(key, Icons.Default.ArrowBack, "Prev Ch")
        "ch_next"      -> BarButton(key, Icons.Default.ArrowForward, "Next Ch")
        "sleep"        -> BarButton(key, Icons.Default.Bedtime, "Sleep")
        "stats"        -> BarButton(key, Icons.Default.BarChart, "Stats")
        "options"      -> BarButton(key, Icons.Default.Tune, "Options")
        "multi_screen" -> BarButton(key, Icons.Default.GridView, "Multi")
        "subtitles"    -> BarButton(key, Icons.Default.ClosedCaption, "Subs")
        else           -> null
    }

    @Composable
    fun optionDef(key: String): BarButton? = when (key) {
        "subtitles" -> BarButton(key, Icons.Default.ClosedCaption, "Subtitles")
        "speed"     -> BarButton(key, Icons.Default.Speed, "Speed")
        "resync_av" -> BarButton(key, Icons.Default.Sync, "Resync")
        "aspect"    -> BarButton(key, Icons.Default.FitScreen, "Aspect")
        "sub_pos"   -> BarButton(key, Icons.Default.VerticalAlignBottom, "Sub Pos")
        "sub_delay" -> BarButton(key, Icons.Default.Timer, "Sub Delay")
        else        -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                GlassTokens.GlassPanelSolid,
                RoundedCornerShape(topStart = GlassTokens.RadiusCard, topEnd = GlassTokens.RadiusCard)
            )
            .border(
                width = 1.dp,
                color = GlassTokens.GlassBorder,
                shape = RoundedCornerShape(topStart = GlassTokens.RadiusCard, topEnd = GlassTokens.RadiusCard)
            )
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Top row: title + quality badge ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!nextProgramme.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GlassTokens.GlassSmallSolid,
                            ) {
                                Text(
                                    text = "Next",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassTokens.TextTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = nextProgramme,
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Wall clock
                    var clockStr by remember { mutableStateOf("") }
                    val clockFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            clockStr = clockFmt.format(Date())
                            kotlinx.coroutines.delay(15_000)
                        }
                    }
                    if (clockStr.isNotBlank()) {
                        Text(
                            text = clockStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.TextTertiary,
                        )
                    }
                    if (!videoQualityLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GlassTokens.AccentBlue.copy(alpha = 0.20f),
                        ) {
                            Text(
                                text = videoQualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTokens.AccentBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // ── Progress slider (VOD/catchup only) ───────────────────────────
            if (hasScrubbing && duration > 0L) {
                val sliderFocused = focusedZone == ZONE_SLIDER
                val elapsed  = (sliderPosition * duration).toLong()
                val remaining = duration - elapsed
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (sliderFocused) 6.dp else 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GlassTokens.GlassBorder)
                            .then(if (sliderFocused) Modifier.border(1.dp, accent, RoundedCornerShape(3.dp)) else Modifier)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(sliderPosition.coerceIn(0f, 1f))
                                .background(if (sliderFocused) accent else GlassTokens.AccentBlue)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatMs(elapsed), style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextTertiary)
                        Text("-${formatMs(remaining)}", style = MaterialTheme.typography.labelSmall, color = GlassTokens.TextTertiary)
                    }
                }
            }

            // ── Options panel (shown when showOptionsPanel) ───────────────────
            if (showOptionsPanel && optionsButtons.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    optionsButtons.forEachIndexed { i, key ->
                        val def = optionDef(key) ?: return@forEachIndexed
                        val focused = focusedZone == ZONE_OPTIONS && i == optionsIndex
                        GlassBarIconBtn(
                            icon = def.icon,
                            label = def.label,
                            focused = focused,
                            size = 40.dp,
                            onClick = when (key) {
                                "subtitles" -> onSubtitles
                                "speed"     -> onSpeed
                                "aspect"    -> onAspect
                                else        -> { {} }
                            },
                        )
                    }
                }
            }

            // ── Centre control row ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                centreButtons.forEachIndexed { i, key ->
                    val def = buttonDef(key) ?: return@forEachIndexed
                    val focused = focusedZone == ZONE_CONTROLS && i == centreIndex
                    val isPlayPause = key == "playpause"
                    GlassBarIconBtn(
                        icon = def.icon,
                        label = def.label,
                        focused = focused,
                        size = if (isPlayPause) 56.dp else 48.dp,
                        accent = isPlayPause && focused,
                        onClick = when (key) {
                            "back"      -> onBack
                            "playpause" -> onPlayPause
                            "rewind"    -> { { onSeek((sliderPosition - 0.01f).coerceAtLeast(0f)) } }
                            "forward"   -> { { onSeek((sliderPosition + 0.01f).coerceAtMost(1f)) } }
                            "next"      -> onNextEpisode
                            "subtitles" -> onSubtitles
                            "speed"     -> onSpeed
                            "aspect"    -> onAspect
                            else        -> { {} }
                        },
                    )
                    if (i < centreButtons.size - 1) Spacer(Modifier.width(4.dp))
                }
            }

            // ── Up-next episode card ──────────────────────────────────────────
            if (nextEpisode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassTokens.GlassSmallSolid)
                        .clickable(onClick = onNextEpisode)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Up Next • S${nextEpisode.seasonNum}E${nextEpisode.episodeNum} – ${nextEpisode.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassBarIconBtn(
    icon: ImageVector,
    label: String,
    focused: Boolean,
    size: Dp = 48.dp,
    accent: Boolean = false,
    onClick: () -> Unit = {},
) {
    val bg = when {
        accent && focused -> GlassTokens.AccentDefault
        focused           -> GlassTokens.AccentDefault.copy(alpha = 0.20f)
        else              -> GlassTokens.GlassSmallSolid
    }
    val iconTint = when {
        accent && focused -> GlassTokens.AccentOnDark
        focused           -> GlassTokens.AccentDefault
        else              -> GlassTokens.TextPrimary
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (focused) Modifier.border(GlassTokens.FocusOutlineWidth, GlassTokens.AccentDefault, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size((size.value * 0.52f).dp),
        )
    }
}
