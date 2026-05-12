package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["channelId", "startTime", "endTime"]),
        Index(value = ["channelId"])
    ]
)
data class ProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val category: String?,
    val icon: String?
)