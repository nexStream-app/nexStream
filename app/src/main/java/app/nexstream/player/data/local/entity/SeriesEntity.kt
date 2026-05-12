package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["categoryName"]),
        Index(value = ["playlistId", "categoryName"])
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val categoryId: String?,
    val categoryName: String?,
    val seasonCount: Int = 0,
    val playlistId: String
)