package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,          // "$playlistId-$episodeId"
    val episodeId: String,               // Raw Xtream episode ID
    val seriesId: String,                // Parent series ID (matches SeriesEntity.id)
    val name: String,
    val seasonNum: Int,
    val episodeNum: Int,
    val streamUrl: String,
    val posterUrl: String?,
    val plot: String?,
    val duration: String?,
    val containerExtension: String,
    val playlistId: String,
    val lastPlayedPosition: Long = 0,
    val lastPlayedTimestamp: Long = 0
)