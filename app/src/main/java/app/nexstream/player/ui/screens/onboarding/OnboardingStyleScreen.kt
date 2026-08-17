package app.nexstream.player.ui.screens.onboarding

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.ThemeMode
import app.nexstream.player.ui.theme.getThemeModeFlow
import app.nexstream.player.ui.theme.saveThemeMode
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.theme.getUiStyleFlow
import app.nexstream.player.ui.theme.saveCloudSyncEnabled
import app.nexstream.player.ui.theme.saveFontScale
import app.nexstream.player.ui.theme.saveFontWeight
import app.nexstream.player.ui.theme.saveOnboardingDone
import app.nexstream.player.ui.theme.saveUiStyle
import app.nexstream.player.ui.theme.saveWhisperSubtitles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Focus indices
private const val FOCUS_DARK          = 0
private const val FOCUS_LIGHT         = 1
private const val FOCUS_MODERN        = 2
private const val FOCUS_CLASSIC       = 3
private const val FOCUS_TEXT_SIZE     = 4
private const val FOCUS_TEXT_WEIGHT   = 5
private const val FOCUS_CLOUD_SYNC    = 6
private const val FOCUS_AI_SUBTITLES  = 7
private const val FOCUS_GETSTARTED    = 8

@Composable
fun OnboardingStyleScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val accent        = LocalNsAccent.current
    val bg            = LocalNsBackground.current
    val surface       = LocalNsSurface.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    val currentThemeMode by context.getThemeModeFlow().collectAsState(initial = ThemeMode.DARK)
    val currentUiStyle   by context.getUiStyleFlow().collectAsState(initial = UiStyle.CLASSIC)

    var selectedMode      by remember { mutableStateOf(ThemeMode.DARK) }
    var selectedStyle     by remember { mutableStateOf(UiStyle.CLASSIC) }
    var selectedFontScale by remember { mutableStateOf(1.0f) }
    var selectedFontWeight by remember { mutableStateOf("normal") }
    var selectedCloudSync  by remember { mutableStateOf(true) }
    var selectedAiSubs     by remember { mutableStateOf(false) }

    LaunchedEffect(currentThemeMode) { selectedMode = currentThemeMode }
    LaunchedEffect(currentUiStyle)   { selectedStyle = currentUiStyle }

    // Check if annual/lifetime licence (available by onboarding time)
    val showCloudSync = remember {
        val prefs = context.getSharedPreferences("nexstream_licence", Context.MODE_PRIVATE)
        val type  = prefs.getString("licence_type", null)?.lowercase()
        type == "annual" || type == "lifetime"
    }

    val fontSizeOptions = remember {
        listOf(0.85f to "Small", 1.0f to "Normal", 1.15f to "Large", 1.3f to "Extra Large")
    }
    val fontWeightOptions = remember {
        listOf("normal" to "Normal", "semibold" to "Semi Bold", "bold" to "Bold")
    }

    var focusedIndex by remember { mutableStateOf(FOCUS_DARK) }
    val outerFocus   = remember { FocusRequester() }
    val scrollState  = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(120)
        try { outerFocus.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(focusedIndex) {
        if (scrollState.maxValue == 0) return@LaunchedEffect
        val fraction = when {
            focusedIndex <= FOCUS_CLASSIC     -> 0f
            focusedIndex == FOCUS_TEXT_SIZE   -> 0.35f
            focusedIndex == FOCUS_TEXT_WEIGHT -> 0.55f
            focusedIndex == FOCUS_CLOUD_SYNC  -> 0.78f
            else                              -> 1.0f
        }
        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
    }

    fun nextAfterTextWeight() = when {
        showCloudSync -> FOCUS_CLOUD_SYNC
        else          -> FOCUS_GETSTARTED
    }
    fun prevBeforeGetStarted() = when {
        showCloudSync -> FOCUS_CLOUD_SYNC
        else          -> FOCUS_TEXT_WEIGHT
    }

    fun handleKey(key: Key): Boolean {
        when (key) {
            // ── Left/Right ────────────────────────────────────────────────
            Key.DirectionRight -> when (focusedIndex) {
                FOCUS_DARK    -> focusedIndex = FOCUS_LIGHT
                FOCUS_MODERN  -> focusedIndex = FOCUS_CLASSIC
                FOCUS_TEXT_SIZE -> {
                    val idx = fontSizeOptions.indexOfFirst { it.first == selectedFontScale }
                    val next = (idx + 1).coerceAtMost(fontSizeOptions.lastIndex)
                    selectedFontScale = fontSizeOptions[next].first
                }
                FOCUS_TEXT_WEIGHT -> {
                    val idx = fontWeightOptions.indexOfFirst { it.first == selectedFontWeight }
                    val next = (idx + 1).coerceAtMost(fontWeightOptions.lastIndex)
                    selectedFontWeight = fontWeightOptions[next].first
                }
                FOCUS_CLOUD_SYNC   -> selectedCloudSync = true
                else -> return false
            }
            Key.DirectionLeft -> when (focusedIndex) {
                FOCUS_LIGHT   -> focusedIndex = FOCUS_DARK
                FOCUS_CLASSIC -> focusedIndex = FOCUS_MODERN
                FOCUS_TEXT_SIZE -> {
                    val idx = fontSizeOptions.indexOfFirst { it.first == selectedFontScale }
                    val prev = (idx - 1).coerceAtLeast(0)
                    selectedFontScale = fontSizeOptions[prev].first
                }
                FOCUS_TEXT_WEIGHT -> {
                    val idx = fontWeightOptions.indexOfFirst { it.first == selectedFontWeight }
                    val prev = (idx - 1).coerceAtLeast(0)
                    selectedFontWeight = fontWeightOptions[prev].first
                }
                FOCUS_CLOUD_SYNC   -> selectedCloudSync = false
                else -> return false
            }
            // ── Down ──────────────────────────────────────────────────────
            Key.DirectionDown -> when (focusedIndex) {
                FOCUS_DARK         -> focusedIndex = FOCUS_MODERN
                FOCUS_LIGHT        -> focusedIndex = FOCUS_CLASSIC
                FOCUS_MODERN,
                FOCUS_CLASSIC      -> focusedIndex = FOCUS_TEXT_SIZE
                FOCUS_TEXT_SIZE    -> focusedIndex = FOCUS_TEXT_WEIGHT
                FOCUS_TEXT_WEIGHT  -> focusedIndex = nextAfterTextWeight()
                FOCUS_CLOUD_SYNC   -> focusedIndex = FOCUS_GETSTARTED
                else -> return false
            }
            // ── Up ────────────────────────────────────────────────────────
            Key.DirectionUp -> when (focusedIndex) {
                FOCUS_MODERN        -> focusedIndex = FOCUS_DARK
                FOCUS_CLASSIC       -> focusedIndex = FOCUS_LIGHT
                FOCUS_TEXT_SIZE     -> focusedIndex = FOCUS_CLASSIC
                FOCUS_TEXT_WEIGHT   -> focusedIndex = FOCUS_TEXT_SIZE
                FOCUS_CLOUD_SYNC    -> focusedIndex = FOCUS_TEXT_WEIGHT
                FOCUS_GETSTARTED    -> focusedIndex = prevBeforeGetStarted()
                else -> return false
            }
            // ── Enter/OK ──────────────────────────────────────────────────
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when (focusedIndex) {
                FOCUS_DARK       -> selectedMode  = ThemeMode.DARK
                FOCUS_LIGHT      -> selectedMode  = ThemeMode.LIGHT
                FOCUS_MODERN     -> selectedStyle = UiStyle.MODERN
                FOCUS_CLASSIC    -> selectedStyle = UiStyle.CLASSIC
                FOCUS_TEXT_SIZE  -> {
                    val idx  = fontSizeOptions.indexOfFirst { it.first == selectedFontScale }
                    val next = (idx + 1) % fontSizeOptions.size
                    selectedFontScale = fontSizeOptions[next].first
                }
                FOCUS_TEXT_WEIGHT -> {
                    val idx  = fontWeightOptions.indexOfFirst { it.first == selectedFontWeight }
                    val next = (idx + 1) % fontWeightOptions.size
                    selectedFontWeight = fontWeightOptions[next].first
                }
                FOCUS_CLOUD_SYNC   -> selectedCloudSync = !selectedCloudSync
                FOCUS_GETSTARTED   -> scope.launch {
                    context.saveThemeMode(selectedMode)
                    context.saveUiStyle(selectedStyle)
                    context.saveFontScale(selectedFontScale)
                    context.saveFontWeight(selectedFontWeight)
                    context.saveCloudSyncEnabled(selectedCloudSync)
                    context.saveWhisperSubtitles(selectedAiSubs)
                    context.saveOnboardingDone(true)
                    onComplete()
                }
            }
            else -> return false
        }
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(bg, bg.copy(alpha = 0.92f), bg)))
            .focusRequester(outerFocus)
            .onFocusChanged { }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleKey(ev.key)
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text       = "Welcome to nexStream",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = textPrimary,
                    textAlign  = TextAlign.Center,
                )
                Text(
                    text      = "Choose how you'd like the app to look and feel",
                    fontSize  = 12.sp,
                    color     = textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Appearance ────────────────────────────────────────────────────
            OnboardingSection(title = "Appearance", accent = accent) {
                OnboardingCard(
                    icon        = Icons.Default.DarkMode,
                    title       = "Dark",
                    description = "Easy on the eyes, great for evening viewing",
                    selected    = selectedMode == ThemeMode.DARK,
                    focused     = focusedIndex == FOCUS_DARK,
                    accent      = accent,
                    surface     = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    modifier    = Modifier.weight(1f),
                    onClick     = { focusedIndex = FOCUS_DARK; selectedMode = ThemeMode.DARK },
                )
                Spacer(Modifier.width(16.dp))
                OnboardingCard(
                    icon        = Icons.Default.LightMode,
                    title       = "Light",
                    description = "Bright and crisp for well-lit rooms",
                    selected    = selectedMode == ThemeMode.LIGHT,
                    focused     = focusedIndex == FOCUS_LIGHT,
                    accent      = accent,
                    surface     = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    modifier    = Modifier.weight(1f),
                    onClick     = { focusedIndex = FOCUS_LIGHT; selectedMode = ThemeMode.LIGHT },
                )
            }

            // ── Interface ─────────────────────────────────────────────────────
            OnboardingSection(title = "Interface", accent = accent) {
                OnboardingCard(
                    icon        = Icons.Default.AutoAwesome,
                    title       = "Modern",
                    description = "Cinematic layouts with full-bleed artwork",
                    selected    = selectedStyle == UiStyle.MODERN,
                    focused     = focusedIndex == FOCUS_MODERN,
                    accent      = accent,
                    surface     = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    modifier    = Modifier.weight(1f),
                    onClick     = { focusedIndex = FOCUS_MODERN; selectedStyle = UiStyle.MODERN },
                )
                Spacer(Modifier.width(16.dp))
                OnboardingCard(
                    icon        = Icons.Default.ViewList,
                    title       = "Classic",
                    description = "Clean grid layout with familiar navigation",
                    selected    = selectedStyle == UiStyle.CLASSIC,
                    focused     = focusedIndex == FOCUS_CLASSIC,
                    accent      = accent,
                    surface     = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    modifier    = Modifier.weight(1f),
                    onClick     = { focusedIndex = FOCUS_CLASSIC; selectedStyle = UiStyle.CLASSIC },
                )
            }

            // ── Text ──────────────────────────────────────────────────────────
            OnboardingSectionColumn(title = "Text", accent = accent) {
                OnboardingOptionRow(
                    label   = "Text Size",
                    options = fontSizeOptions.map { it.second },
                    selectedIdx = fontSizeOptions.indexOfFirst { it.first == selectedFontScale }.coerceAtLeast(0),
                    focused = focusedIndex == FOCUS_TEXT_SIZE,
                    accent  = accent,
                    surface = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        focusedIndex = FOCUS_TEXT_SIZE
                        val idx  = fontSizeOptions.indexOfFirst { it.first == selectedFontScale }
                        val next = (idx + 1) % fontSizeOptions.size
                        selectedFontScale = fontSizeOptions[next].first
                    },
                )
                Spacer(Modifier.height(8.dp))
                OnboardingOptionRow(
                    label   = "Text Weight",
                    options = fontWeightOptions.map { it.second },
                    selectedIdx = fontWeightOptions.indexOfFirst { it.first == selectedFontWeight }.coerceAtLeast(0),
                    focused = focusedIndex == FOCUS_TEXT_WEIGHT,
                    accent  = accent,
                    surface = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = {
                        focusedIndex = FOCUS_TEXT_WEIGHT
                        val idx  = fontWeightOptions.indexOfFirst { it.first == selectedFontWeight }
                        val next = (idx + 1) % fontWeightOptions.size
                        selectedFontWeight = fontWeightOptions[next].first
                    },
                )
            }

            // ── Features (annual/lifetime only) ───────────────────────────────
            if (showCloudSync) {
                OnboardingSectionColumn(title = "Features", accent = accent) {
                    OnboardingToggleRow(
                        title         = "Cloud Sync",
                        description   = "Turn cloud sync on for cross-device sync and customisation sync. Keep off for on device only.",
                        checked       = selectedCloudSync,
                        focused       = focusedIndex == FOCUS_CLOUD_SYNC,
                        accent        = accent,
                        surface       = surface,
                        textPrimary   = textPrimary,
                        textSecondary = textSecondary,
                        onToggle      = { focusedIndex = FOCUS_CLOUD_SYNC; selectedCloudSync = !selectedCloudSync },
                    )
                }
            }

            // ── Get Started button ────────────────────────────────────────────
            val btnFocused = focusedIndex == FOCUS_GETSTARTED
            val btnScale by animateFloatAsState(if (btnFocused) 1.05f else 1.0f, label = "btnScale")
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                    .clip(RoundedCornerShape(50.dp))
                    .clickable {
                        focusedIndex = FOCUS_GETSTARTED
                        scope.launch {
                            context.saveThemeMode(selectedMode)
                            context.saveUiStyle(selectedStyle)
                            context.saveFontScale(selectedFontScale)
                            context.saveFontWeight(selectedFontWeight)
                            context.saveCloudSyncEnabled(selectedCloudSync)
                            context.saveWhisperSubtitles(selectedAiSubs)
                            context.saveOnboardingDone(true)
                            onComplete()
                        }
                    }
                    .background(if (btnFocused) accent else surface)
                    .border(
                        width = 2.dp,
                        color = if (btnFocused) accent else accent.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(50.dp),
                    )
                    .padding(horizontal = 56.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "Get Started",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (btnFocused) bg else textPrimary,
                )
            }

            // ── Hint ──────────────────────────────────────────────────────────
            Text(
                text      = "You can change these at any time in Settings → Appearance",
                fontSize  = 11.sp,
                color     = textSecondary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section wrappers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingSection(
    title:   String,
    accent:  Color,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(title, accent)
        Row(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun OnboardingSectionColumn(
    title:   String,
    accent:  Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(title, accent)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionLabel(title: String, accent: Color) {
    Text(
        text          = title.uppercase(),
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Medium,
        color         = accent,
        letterSpacing = 1.5.sp,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Horizontal option-selector row (text size / text weight)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingOptionRow(
    label:        String,
    options:      List<String>,
    selectedIdx:  Int,
    focused:      Boolean,
    accent:       Color,
    surface:      Color,
    textPrimary:  Color,
    textSecondary: Color,
    onClick:      () -> Unit = {},
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (focused) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (focused) accent else accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Label + chips stay left-aligned together; arrows go to far right
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (focused) textPrimary else textPrimary.copy(alpha = 0.75f),
                modifier   = Modifier.width(100.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { idx, opt ->
                    val isSelected = idx == selectedIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) accent else accent.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text       = opt,
                            fontSize   = 11.sp,
                            color      = if (isSelected) Color.White else textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        if (focused) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("◀", fontSize = 10.sp, color = accent.copy(alpha = 0.7f))
                Text("▶", fontSize = 10.sp, color = accent.copy(alpha = 0.7f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toggle row (cloud sync / AI subtitles)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingToggleRow(
    title:         String,
    description:   String,
    checked:       Boolean,
    focused:       Boolean,
    accent:        Color,
    surface:       Color,
    textPrimary:   Color,
    textSecondary: Color,
    onToggle:      () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .background(if (focused) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (focused) accent else accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text       = title,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (focused) textPrimary else textPrimary.copy(alpha = 0.75f),
            )
            Text(
                text      = description,
                fontSize  = 11.sp,
                color     = textSecondary.copy(alpha = 0.75f),
                lineHeight = 15.sp,
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = null,
            modifier        = Modifier.focusProperties { canFocus = false },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = accent,
                uncheckedThumbColor = textSecondary,
                uncheckedTrackColor = textSecondary.copy(alpha = 0.25f),
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Choice card (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingCard(
    icon:         ImageVector,
    title:        String,
    description:  String,
    selected:     Boolean,
    focused:      Boolean,
    accent:       Color,
    surface:      Color,
    textPrimary:  Color,
    textSecondary: Color,
    modifier:     Modifier = Modifier,
    onClick:      () -> Unit = {},
) {
    val scale by animateFloatAsState(if (focused) 1.04f else 1.0f, label = "cardScale")

    val bgColor = when {
        selected -> accent.copy(alpha = 0.14f)
        focused  -> surface
        else     -> surface.copy(alpha = 0.55f)
    }
    val borderColor = when {
        selected && focused -> accent
        selected            -> accent.copy(alpha = 0.65f)
        focused             -> accent.copy(alpha = 0.80f)
        else                -> Color.Transparent
    }
    val iconTint = if (selected || focused) accent else textSecondary

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(26.dp),
                )
                if (selected) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = accent,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text       = title,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (selected || focused) textPrimary else textPrimary.copy(alpha = 0.65f),
            )
            Text(
                text     = description,
                fontSize = 12.sp,
                color    = textSecondary,
            )
        }
    }
}
