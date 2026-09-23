package app.nexstream.player.ui.screens.onboarding

import android.app.Activity
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary
import app.nexstream.player.ui.theme.getOnboardingDoneFlow
import app.nexstream.player.ui.theme.saveAppLanguage
import app.nexstream.player.ui.theme.saveCloudSyncEnabled
import app.nexstream.player.ui.theme.saveOnboardingDone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LANGUAGES = listOf(
    Triple("",   "🌐", "System default"),
    Triple("en", "🇬🇧", "English"),
    Triple("fr", "🇫🇷", "Français"),
    Triple("de", "🇩🇪", "Deutsch"),
    Triple("nl", "🇳🇱", "Nederlands"),
    Triple("sv", "🇸🇪", "Svenska"),
    Triple("it", "🇮🇹", "Italiano"),
    Triple("tr", "🇹🇷", "Türkçe"),
    Triple("pl", "🇵🇱", "Polski"),
    Triple("es", "🇪🇸", "Español"),
    Triple("pt", "🇵🇹", "Português"),
)

private val CLOUD_SYNC_IDX  = LANGUAGES.size       // 11
private val GET_STARTED_IDX = LANGUAGES.size + 1   // 12

@Composable
fun OnboardingStyleScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Guard against landing back here after Activity.recreate() restores nav state:
    // if onboarding is already done, skip straight to Main without showing UI.
    var shouldShow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val done = withContext(Dispatchers.IO) { context.getOnboardingDoneFlow().first() }
        if (done) onComplete() else shouldShow = true
    }
    if (!shouldShow) return

    val accent        = LocalNsAccent.current
    val bg            = LocalNsBackground.current
    val surface       = LocalNsSurface.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    val showCloudSync = remember {
        val prefs = context.getSharedPreferences("nexstream_licence", Context.MODE_PRIVATE)
        val type  = prefs.getString("licence_type", null)?.lowercase()
        type == "annual" || type == "lifetime"
    }

    var selectedLangIdx  by remember { mutableStateOf(0) }
    var selectedCloudSync by remember { mutableStateOf(true) }
    var focusedIndex     by remember { mutableStateOf(0) }

    val outerFocus  = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(120)
        try { outerFocus.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(focusedIndex) {
        if (scrollState.maxValue == 0) return@LaunchedEffect
        val fraction = when {
            focusedIndex <= 2                    -> 0f
            focusedIndex < LANGUAGES.lastIndex   -> focusedIndex.toFloat() / LANGUAGES.size
            focusedIndex >= CLOUD_SYNC_IDX       -> 1f
            else                                 -> 1f
        }
        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
    }

    fun effectiveGetStarted() = if (showCloudSync) GET_STARTED_IDX else CLOUD_SYNC_IDX

    fun handleKey(key: Key): Boolean {
        when (key) {
            Key.DirectionDown -> when {
                focusedIndex < LANGUAGES.lastIndex      -> focusedIndex++
                focusedIndex == LANGUAGES.lastIndex     -> focusedIndex = if (showCloudSync) CLOUD_SYNC_IDX else effectiveGetStarted()
                focusedIndex == CLOUD_SYNC_IDX && showCloudSync -> focusedIndex = GET_STARTED_IDX
                else -> return false
            }
            Key.DirectionUp -> when {
                focusedIndex in 1..LANGUAGES.lastIndex  -> focusedIndex--
                focusedIndex == CLOUD_SYNC_IDX          -> focusedIndex = LANGUAGES.lastIndex
                focusedIndex == effectiveGetStarted()   -> focusedIndex = if (showCloudSync) CLOUD_SYNC_IDX else LANGUAGES.lastIndex
                else -> return false
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when {
                focusedIndex < LANGUAGES.size -> selectedLangIdx = focusedIndex
                focusedIndex == CLOUD_SYNC_IDX && showCloudSync -> selectedCloudSync = !selectedCloudSync
                focusedIndex == effectiveGetStarted() -> scope.launch {
                    val (code, _, _) = LANGUAGES[selectedLangIdx]
                    context.saveAppLanguage(code)
                    if (showCloudSync) context.saveCloudSyncEnabled(selectedCloudSync)
                    context.saveOnboardingDone(true)
                    (context as? Activity)?.recreate() ?: onComplete()
                }
                else -> return false
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
                .widthIn(max = 600.dp)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
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
                    text      = "Select your language to get started",
                    fontSize  = 12.sp,
                    color     = textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Language list ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LANGUAGES.forEachIndexed { idx, (_, flag, name) ->
                    val isFocused  = focusedIndex == idx
                    val isSelected = selectedLangIdx == idx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(when {
                                isFocused  -> accent.copy(alpha = 0.18f)
                                isSelected -> accent.copy(alpha = 0.08f)
                                else       -> Color.Transparent
                            })
                            .border(
                                width = 1.5.dp,
                                color = if (isFocused) accent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { focusedIndex = idx; selectedLangIdx = idx }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = flag, fontSize = 18.sp)
                        Text(
                            text       = name,
                            fontSize   = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isFocused) textPrimary else textPrimary.copy(alpha = 0.85f),
                            modifier   = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Appearance note ───────────────────────────────────────────────
            Text(
                text      = "Appearance — theme, layout and text size — can be changed at any time in Settings → Appearance",
                fontSize  = 11.sp,
                color     = textSecondary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(4.dp))

            // ── Cloud Sync (annual/lifetime only) ─────────────────────────────
            if (showCloudSync) {
                OnboardingToggleRow(
                    title         = "Cloud Sync",
                    description   = "Sync your watch list and settings across devices.",
                    checked       = selectedCloudSync,
                    focused       = focusedIndex == CLOUD_SYNC_IDX,
                    accent        = accent,
                    surface       = surface,
                    textPrimary   = textPrimary,
                    textSecondary = textSecondary,
                    onToggle      = { focusedIndex = CLOUD_SYNC_IDX; selectedCloudSync = !selectedCloudSync },
                )
                Spacer(Modifier.height(4.dp))
            }

            // ── Get Started button ────────────────────────────────────────────
            val btnFocused = focusedIndex == effectiveGetStarted()
            val btnScale by animateFloatAsState(if (btnFocused) 1.05f else 1.0f, label = "btnScale")
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                    .clip(RoundedCornerShape(50.dp))
                    .clickable {
                        focusedIndex = effectiveGetStarted()
                        scope.launch {
                            val (code, _, _) = LANGUAGES[selectedLangIdx]
                            context.saveAppLanguage(code)
                            if (showCloudSync) context.saveCloudSyncEnabled(selectedCloudSync)
                            context.saveOnboardingDone(true)
                            (context as? Activity)?.recreate() ?: onComplete()
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
        }
    }
}

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
                text       = description,
                fontSize   = 11.sp,
                color      = textSecondary.copy(alpha = 0.75f),
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
