package app.nexstream.player.worker

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nexstream.player.data.local.dao.ReminderDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: ReminderDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "ReminderWorker doWork() fired")

        val channelName  = inputData.getString(KEY_CHANNEL_NAME)  ?: run { android.util.Log.e(TAG, "Missing channel name");  return Result.failure() }
        val programTitle = inputData.getString(KEY_PROGRAM_TITLE) ?: run { android.util.Log.e(TAG, "Missing program title"); return Result.failure() }
        val reminderId   = inputData.getString(KEY_REMINDER_ID)   ?: run { android.util.Log.e(TAG, "Missing reminder id");   return Result.failure() }
        val streamUrl    = inputData.getString(KEY_STREAM_URL)    ?: run { android.util.Log.e(TAG, "Missing stream url");    return Result.failure() }
        val startTime    = inputData.getLong(KEY_START_TIME, 0L)

        return try {
            // Send local broadcast to MainActivity to show the overlay
            val intent = Intent(ACTION_SHOW_REMINDER).apply {
                setPackage(context.packageName)
                putExtra(KEY_REMINDER_ID,   reminderId)
                putExtra(KEY_CHANNEL_NAME,  channelName)
                putExtra(KEY_PROGRAM_TITLE, programTitle)
                putExtra(KEY_STREAM_URL,    streamUrl)
                putExtra(KEY_START_TIME,    startTime)
            }
            context.sendBroadcast(intent)
            android.util.Log.d(TAG, "Reminder broadcast sent: $programTitle on $channelName")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send reminder broadcast", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ReminderWorker"
        const val ACTION_SHOW_REMINDER = "app.nexstream.player.ACTION_SHOW_REMINDER"
        const val KEY_CHANNEL_NAME     = "channel_name"
        const val KEY_PROGRAM_TITLE    = "program_title"
        const val KEY_REMINDER_ID      = "reminder_id"
        const val KEY_STREAM_URL       = "stream_url"
        const val KEY_START_TIME       = "start_time"
    }
}