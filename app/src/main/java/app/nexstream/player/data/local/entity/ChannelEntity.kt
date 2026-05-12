package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val epgChannelId: String?,
    val playlistId: String,
    val isFavourite: Boolean = false,
    val sortIndex: Int = 0,
    val tvArchive: Int = 0,           // 1 = catch-up supported
    val tvArchiveDuration: Int = 0,   // number of days catch-up available
    val streamId: String? = null      // raw stream_id from Xtream API
)

