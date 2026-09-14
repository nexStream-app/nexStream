package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
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
    val lastPlayedTimestamp: Long = 0,
    val certification: String? = null,
    val addedAt: Long = 0L,
    val rtCriticsScore:   Int?    = null,
    val rtAudienceScore:  Int?    = null,
    val rtConsensus:      String? = null,
    val originalLanguage: String? = null,
    val metascore:        Int?    = null,
    @Ignore val trailerUrl: String? = null,
) {
    // Room KSP requires a constructor whose every parameter maps to a DB column.
    // The primary constructor includes @Ignore trailerUrl, so we provide this
    // secondary constructor (without it) for Room to use when reading from the DB.
    constructor(
        id: String, name: String, streamUrl: String, posterUrl: String?,
        backdropUrl: String?, plot: String?, cast: String?, director: String?,
        genre: String?, releaseDate: String?, rating: String?, duration: String?,
        categoryId: String?, categoryName: String?, playlistId: String,
        isFavourite: Boolean, lastPlayedPosition: Long, lastPlayedTimestamp: Long,
        certification: String?, addedAt: Long,
        rtCriticsScore: Int?, rtAudienceScore: Int?, rtConsensus: String?,
        originalLanguage: String?, metascore: Int?,
    ) : this(
        id, name, streamUrl, posterUrl, backdropUrl, plot, cast, director,
        genre, releaseDate, rating, duration, categoryId, categoryName, playlistId,
        isFavourite, lastPlayedPosition, lastPlayedTimestamp, certification, addedAt,
        rtCriticsScore, rtAudienceScore, rtConsensus, originalLanguage, metascore,
        trailerUrl = null,
    )
}
