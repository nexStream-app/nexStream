package app.nexstream.player.ui.screens.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.nexstream.player.ui.theme.AspectRatio
import app.nexstream.player.ui.theme.AspectRatioType
import app.nexstream.player.ui.theme.getAspectRatioFlow
import app.nexstream.player.ui.theme.saveAspectRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight

// Use applicationContext so the same DataStore instance is always used,
// regardless of which composable or which Activity context calls these functions.
private val Context.themePrefsDs: DataStore<Preferences> by preferencesDataStore("theme_mode_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

enum class ThemeMode(val label: String, val icon: ImageVector) {
    SYSTEM("System", Icons.Default.SettingsBrightness),
    DARK("Dark", Icons.Default.DarkMode),
    LIGHT("Light", Icons.Default.LightMode),
}

fun Context.getThemeModeFlow(): Flow<ThemeMode> =
    applicationContext.themePrefsDs.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE_KEY] } ?: ThemeMode.SYSTEM
    }

suspend fun Context.saveThemeMode(mode: ThemeMode) {
    applicationContext.themePrefsDs.edit { it[THEME_MODE_KEY] = mode.name }
}

@Composable
fun AppearanceScreen(
    firstItemFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeMode by context.getThemeModeFlow().collectAsState(initial = ThemeMode.SYSTEM)
    val tvAspectRatio by context.getAspectRatioFlow(AspectRatioType.TV).collectAsState(initial = AspectRatio.FILL)
    val movieAspectRatio by context.getAspectRatioFlow(AspectRatioType.MOVIE).collectAsState(initial = AspectRatio.FIT)
    val seriesAspectRatio by context.getAspectRatioFlow(AspectRatioType.SERIES).collectAsState(initial = AspectRatio.FIT)

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(firstItemFocusRequester) {
        if (firstItemFocusRequester != null) {
            kotlinx.coroutines.delay(100)
            try { firstFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp))

        // ── Theme mode ──────────────────────────────────────────
        Text("Theme", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Your app theme is provided by your service. Choose dark, light or follow the system setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeMode.entries.forEachIndexed { idx, mode ->
                val fr = if (idx == 0) firstFocus else remember { FocusRequester() }
                ThemeModeChip(
                    mode           = mode,
                    isSelected     = themeMode == mode,
                    focusRequester = fr,
                    onClick        = { scope.launch { context.saveThemeMode(mode) } },
                    modifier       = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Player aspect ratio ─────────────────────────────────
        Text("Player", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Default aspect ratio per content type. You can still change it manually during playback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        AspectRatioSection(label = "TV Channels", selected = tvAspectRatio,
            onSelect = { scope.launch { context.saveAspectRatio(AspectRatioType.TV, it) } })
        AspectRatioSection(label = "Movies", selected = movieAspectRatio,
            onSelect = { scope.launch { context.saveAspectRatio(AspectRatioType.MOVIE, it) } })
        AspectRatioSection(label = "Series", selected = seriesAspectRatio,
            onSelect = { scope.launch { context.saveAspectRatio(AspectRatioType.SERIES, it) } })
    }
}

@Composable
private fun ThemeModeChip(
    mode: ThemeMode,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused  -> MaterialTheme.colorScheme.outline
        else       -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isFocused  -> MaterialTheme.colorScheme.surfaceVariant
        else       -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (
                            e.key == Key.Enter ||
                                    e.key == Key.NumPadEnter ||
                                    e.key == Key.DirectionCenter
                            )) { onClick(); true } else false
            }
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.label,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AspectRatioSection(label: String, selected: AspectRatio, onSelect: (AspectRatio) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                            e.key == Key.Enter ||
                                    e.key == Key.NumPadEnter ||
                                    e.key == Key.DirectionCenter
                            )) { onClick(); true } else false
            }
            .clickable(onClick = onClick)
            .then(if (isSelected || isFocused) Modifier.border(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            ) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isFocused  -> MaterialTheme.colorScheme.surfaceVariant
                else       -> MaterialTheme.colorScheme.surface
            })
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