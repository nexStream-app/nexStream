package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["categoryName"]),
        Index(value = ["playlistId", "categoryName"])
    ]
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val duration: String?,
    val categoryId: String?,
    val categoryName: String?,
    val playlistId: String,
    val isFavourite: Boolean = false,
    val lastPlayedPosition: Long = 0,
    val lastPlayedTimestamp: Long = 0
)
