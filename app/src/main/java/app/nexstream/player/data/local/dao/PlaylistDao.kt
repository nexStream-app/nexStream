package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY addedDate DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deleteById(playlistId: String)

    @Update
    suspend fun update(playlist: PlaylistEntity)
}