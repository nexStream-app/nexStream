package app.nexstream.player.ui.screens.appearance

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.screens.appearance.AppearanceViewModel
import app.nexstream.player.ui.screens.settings.rememberUiStyle
import app.nexstream.player.ui.screens.settings.SettingsSectionContainer
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.ThemeMode
import app.nexstream.player.ui.theme.UiStyle
import app.nexstream.player.ui.screens.settings.SettingsToggle
import app.nexstream.player.ui.theme.getAspectRatioFlow
import app.nexstream.player.ui.theme.getEpgMiniPlayerFlow
import app.nexstream.player.ui.theme.getFontScaleFlow
import app.nexstream.player.ui.theme.getFontWeightFlow
import app.nexstream.player.ui.theme.getKeyboardBoldFlow
import app.nexstream.player.ui.theme.getKeyboardFontScaleFlow
import app.nexstream.player.ui.theme.getThemeModeFlow
import app.nexstream.player.ui.theme.getUiStyleFlow
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isAndroidTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val scope = rememberCoroutineScope()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()

    val themeMode           by context.getThemeModeFlow().collectAsState(initial = ThemeMode.SYSTEM)
    val fontScaleOrNull     by context.getFontScaleFlow().collectAsState(initial = null)
    val fontWeightOrNull    by context.getFontWeightFlow().collectAsState(initial = null)
    val nsTypo              = nsTheme.typography
    val fontScale           = fontScaleOrNull  ?: nsTypo.scale
    val fontWeight          = fontWeightOrNull ?: nsTypo.weight
    val tvAspectRatio      by context.getAspectRatioFlow(AspectRatioType.TV).collectAsState(initial = AspectRatio.FILL)
    val movieAspectRatio by context.getAspectRatioFlow(AspectRatioType.MOVIE).collectAsState(initial = AspectRatio.FIT)
    val seriesAspectRatio by context.getAspectRatioFlow(AspectRatioType.SERIES).collectAsState(initial = AspectRatio.FIT)
    val epgMiniPlayer      by context.getEpgMiniPlayerFlow().collectAsState(initial = true)
    val keyboardFontScale  by context.getKeyboardFontScaleFlow().collectAsState(initial = 1.4f)
    val keyboardBold       by context.getKeyboardBoldFlow().collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Theme & Display section ─────────────────────────────────────
            SettingsSectionContainer(
                title = "Theme & Display",
                icon = Icons.Default.DarkMode,
                uiStyle = uiStyle
            ) {
                Column(modifier = Modifier.padding(
                    horizontal = if (uiStyle == UiStyle.MODERN) 0.dp else 0.dp,
                    vertical = 4.dp
                )) {
                    // Dark Mode toggle
                    var themeFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (themeFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .then(
                                if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester)
                                else Modifier
                            )
                            .onFocusChanged { themeFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown &&
                                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                    scope.launch {
                                        viewModel.saveThemeMode(if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
                                    }
                                    true
                                } else false
                            }
                            .clickable {
                                scope.launch {
                                    viewModel.saveThemeMode(if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Dark Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                when (themeMode) {
                                    ThemeMode.HIGH_CONTRAST -> "App uses a high contrast theme"
                                    ThemeMode.DARK          -> "App uses dark theme"
                                    else                    -> "App uses light theme"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = themeMode == ThemeMode.DARK,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    viewModel.saveThemeMode(if (checked) ThemeMode.DARK else ThemeMode.LIGHT)
                                }
                            },
                            modifier = Modifier.focusProperties { canFocus = false }
                        )
                    }

                    // High Contrast toggle
                    var hcFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (hcFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .onFocusChanged { hcFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown &&
                                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                    scope.launch {
                                        viewModel.saveThemeMode(
                                            if (themeMode == ThemeMode.HIGH_CONTRAST) ThemeMode.DARK else ThemeMode.HIGH_CONTRAST
                                        )
                                    }
                                    true
                                } else false
                            }
                            .clickable {
                                scope.launch {
                                    viewModel.saveThemeMode(
                                        if (themeMode == ThemeMode.HIGH_CONTRAST) ThemeMode.DARK else ThemeMode.HIGH_CONTRAST
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "High Contrast",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (themeMode == ThemeMode.HIGH_CONTRAST) "Black background, white text, yellow accents"
                                else "Use standard colour theme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = themeMode == ThemeMode.HIGH_CONTRAST,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    viewModel.saveThemeMode(if (checked) ThemeMode.HIGH_CONTRAST else ThemeMode.DARK)
                                }
                            },
                            modifier = Modifier.focusProperties { canFocus = false }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    // Font Size dropdown
                    val fontOptions = remember {
                        listOf(
                            0.85f  to "Small",
                            1.0f   to "Normal",
                            1.15f  to "Large",
                            1.3f   to "Extra Large"
                        )
                    }
                    val selectedLabel = fontOptions.firstOrNull { it.first == fontScale }?.second ?: "Normal"
                    var fontDropExpanded by remember { mutableStateOf(false) }
                    var fontTriggerFocused by remember { mutableStateOf(false) }
                    val fontOptionFRs = remember { List(fontOptions.size) { FocusRequester() } }
                    val fontTriggerFR = remember { FocusRequester() }
                    var prevFontExpanded by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(fontDropExpanded) {
                        val prev = prevFontExpanded
                        prevFontExpanded = fontDropExpanded
                        if (prev == null) return@LaunchedEffect
                        kotlinx.coroutines.delay(50)
                        try {
                            if (fontDropExpanded) {
                                val idx = fontOptions.indexOfFirst { it.first == fontScale }.coerceAtLeast(0)
                                fontOptionFRs.getOrElse(idx) { fontOptionFRs.first() }.requestFocus()
                            } else {
                                fontTriggerFR.requestFocus()
                            }
                        } catch (_: Exception) {}
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (fontTriggerFocused || fontDropExpanded)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fontTriggerFR)
                                .onFocusChanged { fontTriggerFocused = it.isFocused }
                                .onKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown &&
                                        (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                        fontDropExpanded = !fontDropExpanded; true
                                    } else false
                                }
                                .clickable { fontDropExpanded = !fontDropExpanded }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Font Size",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    selectedLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector        = if (fontDropExpanded) Icons.Default.KeyboardArrowUp
                                                     else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (fontDropExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            fontOptions.forEachIndexed { optIdx, (scale, label) ->
                                val isCurrentScale = scale == fontScale
                                var optFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(fontOptionFRs[optIdx])
                                        .background(
                                            when {
                                                isCurrentScale -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                optFocused     -> MaterialTheme.colorScheme.surfaceVariant
                                                else           -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .onFocusChanged { optFocused = it.isFocused }
                                        .onKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown &&
                                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                                scope.launch { viewModel.saveFontScale(scale) }
                                                fontDropExpanded = false; true
                                            } else false
                                        }
                                        .clickable {
                                            scope.launch { viewModel.saveFontScale(scale) }
                                            fontDropExpanded = false
                                        }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        style  = MaterialTheme.typography.bodyMedium,
                                        color  = if (isCurrentScale) MaterialTheme.colorScheme.onPrimaryContainer
                                                 else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrentScale) {
                                        Icon(
                                            imageVector        = Icons.Default.Check,
                                            contentDescription = null,
                                            tint               = MaterialTheme.colorScheme.primary,
                                            modifier           = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    // Font Weight dropdown
                    val weightOptions = remember {
                        listOf(
                            "normal"   to "Normal",
                            "semibold" to "Semi Bold",
                            "bold"     to "Bold"
                        )
                    }
                    val weightLabel = weightOptions.firstOrNull { it.first == fontWeight }?.second ?: "Normal"
                    var weightDropExpanded by remember { mutableStateOf(false) }
                    var weightTriggerFocused by remember { mutableStateOf(false) }
                    val weightOptionFRs = remember { List(weightOptions.size) { FocusRequester() } }
                    val weightTriggerFR = remember { FocusRequester() }
                    var prevWeightExpanded by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(weightDropExpanded) {
                        val prev = prevWeightExpanded
                        prevWeightExpanded = weightDropExpanded
                        if (prev == null) return@LaunchedEffect
                        kotlinx.coroutines.delay(50)
                        try {
                            if (weightDropExpanded) {
                                val idx = weightOptions.indexOfFirst { it.first == fontWeight }.coerceAtLeast(0)
                                weightOptionFRs.getOrElse(idx) { weightOptionFRs.first() }.requestFocus()
                            } else {
                                weightTriggerFR.requestFocus()
                            }
                        } catch (_: Exception) {}
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (weightTriggerFocused || weightDropExpanded)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(weightTriggerFR)
                                .onFocusChanged { weightTriggerFocused = it.isFocused }
                                .onKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown &&
                                        (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                        weightDropExpanded = !weightDropExpanded; true
                                    } else false
                                }
                                .clickable { weightDropExpanded = !weightDropExpanded }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Font Weight",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    weightLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector        = if (weightDropExpanded) Icons.Default.KeyboardArrowUp
                                                     else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (weightDropExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            weightOptions.forEachIndexed { optIdx, (value, label) ->
                                val isCurrent = value == fontWeight
                                var optFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(weightOptionFRs[optIdx])
                                        .background(
                                            when {
                                                isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                optFocused -> MaterialTheme.colorScheme.surfaceVariant
                                                else       -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .onFocusChanged { optFocused = it.isFocused }
                                        .onKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown &&
                                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                                scope.launch { viewModel.saveFontWeight(value) }
                                                weightDropExpanded = false; true
                                            } else false
                                        }
                                        .clickable {
                                            scope.launch { viewModel.saveFontWeight(value) }
                                            weightDropExpanded = false
                                        }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrent) {
                                        Icon(
                                            imageVector        = Icons.Default.Check,
                                            contentDescription = null,
                                            tint               = MaterialTheme.colorScheme.primary,
                                            modifier           = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Keyboard Font Size dropdown
                    val kbFontOptions = remember {
                        listOf(
                            0.85f to "Small",
                            1.0f  to "Normal",
                            1.2f  to "Large",
                            1.4f  to "Extra Large"
                        )
                    }
                    val kbFontLabel = kbFontOptions.firstOrNull { it.first == keyboardFontScale }?.second ?: "Normal"
                    var kbFontDropExpanded by remember { mutableStateOf(false) }
                    var kbFontTriggerFocused by remember { mutableStateOf(false) }
                    val kbFontOptionFRs = remember { List(kbFontOptions.size) { FocusRequester() } }
                    val kbFontTriggerFR = remember { FocusRequester() }
                    var prevKbFontExpanded by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(kbFontDropExpanded) {
                        val prev = prevKbFontExpanded
                        prevKbFontExpanded = kbFontDropExpanded
                        if (prev == null) return@LaunchedEffect
                        kotlinx.coroutines.delay(50)
                        try {
                            if (kbFontDropExpanded) {
                                val idx = kbFontOptions.indexOfFirst { it.first == keyboardFontScale }.coerceAtLeast(0)
                                kbFontOptionFRs.getOrElse(idx) { kbFontOptionFRs.first() }.requestFocus()
                            } else {
                                kbFontTriggerFR.requestFocus()
                            }
                        } catch (_: Exception) {}
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (kbFontTriggerFocused || kbFontDropExpanded)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(kbFontTriggerFR)
                                .onFocusChanged { kbFontTriggerFocused = it.isFocused }
                                .onKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown &&
                                        (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                        kbFontDropExpanded = !kbFontDropExpanded; true
                                    } else false
                                }
                                .clickable { kbFontDropExpanded = !kbFontDropExpanded }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Keyboard Letter Size",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    kbFontLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector        = if (kbFontDropExpanded) Icons.Default.KeyboardArrowUp
                                                     else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (kbFontDropExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            kbFontOptions.forEachIndexed { optIdx, (scale, label) ->
                                val isCurrent = scale == keyboardFontScale
                                var optFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(kbFontOptionFRs[optIdx])
                                        .background(
                                            when {
                                                isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                optFocused -> MaterialTheme.colorScheme.surfaceVariant
                                                else       -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .onFocusChanged { optFocused = it.isFocused }
                                        .onKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown &&
                                                (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                                                scope.launch { viewModel.saveKeyboardFontScale(scale) }
                                                kbFontDropExpanded = false; true
                                            } else false
                                        }
                                        .clickable {
                                            scope.launch { viewModel.saveKeyboardFontScale(scale) }
                                            kbFontDropExpanded = false
                                        }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrent) {
                                        Icon(
                                            imageVector        = Icons.Default.Check,
                                            contentDescription = null,
                                            tint               = MaterialTheme.colorScheme.primary,
                                            modifier           = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingsToggle(
                        label       = "Bold Keyboard Letters",
                        description = "Always show keyboard keys in bold weight",
                        checked     = keyboardBold,
                        uiStyle     = uiStyle,
                        onToggle    = { scope.launch { viewModel.saveKeyboardBold(!keyboardBold) } }
                    )
                }
            }

            // ── App Layout section ───────────────────────────────────────────
            SettingsSectionContainer(
                title = "App Layout",
                icon = Icons.Default.Dashboard,
                uiStyle = uiStyle
            ) {
                Column(modifier = Modifier.padding(
                    horizontal = if (uiStyle == UiStyle.MODERN) 16.dp else 0.dp,
                    vertical = 8.dp
                )) {
                    SettingsToggle(
                        label = "Mini Player & Programme Info",
                        description = "Shows a mini video player and programme description strip at the top of the TV Guide. When disabled, the guide fills the full screen.",
                        checked = epgMiniPlayer,
                        uiStyle = uiStyle,
                        onToggle = { scope.launch { viewModel.saveEpgMiniPlayer(!epgMiniPlayer) } }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    val currentUiStyle by context.getUiStyleFlow().collectAsState(initial = UiStyle.CLASSIC)
                    SettingsToggle(
                        label       = "Modern Layout",
                        description = "Full-screen artwork, carousels and rich detail pages. Disable for the classic grid layout.",
                        checked     = currentUiStyle == UiStyle.MODERN,
                        uiStyle     = uiStyle,
                        onToggle    = {
                            scope.launch {
                                viewModel.saveUiStyle(if (currentUiStyle == UiStyle.MODERN) UiStyle.CLASSIC else UiStyle.MODERN)
                            }
                        }
                    )
                }
            }

            // ── Player Defaults (mobile only) ────────────────────────────────
            if (!isAndroidTV) {
                SettingsSectionContainer(
                    title = "Player Defaults",
                    icon = Icons.Default.AspectRatio,
                    uiStyle = uiStyle
                ) {
                    Column(modifier = Modifier.padding(
                        horizontal = if (uiStyle == UiStyle.MODERN) 16.dp else 0.dp,
                        vertical = 8.dp
                    )) {
                        Text(
                            "Default aspect ratio per content type. You can still change it manually during playback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        AspectRatioSection(
                            label    = "TV Channels",
                            selected = tvAspectRatio,
                            onSelect = { scope.launch { viewModel.saveAspectRatio(AspectRatioType.TV, it) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        AspectRatioSection(
                            label    = "Movies",
                            selected = movieAspectRatio,
                            onSelect = { scope.launch { viewModel.saveAspectRatio(AspectRatioType.MOVIE, it) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        AspectRatioSection(
                            label    = "Series",
                            selected = seriesAspectRatio,
                            onSelect = { scope.launch { viewModel.saveAspectRatio(AspectRatioType.SERIES, it) } }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AspectRatioSection(label: String, selected: AspectRatio, onSelect: (AspectRatio) -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { ratio ->
                AspectRatioChip(
                    ratio      = ratio,
                    isSelected = selected == ratio,
                    onClick    = { onSelect(ratio) },
                    modifier   = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AspectRatioChip(
    ratio: AspectRatio,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (
                    e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                )) { onClick(); true } else false
            }
            .clickable(onClick = onClick)
            .then(
                if (isSelected || isFocused) Modifier.border(
                    2.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isFocused  -> MaterialTheme.colorScheme.surfaceVariant
                    else       -> MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(ratio.icon, style = MaterialTheme.typography.titleMedium)
            Text(
                ratio.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
