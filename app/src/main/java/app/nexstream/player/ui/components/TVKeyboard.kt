package app.nexstream.player.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val KbKey.weight: Float get() = when (this) {
    KbKey.Space  -> 4f
    KbKey.Done   -> 2f
    KbKey.Delete -> 1.5f
    KbKey.Clear  -> 1.5f
    KbKey.Shift  -> 1.5f
    else         -> 1f
}

private val ROWS: List<List<KbKey>> = listOf(
    "1234567890".map { KbKey.Sym(it.toString()) },
    listOf("-","_",".",":","/","@","!","?","#","+").map { KbKey.Sym(it) },
    "QWERTYUIOP".map { KbKey.Char(it.lowercaseChar().toString(), it.toString()) },
    "ASDFGHJKL".map  { KbKey.Char(it.lowercaseChar().toString(), it.toString()) },
    "ZXCVBNM".map    { KbKey.Char(it.lowercaseChar().toString(), it.toString()) },
    listOf(KbKey.Shift, KbKey.Space, KbKey.Clear, KbKey.Delete, KbKey.Done)
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
    onNavigateUp:     (() -> Unit)? = null,   // called when user D-pads up from keyboard
    modifier:         Modifier     = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Dim scrim
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200))
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    // tap outside to dismiss
                    .then(Modifier)
            )
        }

        // Keyboard panel — slides up from bottom
        AnimatedVisibility(
            visible  = visible,
            enter    = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
            exit     = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    // Search bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text  = if (value.isEmpty()) hint else value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (value.isEmpty())
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (value.isNotEmpty()) {
                                Text("│", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    TvKeyboard(
                        value            = value,
                        onValueChange    = onValueChange,
                        onDone           = onDone,
                        onNavigateToResults = onNavigateUp
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
    modifier:            Modifier      = Modifier
) {
    var shifted by remember { mutableStateOf(false) }

    val focusGrid = remember {
        ROWS.map { row -> row.map { FocusRequester() } }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { focusGrid[2][0].requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ROWS.forEachIndexed { rowIdx, row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                        onNavigateToResults?.invoke()
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
            .height(38.dp)          // more compact than original 44dp
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
                    KbKey.Space, KbKey.Done, KbKey.Clear -> 10.sp
                    else -> 13.sp
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