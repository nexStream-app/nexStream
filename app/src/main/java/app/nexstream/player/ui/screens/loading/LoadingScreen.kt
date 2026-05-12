package app.nexstream.player.ui.screens.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    val playlists by viewModel.playlists.collectAsState()
    val playlistsLoaded by viewModel.playlistsLoaded.collectAsState()
    val channelCount by viewModel.channelCount.collectAsState()

    val statusText = when {
        !playlistsLoaded    -> "Starting up..."
        playlists.isEmpty() -> "No playlist found..."
        else                -> "Loading..."
    }

    LaunchedEffect(playlistsLoaded, playlists) {
        when {
            !playlistsLoaded -> return@LaunchedEffect
            playlists.isEmpty() -> {
                kotlinx.coroutines.delay(300)
                onLoadingComplete(false)
            }
            else -> {
                // Playlists exist — navigate immediately, screens handle their own loading state
                onLoadingComplete(true)
            }
        }
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