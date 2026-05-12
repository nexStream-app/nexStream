package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RecentlyWatchedType { CHANNEL, MOVIE, EPISODE }

@Entity(
    tableName = "recently_watched",
    primaryKeys = ["id", "profileId"]
)
data class RecentlyWatchedEntity(
    val id: String,
    val profileId: String,
    val type: RecentlyWatchedType,
    val name: String,
    val subtitle: String?,
    val streamUrl: String,
    val logoUrl: String?,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val movieId: String? = null,
    val watchedAt: Long = System.currentTimeMillis()
)