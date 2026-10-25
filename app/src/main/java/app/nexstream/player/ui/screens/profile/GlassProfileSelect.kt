package app.nexstream.player.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.ui.theme.GlassTokens

@Composable
fun GlassProfileSelect(
    profiles: List<ProfileEntity>,
    activeProfileId: String? = null,
    onProfileSelected: (ProfileEntity) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    if (onDismiss == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(GlassTokens.Background),
            contentAlignment = Alignment.Center,
        ) {
            GlassProfileSelectBody(profiles, activeProfileId, onProfileSelected, null)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(GlassTokens.RadiusPanel))
                    .background(GlassTokens.GlassPanelSolid)
                    .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusPanel))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { /* consume */ }
                    .padding(GlassTokens.PanelPadding),
                contentAlignment = Alignment.Center,
            ) {
                GlassProfileSelectBody(profiles, activeProfileId, onProfileSelected, onDismiss)
            }
        }
    }
}

@Composable
private fun GlassProfileSelectBody(
    profiles: List<ProfileEntity>,
    activeProfileId: String?,
    onProfileSelected: (ProfileEntity) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    var selectedIndex by remember { mutableIntStateOf(
        profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    ) }
    var showPinEntry by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    val avatarFRs = remember(profiles) { profiles.map { FocusRequester() } }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        try { avatarFRs.getOrNull(selectedIndex)?.requestFocus() } catch (_: Exception) {}
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = "Who's watching?",
            color = GlassTokens.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(profiles.size) { i ->
                val profile = profiles[i]
                val isPinSelected = i == selectedIndex && showPinEntry
                var isFocused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) GlassTokens.FocusScaleMax else 1f,
                    label = "avatarScale$i",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(if (i < avatarFRs.size) Modifier.focusRequester(avatarFRs[i]) else Modifier)
                            .onFocusChanged { fs ->
                                isFocused = fs.isFocused
                                if (fs.isFocused && i != selectedIndex) {
                                    selectedIndex = i
                                    showPinEntry = false
                                    pin = ""
                                    pinError = false
                                } else if (fs.isFocused) {
                                    selectedIndex = i
                                }
                            }
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                        if (profile.pinHash != null) {
                                            showPinEntry = true; pin = ""; pinError = false
                                        } else {
                                            onProfileSelected(profile)
                                        }
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        try { avatarFRs[(i - 1).coerceAtLeast(0)].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        try { avatarFRs[(i + 1).coerceAtMost(profiles.lastIndex)].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .focusable()
                            .clickable {
                                if (i != selectedIndex) {
                                    selectedIndex = i; showPinEntry = false; pin = ""; pinError = false
                                }
                                if (profile.pinHash != null) {
                                    showPinEntry = true; pin = ""; pinError = false
                                } else {
                                    onProfileSelected(profile)
                                }
                            }
                            .clip(CircleShape)
                            .background(GlassTokens.GlassSmallSolid)
                            .then(
                                if (isFocused || isPinSelected)
                                    Modifier.border(GlassTokens.FocusOutlineWidth, GlassTokens.AccentDefault, CircleShape)
                                else
                                    Modifier.border(1.dp, GlassTokens.GlassBorder, CircleShape)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = profile.emoji, fontSize = 38.sp)
                    }
                    Text(
                        text = profile.name,
                        color = if (isFocused || isPinSelected) GlassTokens.TextPrimary else GlassTokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isFocused || isPinSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }

        // Inline PIN keypad shown below selected avatar when it has a PIN
        if (showPinEntry) {
            val selectedProfile = profiles.getOrNull(selectedIndex)
            if (selectedProfile != null) {
                Spacer(Modifier.height(28.dp))
                GlassPinKeypad(
                    pin = pin,
                    pinError = pinError,
                    onDigit = { d ->
                        if (pin.length < 4) {
                            pin += d
                            if (pin.length == 4) {
                                if (hashPin(pin) == selectedProfile.pinHash) {
                                    showPinEntry = false
                                    onProfileSelected(selectedProfile)
                                } else {
                                    pinError = true
                                    pin = ""
                                }
                            }
                        }
                    },
                    onDelete = { if (pin.isNotEmpty()) { pin = pin.dropLast(1); pinError = false } },
                    onCancel = { showPinEntry = false; pin = ""; pinError = false },
                )
            }
        }

        if (onDismiss != null && !showPinEntry) {
            Spacer(Modifier.height(24.dp))
            var cancelFocused by remember { mutableStateOf(false) }
            val cancelFR = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .defaultMinSize(minWidth = 100.dp)
                    .focusRequester(cancelFR)
                    .onFocusChanged { cancelFocused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                            onDismiss(); true
                        } else false
                    }
                    .focusable()
                    .clickable { onDismiss() }
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(if (cancelFocused) GlassTokens.GlassSmallSolid else Color.Transparent)
                    .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusPill))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    color = GlassTokens.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GlassPinKeypad(
    pin: String,
    pinError: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val digits = listOf("1","2","3","4","5","6","7","8","9","0")
    val digitFRs = remember { digits.map { FocusRequester() } }
    val deleteFR = remember { FocusRequester() }
    val cancelFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { cancelFR.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(GlassTokens.RadiusCard))
            .background(GlassTokens.GlassPanelSolid)
            .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(GlassTokens.RadiusCard))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Enter PIN",
            color = GlassTokens.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        // 4 PIN dot indicators
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) GlassTokens.AccentDefault else Color.Transparent)
                        .border(1.5.dp, if (i < pin.length) GlassTokens.AccentDefault else GlassTokens.GlassBorder, CircleShape),
                )
            }
        }

        if (pinError) {
            Text(
                text = "Incorrect PIN",
                color = GlassTokens.AccentRed,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        // Row 1: 1–5
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..4).forEach { idx ->
                GlassPinKey(
                    label = digits[idx],
                    focusRequester = digitFRs[idx],
                    onKeyEvent = { e ->
                        if (e.type != KeyEventType.KeyDown) return@GlassPinKey false
                        when (e.key) {
                            Key.DirectionRight -> { try { digitFRs[(idx + 1).coerceAtMost(4)].requestFocus() } catch (_: Exception) {}; true }
                            Key.DirectionLeft  -> { try { digitFRs[(idx - 1).coerceAtLeast(0)].requestFocus() } catch (_: Exception) {}; true }
                            Key.DirectionDown  -> { try { digitFRs[idx + 5].requestFocus() } catch (_: Exception) {}; true }
                            else -> false
                        }
                    },
                    onClick = { onDigit(digits[idx]) },
                )
            }
        }

        // Row 2: 6–0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (5..9).forEach { idx ->
                GlassPinKey(
                    label = digits[idx],
                    focusRequester = digitFRs[idx],
                    onKeyEvent = { e ->
                        if (e.type != KeyEventType.KeyDown) return@GlassPinKey false
                        when (e.key) {
                            Key.DirectionRight -> { try { digitFRs[(idx + 1).coerceAtMost(9)].requestFocus() } catch (_: Exception) {}; true }
                            Key.DirectionLeft  -> { try { digitFRs[(idx - 1).coerceAtLeast(5)].requestFocus() } catch (_: Exception) {}; true }
                            Key.DirectionUp    -> { try { digitFRs[idx - 5].requestFocus() } catch (_: Exception) {}; true }
                            Key.DirectionDown  -> { try { deleteFR.requestFocus() } catch (_: Exception) {}; true }
                            else -> false
                        }
                    },
                    onClick = { onDigit(digits[idx]) },
                )
            }
        }

        // Row 3: Delete + Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassPinActionKey(
                label = "Delete",
                focusRequester = deleteFR,
                modifier = Modifier.weight(1f),
                onKeyEvent = { e ->
                    if (e.type != KeyEventType.KeyDown) return@GlassPinActionKey false
                    when (e.key) {
                        Key.DirectionUp    -> { try { digitFRs[7].requestFocus() } catch (_: Exception) {}; true }
                        Key.DirectionRight -> { try { cancelFR.requestFocus() } catch (_: Exception) {}; true }
                        else -> false
                    }
                },
                onClick = onDelete,
            )
            GlassPinActionKey(
                label = "Cancel",
                focusRequester = cancelFR,
                modifier = Modifier.weight(1f),
                onKeyEvent = { e ->
                    if (e.type != KeyEventType.KeyDown) return@GlassPinActionKey false
                    when (e.key) {
                        Key.DirectionUp   -> { try { digitFRs[8].requestFocus() } catch (_: Exception) {}; true }
                        Key.DirectionLeft -> { try { deleteFR.requestFocus() } catch (_: Exception) {}; true }
                        else -> false
                    }
                },
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun GlassPinKey(
    label: String,
    focusRequester: FocusRequester,
    onKeyEvent: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) GlassTokens.FocusScaleMax else 1f,
        label = "pinKeyScale",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else onKeyEvent(e)
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) GlassTokens.AccentDefault else GlassTokens.GlassSmallSolid)
            .border(1.dp, if (isFocused) GlassTokens.AccentDefault else GlassTokens.GlassBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isFocused) GlassTokens.AccentOnDark else GlassTokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GlassPinActionKey(
    label: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onKeyEvent: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)) {
                    onClick(); true
                } else onKeyEvent(e)
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) GlassTokens.GlassSmallSolid else Color.Transparent)
            .border(1.dp, GlassTokens.GlassBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = GlassTokens.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
