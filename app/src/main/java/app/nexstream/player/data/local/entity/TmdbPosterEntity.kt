package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_poster_cache")
data class TmdbPosterEntity(
    @PrimaryKey
    val title:     String,       // programme title — exact match key
    val posterUrl: String,       // https://image.tmdb.org/t/p/w300/...
    val fetchedAt: Long = System.currentTimeMillis()
)