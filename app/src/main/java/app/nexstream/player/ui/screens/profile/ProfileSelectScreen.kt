package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.data.local.entity.ProfileEntity

@Composable
fun ProfileSelectScreen(
    profiles: List<ProfileEntity>,
    appName: String,
    onProfileSelected: (ProfileEntity) -> Unit
) {
    var showPinDialog  by remember { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError       by remember { mutableStateOf(false) }

    val focusRequesters = remember(profiles) { profiles.map { FocusRequester() } }

    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            try { focusRequesters[0].requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier          = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            Text(
                text       = appName,
                color      = MaterialTheme.colorScheme.primary,
                fontSize   = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text       = "Who's watching?",
                color      = Color.White,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding        = PaddingValues(horizontal = 48.dp)
            ) {
                items(profiles.size) { index ->
                    val profile = profiles[index]
                    ProfileAvatar(
                        profile        = profile,
                        focusRequester = focusRequesters.getOrNull(index),
                        onClick        = {
                            if (profile.pinHash != null) {
                                pendingProfile = profile
                                showPinDialog  = true
                                pinError       = false
                            } else {
                                onProfileSelected(profile)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPinDialog && pendingProfile != null) {
        PinEntryDialog(
            profile   = pendingProfile!!,
            hasError  = pinError,
            onDismiss = { showPinDialog = false; pendingProfile = null },
            onConfirm = { pin ->
                if (hashPin(pin) == pendingProfile!!.pinHash) {
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
fun ProfileAvatar(
    profile:        ProfileEntity,
    showName:       Boolean       = true,
    isSelected:     Boolean       = false,
    focusRequester: FocusRequester? = null,
    onClick:        () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape     = RoundedCornerShape(12.dp)
    val primary   = MaterialTheme.colorScheme.primary
    val bgColor   = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                // clip first — everything inside is clipped to shape
                .clip(shape)
                // background after clip — no flash
                .background(bgColor)
                // border drawn as overlay inside the clip — stays visible
                .then(
                    if (isFocused || isSelected)
                        Modifier.border(3.dp, primary, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = profile.emoji, fontSize = 48.sp)
        }
        if (showName) {
            Text(
                text       = profile.name,
                color      = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center,
                maxLines   = 1
            )
        }
    }
}

@Composable
fun PinEntryDialog(
    profile:   ProfileEntity,
    hasError:  Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin    by remember { mutableStateOf("") }
    val maxPin = 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(profile.emoji, fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("Enter PIN for ${profile.name}")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxPin) { i ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                if (hasError) {
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                val digits = listOf("1","2","3","4","5","6","7","8","9","","0","<")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    digits.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { digit ->
                                if (digit.isEmpty()) {
                                    Spacer(Modifier.size(64.dp))
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            when (digit) {
                                                "<"  -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                else -> if (pin.length < maxPin) {
                                                    pin += digit
                                                    if (pin.length == maxPin) onConfirm(pin)
                                                }
                                            }
                                        },
                                        modifier        = Modifier.size(64.dp),
                                        shape           = RoundedCornerShape(8.dp),
                                        contentPadding  = PaddingValues(0.dp)
                                    ) {
                                        Text(digit, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun hashPin(pin: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}