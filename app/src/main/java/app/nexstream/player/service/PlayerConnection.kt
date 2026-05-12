package app.nexstream.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var _controller: MediaController? = null
    val controller get() = _controller

    fun connect(onConnected: (MediaController) -> Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, NexStreamPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            _controller = controllerFuture?.get()
            _controller?.let { onConnected(it) }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _controller = null
    }
}