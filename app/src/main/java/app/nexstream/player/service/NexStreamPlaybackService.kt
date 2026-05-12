package app.nexstream.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.nexstream.player.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class NexStreamPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val EXTRA_URL      = "extra_url"
        const val EXTRA_TITLE    = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_POSITION = "extra_position"
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val sessionIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val url      = intent?.getStringExtra(EXTRA_URL)      ?: return START_NOT_STICKY
        val title    = intent.getStringExtra(EXTRA_TITLE)
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)

        val player = mediaSession?.player ?: return START_NOT_STICKY

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        if (position > 0) player.seekTo(position)
        player.playWhenReady = true

        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }
}