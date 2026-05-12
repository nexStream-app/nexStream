package app.nexstream.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val type: String, // "M3U" or "XTREAM"
    val xtreamHost: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val xtreamExpiry: String? = null, // Add this field
    val addedDate: Long = System.currentTimeMillis()
)