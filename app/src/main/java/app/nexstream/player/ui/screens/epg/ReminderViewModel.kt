package app.nexstream.player.ui.screens.epg

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import app.nexstream.player.data.local.dao.ReminderDao
import app.nexstream.player.data.local.entity.ReminderEntity
import app.nexstream.player.worker.ReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao
) : ViewModel() {

    val reminderIds: StateFlow<Set<String>> = reminderDao.getAll()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun reminderId(channelId: String, startTime: Long) = "${channelId}_${startTime}"

    fun setReminder(
        channelId: String,
        channelName: String,
        streamUrl: String,
        programTitle: String,
        startTime: Long
    ) {
        val id = reminderId(channelId, startTime)
        viewModelScope.launch {
            reminderDao.insert(ReminderEntity(
                id           = id,
                channelId    = channelId,
                channelName  = channelName,
                streamUrl    = streamUrl,
                programTitle = programTitle,
                startTime    = startTime
            ))
            scheduleWork(id, channelName, streamUrl, programTitle, startTime)
        }
    }

    fun cancelReminder(channelId: String, startTime: Long) {
        val id = reminderId(channelId, startTime)
        viewModelScope.launch {
            reminderDao.deleteById(id)
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        }
    }

    private fun scheduleWork(
        id: String,
        channelName: String,
        streamUrl: String,
        programTitle: String,
        startTime: Long
    ) {
        val now     = System.currentTimeMillis()
        val fireAt  = if (startTime - now <= 5 * 60_000L) now else startTime - 60_000L
        val delayMs = (fireAt - now).coerceAtLeast(0L)

        val data = workDataOf(
            ReminderWorker.KEY_REMINDER_ID   to id,
            ReminderWorker.KEY_CHANNEL_NAME  to channelName,
            ReminderWorker.KEY_PROGRAM_TITLE to programTitle,
            ReminderWorker.KEY_STREAM_URL    to streamUrl,
            ReminderWorker.KEY_START_TIME    to startTime
        )

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder")
            .build()

        android.util.Log.d("ReminderViewModel", "Scheduling '$programTitle' on '$channelName' in ${delayMs / 1000}s")

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(id: String) = "reminder_$id"
}