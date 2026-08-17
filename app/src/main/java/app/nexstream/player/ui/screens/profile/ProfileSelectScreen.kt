package app.nexstream.player.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
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
        modifier          = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                color      = MaterialTheme.colorScheme.onBackground,
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
        ThemedPinEntryDialog(
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (
                        e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                    )) { onClick(); true } else false
                }
                .focusable()
                .clickable { onClick() }
                .clip(shape)
                .background(bgColor)
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
                color      = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center,
                maxLines   = 1
            )
        }
    }
}

fun hashPin(pin: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}