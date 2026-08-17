package app.nexstream.player.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RecordingService : Service() {

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs   = ConcurrentHashMap<Long, Job>()
    private val calls  = ConcurrentHashMap<Long, Call>()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG        = "RecordingService"
        private const val CHANNEL_ID = "nexstream_recording"
        private const val NOTIF_ID   = 8001

        const val ACTION_START      = "nexstream.record.START"
        const val ACTION_STOP       = "nexstream.record.STOP"
        const val EXTRA_URL         = "url"
        const val EXTRA_TITLE       = "title"
        const val EXTRA_PROFILE     = "profile_id"
        const val EXTRA_REC_ID      = "rec_id"
        const val EXTRA_POSTER      = "poster_url"
        const val EXTRA_LOGO        = "logo_url"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_END_TIME    = "end_time_ms"

        fun start(
            ctx: Context, url: String, title: String, profileId: String,
            posterUrl: String? = null, channelLogoUrl: String? = null,
            description: String? = null, endTimeMs: Long = 0L
        ) {
            ctx.startForegroundService(
                Intent(ctx, RecordingService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PROFILE, profileId)
                    if (posterUrl != null) putExtra(EXTRA_POSTER, posterUrl)
                    if (channelLogoUrl != null) putExtra(EXTRA_LOGO, channelLogoUrl)
                    if (!description.isNullOrBlank()) putExtra(EXTRA_DESCRIPTION, description)
                    if (endTimeMs > 0L) putExtra(EXTRA_END_TIME, endTimeMs)
                }
            )
        }

        fun stop(ctx: Context, recId: Long) {
            ctx.startService(
                Intent(ctx, RecordingService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_REC_ID, recId)
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url    = intent.getStringExtra(EXTRA_URL)          ?: return START_NOT_STICKY
                val title  = intent.getStringExtra(EXTRA_TITLE)        ?: "Recording"
                val profile = intent.getStringExtra(EXTRA_PROFILE)     ?: "default"
                val poster = intent.getStringExtra(EXTRA_POSTER)
                val logo   = intent.getStringExtra(EXTRA_LOGO)
                val desc   = intent.getStringExtra(EXTRA_DESCRIPTION)
                val recId    = NexStreamRecordingManager.startRecording(this, url, title, profile, poster, logo, desc)
                val endTimeMs = intent.getLongExtra(EXTRA_END_TIME, 0L)
                Log.i(TAG, "start id=$recId title=$title endTimeMs=$endTimeMs")
                jobs[recId] = scope.launch { record(recId, url) }
                if (endTimeMs > 0L) {
                    scope.launch {
                        val delayMs = endTimeMs - System.currentTimeMillis()
                        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                        Log.i(TAG, "auto-stop id=$recId (program end time reached)")
                        calls.remove(recId)?.cancel()
                        jobs.remove(recId)?.cancel()
                        NexStreamRecordingManager.finalizeRecording(this@RecordingService, recId)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            updateNotif()
                            if (jobs.isEmpty()) stopSelf()
                        }
                    }
                }
                updateNotif()
            }
            ACTION_STOP -> {
                val recId = intent.getLongExtra(EXTRA_REC_ID, -1L)
                if (recId >= 0L) {
                    Log.i(TAG, "stop id=$recId")
                    calls.remove(recId)?.cancel()
                    jobs.remove(recId)?.cancel()
                    NexStreamRecordingManager.finalizeRecording(this, recId)
                    updateNotif()
                }
                if (jobs.isEmpty()) stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun record(recId: Long, url: String) {
        val path = NexStreamRecordingManager.getFilePath(this, recId) ?: return
        val file = File(path).also { it.parentFile?.mkdirs() }
        try {
            val call = http.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
                    .build()
            )
            calls[recId] = call
            call.execute().use { resp ->
                val body = resp.body ?: return
                file.outputStream().buffered(65536).use { out ->
                    val buf = ByteArray(65536)
                    var total = 0L
                    var lastUpdate = 0L
                    body.byteStream().use { inp ->
                        var n = inp.read(buf)
                        while (n >= 0) {
                            out.write(buf, 0, n)
                            total += n
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 2000) {
                                NexStreamRecordingManager.updateBytes(this@RecordingService, recId, total)
                                lastUpdate = now
                            }
                            n = inp.read(buf)
                        }
                        NexStreamRecordingManager.updateBytes(this@RecordingService, recId, total)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            calls.remove(recId)
            NexStreamRecordingManager.finalizeRecording(this@RecordingService, recId)
            jobs.remove(recId)
            withContext(Dispatchers.Main) {
                updateNotif()
                if (jobs.isEmpty()) stopSelf()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Live TV recording"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(if (jobs.isEmpty()) "Recording stopped" else "Recording")
        .setContentText(
            if (jobs.isEmpty()) "All recordings stopped"
            else "${jobs.size} recording${if (jobs.size == 1) "" else "s"} active"
        )
        .setOngoing(jobs.isNotEmpty())
        .setSilent(true)
        .build()

    private fun updateNotif() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotif())
    }
}
