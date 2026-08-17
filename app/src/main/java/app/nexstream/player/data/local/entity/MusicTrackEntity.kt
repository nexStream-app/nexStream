package app.nexstream.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "music_tracks", indices = [Index("playlistId")])
data class MusicTrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "") val jellyfinItemId: String = "",
    val title: String,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val albumArtUrl: String? = null,
    val streamUrl: String,
    val playlistId: String,
    val genre: String? = null,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    @ColumnInfo(defaultValue = "0") val hasLyrics: Boolean = false
)
