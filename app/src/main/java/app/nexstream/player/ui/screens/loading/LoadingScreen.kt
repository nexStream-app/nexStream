package app.nexstream.player.ui.screens.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.R
import app.nexstream.player.ui.screens.main.MainScreenViewModel

@Composable
fun LoadingScreen(
    onLoadingComplete: (hasPlaylists: Boolean) -> Unit,
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val loadState     by viewModel.playlistLoadState.collectAsState()
    val profilesReady by viewModel.profilesReady.collectAsState()

    val startingUp = stringResource(R.string.loading_starting_up)
    val noPlaylist = stringResource(R.string.loading_no_playlist)
    val loading    = stringResource(R.string.loading_loading)
    val statusText = when {
        !loadState.loaded             -> startingUp
        loadState.playlists.isEmpty() -> noPlaylist
        else                          -> loading
    }

    // Wait for both playlists (Room, instant) AND the first profile sync (network) before
    // navigating, so the profile picker always shows profiles from all devices.
    // profilesReady falls through after 3 s to handle offline / sync-disabled cases.
    LaunchedEffect(loadState, profilesReady) {
        if (!loadState.loaded || !profilesReady) return@LaunchedEffect
        onLoadingComplete(loadState.playlists.isNotEmpty())
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp).alpha(alpha),
                strokeWidth = 6.dp
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily(Font(R.font.exo2))
            )
        }
    }
}