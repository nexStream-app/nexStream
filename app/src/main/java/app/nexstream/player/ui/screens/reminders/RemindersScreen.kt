package app.nexstream.player.ui.screens.reminders

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.nexstream.player.data.local.dao.ReminderDao
import app.nexstream.player.data.local.entity.ReminderEntity
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao
) : ViewModel() {

    val reminders: StateFlow<List<ReminderEntity>> = reminderDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancel(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderDao.deleteById(reminder.id)
            WorkManager.getInstance(context).cancelUniqueWork("reminder_${reminder.id}")
        }
    }

    fun clearExpired() {
        viewModelScope.launch {
            reminderDao.deleteExpired(System.currentTimeMillis())
        }
    }
}

private fun reminderTimeLabel(startTime: Long, nowMs: Long = System.currentTimeMillis()): String {
    val now   = nowMs
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val tomorrow  = today + 86_400_000L
    val timeFmt   = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr   = timeFmt.format(Date(startTime))
    val diffMins  = ((startTime - now) / 60_000L).coerceAtLeast(0L)
    val dayLabel  = when {
        startTime < today + 86_400_000L -> "Today"
        startTime < tomorrow + 86_400_000L -> "Tomorrow"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(startTime))
    }
    val countdownLabel = when {
        startTime <= now -> "Starting now"
        diffMins < 60   -> "in ${diffMins}m"
        else            -> "in ${diffMins / 60}h ${diffMins % 60}m"
    }
    return "$dayLabel · $timeStr · $countdownLabel"
}

@Composable
fun RemindersScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme  = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp

    val reminders by viewModel.reminders.collectAsState()

    // Clean up expired reminders when screen is opened
    LaunchedEffect(Unit) { viewModel.clearExpired() }

    // Ticker to keep countdown labels live — updates every 30 seconds
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            tickMs = System.currentTimeMillis()
        }
    }

    val upcoming = remember(reminders, tickMs) {
        reminders.filter { it.startTime >= tickMs - 60_000L }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text  = "Upcoming",
                style = MaterialTheme.typography.titleMedium,
                color = sTheme.categoryText
            )
        }
        HorizontalDivider(color = sTheme.divider)

        if (upcoming.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications, null,
                        modifier = Modifier.size(48.dp),
                        tint = sTheme.categoryText.copy(alpha = 0.3f)
                    )
                    Text(
                        "No upcoming reminders",
                        style = MaterialTheme.typography.titleMedium,
                        color = sTheme.categoryText.copy(alpha = 0.6f)
                    )
                    Text(
                        "Set reminders from the TV Guide",
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(upcoming, key = { it.id }) { reminder ->
                    val isFirst = upcoming.indexOf(reminder) == 0
                    ReminderRow(
                        reminder       = reminder,
                        nowMs          = tickMs,
                        focusRequester = if (isFirst) firstItemFocusRequester else null,
                        onCancel       = { viewModel.cancel(reminder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    nowMs: Long = System.currentTimeMillis(),
    focusRequester: FocusRequester? = null,
    onCancel: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text     = reminder.programTitle,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text  = reminder.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text  = reminderTimeLabel(reminder.startTime, nowMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.NotificationsOff, "Cancel reminder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
