package app.nexstream.player.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.getKeyboardFontScaleFlow

// ── Key model ─────────────────────────────────────────────────────────────────

private sealed class KbKey {
    data class Char(val lower: String, val upper: String) : KbKey()
    object Space  : KbKey()
    object Delete : KbKey()
    object Done   : KbKey()
    object Shift  : KbKey()
    object Clear  : KbKey()
    data class Sym(val value: String) : KbKey()
}

private val KbKey.weight: Float get() = 1f

private val ROWS: List<List<KbKey>> = listOf(
    "1234567890".map { KbKey.Sym(it.toString()) },
    listOf("-","_",".",":","/","@","!","?","#","+").map { KbKey.Sym(it) },
    "QWERTYUIOP".map { KbKey.Char(it.lowercaseChar().toString(), it.toString()) },
    listOf(KbKey.Shift) + "ASDFGHJKL".map { KbKey.Char(it.lowercaseChar().toString(), it.toString()) },
    "ZXCVBNM".map { KbKey.Char(it.lowercaseChar().toString(), it.toString()) } + listOf(KbKey.Space, KbKey.Delete, KbKey.Done),
)

// ── Bottom-sheet wrapper — slides up from bottom like subtitle panel ───────────

@Composable
fun TvKeyboardSheet(
    visible:          Boolean,
    value:            String,
    onValueChange:    (String) -> Unit,
    onDone:           () -> Unit,
    onDismiss:        () -> Unit,
    hint:             String       = "Search…",
    onNavigateUp:     (() -> Unit)? = null,
    modifier:         Modifier     = Modifier
) {
    val focusManager = LocalFocusManager.current
    var micFocused   by remember { mutableStateOf(false) }
    val micFR        = remember { FocusRequester() }

    val context = LocalContext.current
    val hasSpeechRecognition = remember(context) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { onValueChange(it) }
        }
    }

    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { speechLauncher.launch(intent) } catch (_: ActivityNotFoundException) {}
    }

    var clrFocused by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Dim scrim
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200))
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }

        // Keyboard panel — slides up from bottom
        AnimatedVisibility(
            visible  = visible,
            enter    = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
            exit     = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color           = MaterialTheme.colorScheme.surface,
                tonalElevation  = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    // ── Search bar + mic + CLR button ─────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mic button — only shown when speech recognition is available
                        if (hasSpeechRecognition) {
                            val accent = MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        color = if (micFocused) accent else MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (micFocused) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .focusRequester(micFR)
                                    .onFocusChanged { micFocused = it.isFocused }
                                    .clickable { launchSpeech() }
                                    .onKeyEvent { e ->
                                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        when (e.key) {
                                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                                launchSpeech(); true
                                            }
                                            Key.DirectionDown -> {
                                                focusManager.moveFocus(FocusDirection.Down); true
                                            }
                                            else -> false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.Mic,
                                    contentDescription = "Voice search",
                                    tint               = if (micFocused) MaterialTheme.colorScheme.onPrimary
                                                         else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }

                        // CLR button — always present, dims when nothing to clear
                        val clrEnabled = value.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        !clrEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        clrFocused  -> MaterialTheme.colorScheme.primary
                                        else        -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                                .onFocusChanged { clrFocused = it.isFocused }
                                .focusable(enabled = clrEnabled)
                                .clickable(enabled = clrEnabled) { onValueChange("") }
                                .onKeyEvent { e ->
                                    if (!clrEnabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    when (e.key) {
                                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onValueChange(""); true }
                                        else -> false
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = "CLR",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = when {
                                    !clrEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    clrFocused  -> MaterialTheme.colorScheme.onPrimary
                                    else        -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }

                        // Search text preview
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(8.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text     = if (value.isEmpty()) hint else value,
                                    style    = MaterialTheme.typography.bodyLarge,
                                    color    = if (value.isEmpty())
                                                   MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                               else
                                                   MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (value.isNotEmpty()) {
                                    Text(
                                        "│",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }

                    TvKeyboard(
                        value               = value,
                        onValueChange       = onValueChange,
                        onDone              = onDone,
                        onNavigateToResults = onNavigateUp,
                        micFocusRequester   = if (hasSpeechRecognition) micFR else null
                    )
                }
            }
        }
    }
}

// ── Core keyboard ─────────────────────────────────────────────────────────────

@Composable
fun TvKeyboard(
    value:               String,
    onValueChange:       (String) -> Unit,
    onDone:              () -> Unit,
    onNavigateToResults: (() -> Unit)? = null,
    micFocusRequester:   FocusRequester? = null,
    modifier:            Modifier        = Modifier
) {
    var shifted by remember { mutableStateOf(false) }

    val focusGrid = remember {
        ROWS.map { row -> row.map { FocusRequester() } }
    }

    // Guard: ignore key presses for 500ms after opening so the long-press release
    // that triggered the keyboard doesn't type into the first focused key (e.g. "q").
    var readyToType by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { focusGrid[2][0].requestFocus() } catch (_: Exception) {}
        kotlinx.coroutines.delay(420)
        readyToType = true
    }

    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ROWS.forEachIndexed { rowIdx, row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                row.forEachIndexed { colIdx, key ->
                    KbKeyButton(
                        key            = key,
                        shifted        = shifted,
                        focusRequester = focusGrid[rowIdx][colIdx],
                        modifier       = Modifier.weight(key.weight),
                        onKeyEvent     = { e ->
                            if (e.type == KeyEventType.KeyDown) when (e.key) {
                                Key.DirectionUp -> {
                                    if (rowIdx > 0) {
                                        val col = colIdx.coerceAtMost(focusGrid[rowIdx - 1].lastIndex)
                                        focusGrid[rowIdx - 1][col].requestFocus()
                                    } else {
                                        // Row 0: go to mic button if available, else exit keyboard
                                        try { micFocusRequester?.requestFocus() }
                                        catch (_: Exception) { onNavigateToResults?.invoke() }
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (rowIdx < focusGrid.lastIndex) {
                                        val col = colIdx.coerceAtMost(focusGrid[rowIdx + 1].lastIndex)
                                        focusGrid[rowIdx + 1][col].requestFocus()
                                    } else {
                                        onNavigateToResults?.invoke()
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (colIdx > 0) focusGrid[rowIdx][colIdx - 1].requestFocus()
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (colIdx < focusGrid[rowIdx].lastIndex)
                                        focusGrid[rowIdx][colIdx + 1].requestFocus()
                                    else
                                        onNavigateToResults?.invoke()
                                    true
                                }
                                else -> false
                            } else false
                        },
                        onClick = {
                            if (!readyToType) return@KbKeyButton
                            when (key) {
                                is KbKey.Char -> {
                                    onValueChange(value + if (shifted) key.upper else key.lower)
                                    shifted = false
                                }
                                is KbKey.Sym  -> onValueChange(value + key.value)
                                KbKey.Space   -> onValueChange("$value ")
                                KbKey.Delete  -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                KbKey.Clear   -> onValueChange("")
                                KbKey.Shift   -> shifted = !shifted
                                KbKey.Done    -> onDone()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Key button ────────────────────────────────────────────────────────────────

@Composable
private fun KbKeyButton(
    key:            KbKey,
    shifted:        Boolean,
    focusRequester: FocusRequester,
    modifier:       Modifier = Modifier,
    onKeyEvent:     (KeyEvent) -> Boolean,
    onClick:        () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyFontScale by LocalContext.current.getKeyboardFontScaleFlow().collectAsState(initial = 1.0f)

    val label = when (key) {
        is KbKey.Char -> if (shifted) key.upper else key.lower
        is KbKey.Sym  -> key.value
        KbKey.Space   -> "SPACE"
        KbKey.Delete  -> "⌫"
        KbKey.Done    -> "DONE"
        KbKey.Shift   -> if (shifted) "⇧" else "⇑"
        KbKey.Clear   -> "CLR"
    }

    val isAccent = key == KbKey.Delete || key == KbKey.Done || (key == KbKey.Shift && shifted)

    Surface(
        onClick         = onClick,
        modifier        = modifier
            .height(42.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { onKeyEvent(it) },
        shape           = RoundedCornerShape(5.dp),
        color           = when {
            isFocused && isAccent -> MaterialTheme.colorScheme.primary
            isFocused             -> MaterialTheme.colorScheme.primaryContainer
            isAccent              -> MaterialTheme.colorScheme.secondaryContainer
            else                  -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation  = if (isFocused) 6.dp else 1.dp,
        shadowElevation = if (isFocused) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text       = label,
                fontSize   = when (key) {
                    KbKey.Space, KbKey.Done, KbKey.Clear -> (9 * keyFontScale).sp
                    else -> (12 * keyFontScale).sp
                },
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                color      = when {
                    isFocused && isAccent -> MaterialTheme.colorScheme.onPrimary
                    isFocused             -> MaterialTheme.colorScheme.onPrimaryContainer
                    isAccent              -> MaterialTheme.colorScheme.onSecondaryContainer
                    else                  -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
