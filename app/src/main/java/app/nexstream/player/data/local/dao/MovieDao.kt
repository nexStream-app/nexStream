package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.MovieGridItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMoviesByPlaylist(playlistId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE categoryName = :category ORDER BY name ASC")
    fun getMoviesByCategory(category: String): Flow<List<MovieEntity>>

    @Query("SELECT DISTINCT categoryName FROM movies WHERE categoryName IS NOT NULL ORDER BY categoryName ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchMovies(query: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE isFavourite = 1 ORDER BY name ASC")
    fun getFavouriteMovies(): Flow<List<MovieEntity>>

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    @Query("SELECT * FROM movies WHERE id = :movieId")
    fun getMovieById(movieId: String): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MovieEntity?

    // Alias used by PlayerViewModel — returns full entity with plot/cast/director
    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieByIdOnce(id: String): MovieEntity?

    // Lightweight projection — only fetches the columns needed for grid display
    @Query("SELECT id, name, posterUrl, categoryName, streamUrl, playlistId, lastPlayedPosition FROM movies WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMovieGridItems(playlistId: String): Flow<List<MovieGridItem>>

    // Category-filtered version
    @Query("SELECT id, name, posterUrl, categoryName, streamUrl, playlistId, lastPlayedPosition FROM movies WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name ASC")
    fun getMovieGridItemsByCategory(playlistId: String, category: String): Flow<List<MovieGridItem>>

    // Slim projection for one-time progress migration — only fields needed
    @Query("SELECT id, lastPlayedPosition FROM movies WHERE lastPlayedPosition > 0")
    suspend fun getAllWithProgress(): List<MovieProgressItem>

    data class MovieProgressItem(val id: String, val lastPlayedPosition: Long)
}