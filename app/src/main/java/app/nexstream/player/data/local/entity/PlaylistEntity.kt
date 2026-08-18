package app.nexstream.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val type: String, // "M3U", "XTREAM", "JELLYFIN", or "PLEX"
    val xtreamHost: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val xtreamExpiry: String? = null,
    val addedDate: Long = System.currentTimeMillis(),
    val sortIndex: Int = 0,
    @ColumnInfo(name = "plex_token") val plexToken: String? = null,
)