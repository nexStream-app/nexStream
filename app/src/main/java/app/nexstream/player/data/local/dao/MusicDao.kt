package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrackEntity>)

    @Query("DELETE FROM music_tracks WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("""
        SELECT * FROM music_tracks
        WHERE playlistId IN (:playlistIds)
          AND (:genre IS NULL OR genre = :genre)
        ORDER BY albumArtist, album, discNumber, trackNumber, title
    """)
    fun getTracks(playlistIds: List<String>, genre: String?): Flow<List<MusicTrackEntity>>

    @Query("""
        SELECT DISTINCT genre FROM music_tracks
        WHERE playlistId IN (:playlistIds) AND genre IS NOT NULL
        ORDER BY genre
    """)
    fun getGenres(playlistIds: List<String>): Flow<List<String>>

    @Query("""
        SELECT DISTINCT COALESCE(albumArtist, artist)
        FROM music_tracks
        WHERE playlistId IN (:playlistIds)
          AND (albumArtist IS NOT NULL OR artist IS NOT NULL)
        ORDER BY COALESCE(albumArtist, artist) COLLATE NOCASE
    """)
    fun getArtists(playlistIds: List<String>): Flow<List<String>>
}
