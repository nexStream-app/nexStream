package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.ProfileEntity

@Composable
fun ProfileSwitchDialog(
    profiles: List<ProfileEntity>,
    activeProfileId: String?,
    onProfileSelected: (ProfileEntity) -> Unit,
    onDismiss: () -> Unit,
    onManageProfiles: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }

    var focusedIndex by remember { mutableStateOf(
        profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    ) }

    val focusRequesters = remember(profiles) { profiles.map { FocusRequester() } }
    val dialogFocus = remember { FocusRequester() }

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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Profile avatars row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.fillMaxWidth()
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

                HorizontalDivider()

                // Manage profiles button
                val manageFocus = remember { FocusRequester() }
                var manageFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(manageFocus)
                        .focusable()
                        .onFocusChanged { manageFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (
                                        e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                                        )) { onManageProfiles(); true } else false
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    if (manageFocused)
                        Button(onClick = onManageProfiles, shape = RoundedCornerShape(8.dp)) {
                            Text("Manage Profiles")
                        }
                    else
                        OutlinedButton(onClick = onManageProfiles, shape = RoundedCornerShape(8.dp)) {
                            Text("Manage Profiles")
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

    // Zone: 0 = row1 (0-4), 1 = row2 (5-9), 2 = action row
    var focusedDigit by remember { mutableStateOf(0) } // index into digits list

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { digitFocusRequesters[0].requestFocus() } catch (_: Exception) {}
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var backspaceFocused by remember { mutableStateOf(false) }
                    var cancelFocused by remember { mutableStateOf(false) }

                    // Backspace
                    OutlinedButton(
                        onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(backspaceFocus)
                            .onFocusChanged { backspaceFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.DirectionUp   -> { try { digitFocusRequesters[7].requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionRight -> { try { cancelFocus.requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionLeft  -> true
                                    else -> false
                                } else false
                            },
                        colors = if (backspaceFocused)
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Backspace, null,
                            Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }

                    // Cancel
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cancelFocus)
                            .onFocusChanged { cancelFocused = it.isFocused }
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) when (e.key) {
                                    Key.DirectionUp   -> { try { digitFocusRequesters[8].requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionLeft -> { try { backspaceFocus.requestFocus() } catch (_: Exception) {}; true }
                                    Key.DirectionRight -> true
                                    else -> false
                                } else false
                            },
                        colors = if (cancelFocused)
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, null,
                            Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
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
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(52.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (
                            e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                            )) { onClick(); true }
                else onKeyEvent(e)
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}