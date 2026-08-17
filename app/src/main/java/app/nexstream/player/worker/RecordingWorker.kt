package app.nexstream.player.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nexstream.player.downloads.NexStreamDownloadManager
import app.nexstream.player.recording.RecordingService

class RecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url       = inputData.getString(KEY_STREAM_URL)  ?: return Result.failure()
        val title     = inputData.getString(KEY_TITLE)       ?: return Result.failure()
        val profile   = inputData.getString(KEY_PROFILE_ID)  ?: "default"
        val desc      = inputData.getString(KEY_DESCRIPTION)
        val endTimeMs = inputData.getLong(KEY_END_TIME, 0L)
        if (!NexStreamDownloadManager.hasEnoughSpace(applicationContext)) return Result.failure()
        RecordingService.start(applicationContext, url, title, profile, description = desc, endTimeMs = endTimeMs)
        return Result.success()
    }

    companion object {
        const val KEY_STREAM_URL  = "stream_url"
        const val KEY_TITLE       = "title"
        const val KEY_PROFILE_ID  = "profile_id"
        const val KEY_DESCRIPTION = "description"
        const val KEY_END_TIME    = "end_time_ms"
    }
}
