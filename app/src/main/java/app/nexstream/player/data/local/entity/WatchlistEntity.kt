package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WatchlistType { CHANNEL, MOVIE, SERIES, MUSIC }

@Entity(
    tableName = "watchlist",
    primaryKeys = ["id", "profileId"]
)
data class WatchlistEntity(
    val id: String,
    val profileId: String = "default",
    val type: WatchlistType,
    val name: String,
    val posterUrl: String?,
    val streamUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
)