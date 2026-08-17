package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.ProfileEntity

@Composable
fun ProfileSwitchDialog(
    profiles: List<ProfileEntity>,
    activeProfileId: String?,
    onProfileSelected: (ProfileEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }

    val focusedIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    val focusRequesters = remember(profiles) { profiles.map { FocusRequester() } }
    val closeFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try { focusRequesters.getOrNull(focusedIndex)?.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.80f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                var closeFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Who's watching?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(closeFR)
                            .onFocusChanged { closeFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (
                                    e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                                )) { onDismiss(); true } else false
                            }
                            .focusable()
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (closeFocused) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (closeFocused) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Profile avatars — centered horizontally
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        items(profiles.size) { index ->
                            val profile = profiles[index]
                            val isActive = profile.id == activeProfileId
                            ProfileAvatar(
                                profile = profile,
                                isSelected = isActive,
                                focusRequester = focusRequesters.getOrNull(index),
                                onClick = {
                                    if (profile.pinHash != null && profile.id != activeProfileId) {
                                        pendingProfile = profile
                                        showPinDialog = true
                                        pinError = false
                                    } else {
                                        onProfileSelected(profile)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog && pendingProfile != null) {
        ThemedPinEntryDialog(
            profile = pendingProfile!!,
            hasError = pinError,
            onDismiss = { showPinDialog = false; pendingProfile = null },
            onConfirm = { pin ->
                val hash = hashPin(pin)
                if (hash == pendingProfile!!.pinHash) {
                    showPinDialog = false
                    onProfileSelected(pendingProfile!!)
                    pendingProfile = null
                } else {
                    pinError = true
                }
            }
        )
    }
}

@Composable
fun ThemedPinEntryDialog(
    profile: ProfileEntity,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val maxPin = 4

    // Focus requesters for digit buttons + backspace + cancel
    // Layout: row1 = 1,2,3,4,5  row2 = 6,7,8,9,0  row3 = backspace, cancel (right-aligned)
    val digits = listOf("1","2","3","4","5","6","7","8","9","0")
    val digitFocusRequesters = remember { digits.map { FocusRequester() } }
    val backspaceFocus = remember { FocusRequester() }
    val cancelFocus    = remember { FocusRequester() }

    // -1 = no digit focused (Cancel/Backspace has focus or nothing yet)
    var focusedDigit by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { cancelFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.60f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(profile.emoji, style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text("Enter PIN", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(profile.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 4 PIN dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(maxPin) { i ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (i < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .then(
                                    if (i < pin.length) Modifier
                                    else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline,
                                        androidx.compose.foundation.shape.CircleShape)
                                )
                        )
                    }
                }

                // Error message
                if (hasError) {
                    Text("Incorrect PIN — try again",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                HorizontalDivider()

                // Row 1: 1 2 3 4 5
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0,1,2,3,4).forEach { idx ->
                        PinDigitButton(
                            digit = digits[idx],
                            focusRequester = digitFocusRequesters[idx],
                            isFocused = focusedDigit == idx,
                            onFocus = { focusedDigit = idx },
                            onKeyEvent = { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.DirectionRight -> {
                                        val next = if (idx < 4) idx + 1 else idx
                                        try { digitFocusRequesters[next].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        val prev = if (idx > 0) idx - 1 else idx
                                        try { digitFocusRequesters[prev].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        try { digitFocusRequesters[idx + 5].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionUp -> false
                                    else -> false
                                } else false
                            },
                            onClick = {
                                if (pin.length < maxPin) {
                                    pin += digits[idx]
                                    if (pin.length == maxPin) onConfirm(pin)
                                }
                            }
                        )
                    }
                }

                // Row 2: 6 7 8 9 0
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5,6,7,8,9).forEach { idx ->
                        PinDigitButton(
                            digit = digits[idx],
                            focusRequester = digitFocusRequesters[idx],
                            isFocused = focusedDigit == idx,
                            onFocus = { focusedDigit = idx },
                            onKeyEvent = { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.DirectionRight -> {
                                        val next = if (idx < 9) idx + 1 else idx
                                        try { digitFocusRequesters[next].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        val prev = if (idx > 5) idx - 1 else idx
                                        try { digitFocusRequesters[prev].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        try { digitFocusRequesters[idx - 5].requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        try { backspaceFocus.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    else -> false
                                } else false
                            },
                            onClick = {
                                if (pin.length < maxPin) {
                                    pin += digits[idx]
                                    if (pin.length == maxPin) onConfirm(pin)
                                }
                            }
                        )
                    }
                }

                // Action row: ← Backspace  |  Cancel
                var backspaceFocused by remember { mutableStateOf(false) }
                var cancelFocused    by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Backspace
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .focusRequester(backspaceFocus)
                            .onFocusChanged { backspaceFocused = it.isFocused; if (it.isFocused) focusedDigit = -1 }
                            .focusable()
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { if (pin.isNotEmpty()) pin = pin.dropLast(1); true }
                                    Key.DirectionUp    -> { try { digitFocusRequesters[7].requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionRight -> { try { cancelFocus.requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionLeft, Key.DirectionDown -> true
                                    else -> false
                                }
                            }
                            .border(
                                width = if (backspaceFocused) 2.dp else 0.dp,
                                color = if (backspaceFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (backspaceFocused) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Backspace, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Delete", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Cancel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .focusRequester(cancelFocus)
                            .onFocusChanged { cancelFocused = it.isFocused; if (it.isFocused) focusedDigit = -1 }
                            .focusable()
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onDismiss(); true }
                                    Key.DirectionUp    -> { try { digitFocusRequesters[8].requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionLeft  -> { try { backspaceFocus.requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionRight, Key.DirectionDown -> true
                                    else -> false
                                }
                            }
                            .border(
                                width = if (cancelFocused) 2.dp else 0.dp,
                                color = if (cancelFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (cancelFocused) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Cancel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: String,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
    onClick: () -> Unit
) {
    var localFocused by remember { mutableStateOf(false) }
    val focused = isFocused || localFocused
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.14f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness    = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "pinDigitScale",
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                localFocused = it.hasFocus
                if (it.hasFocus) onFocus()
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (
                            e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                            )) { onClick(); true }
                else onKeyEvent(e)
            }
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (focused) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}