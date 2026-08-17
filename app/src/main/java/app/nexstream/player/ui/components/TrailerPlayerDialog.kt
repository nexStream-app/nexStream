package app.nexstream.player.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun youtubeEmbedUrl(rawUrl: String): String {
    val videoId = when {
        rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
        rawUrl.contains("v=") -> rawUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
        rawUrl.matches(Regex("[A-Za-z0-9_-]{11}")) -> rawUrl
        else -> return rawUrl
    }
    return "https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&playsinline=1"
}

@Composable
fun TrailerPlayerDialog(trailerUrl: String, onDismiss: () -> Unit) {
    val embedUrl = remember(trailerUrl) { youtubeEmbedUrl(trailerUrl) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onDismiss(); true } else false
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadUrl(embedUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close trailer", tint = Color.White)
            }
        }
    }
}
