package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "itemId"],
    indices = [
        Index("profileId"),
        Index("itemId"),
        Index(value = ["profileId", "seriesId"])
    ]
)
data class WatchProgressEntity(
    val profileId:  String,
    val itemId:     String,   // movieId or episodeId
    val itemType:   String,   // "movie" or "episode"
    val seriesId:   String?,  // null for movies, seriesId for episodes
    val positionMs: Long,
    val durationMs: Long = 0L,
    val updatedAt:  Long = System.currentTimeMillis()
)
