package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,          // "${channelId}_${startTime}"
    val channelId: String,
    val channelName: String,
    val streamUrl: String,
    val programTitle: String,
    val startTime: Long                  // epoch ms
)