package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.IdAddedAt
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

    @Query("SELECT * FROM movies WHERE `cast` LIKE :pattern OR director LIKE :pattern ORDER BY name ASC LIMIT 100")
    fun searchMoviesByPeople(pattern: String): Flow<List<MovieEntity>>

    @Query("""
        SELECT * FROM movies WHERE
            LOWER(name) = LOWER(:title) OR
            name LIKE :title || ' (%' OR
            name LIKE :title || ' [%'
        ORDER BY CASE WHEN LOWER(name) = LOWER(:title) THEN 0 ELSE 1 END ASC, name ASC
        LIMIT 3
    """)
    suspend fun findMoviesByTitle(title: String): List<MovieEntity>

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
    @Query("SELECT id, name, posterUrl, categoryName, streamUrl, playlistId, lastPlayedPosition, certification, rating, addedAt, releaseDate FROM movies WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMovieGridItems(playlistId: String): Flow<List<MovieGridItem>>

    // Category-filtered version
    @Query("SELECT id, name, posterUrl, categoryName, streamUrl, playlistId, lastPlayedPosition, certification, rating, addedAt, releaseDate FROM movies WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name ASC")
    fun getMovieGridItemsByCategory(playlistId: String, category: String): Flow<List<MovieGridItem>>

    @Query("SELECT id, addedAt FROM movies WHERE playlistId = :playlistId")
    suspend fun getMovieAddedAtForPlaylist(playlistId: String): List<IdAddedAt>

    @Query("UPDATE movies SET certification = :certification WHERE id = :id")
    suspend fun updateCertification(id: String, certification: String?)

    @Query("UPDATE movies SET originalLanguage = :lang WHERE id = :id")
    suspend fun updateOriginalLanguage(id: String, lang: String)

    @Query("UPDATE movies SET releaseDate = :releaseDate WHERE id = :id")
    suspend fun updateReleaseDate(id: String, releaseDate: String)

    @Query("UPDATE movies SET `cast` = :cast, director = :director WHERE id = :id")
    suspend fun updateCastAndDirector(id: String, cast: String?, director: String?)

    @Query("SELECT id, name FROM movies WHERE `cast` IS NULL ORDER BY name ASC")
    suspend fun getMoviesWithoutCast(): List<MovieNameRow>

    data class MovieNameRow(val id: String, val name: String)

    @Query("UPDATE movies SET rtCriticsScore = :criticsScore, rtAudienceScore = :audienceScore, rtConsensus = :consensus, metascore = :metascore WHERE id = :id")
    suspend fun updateRtData(id: String, criticsScore: Int?, audienceScore: Int?, consensus: String?, metascore: Int?)

    @Query("SELECT id, certification FROM movies WHERE playlistId = :playlistId AND certification IS NOT NULL")
    suspend fun getExistingCertifications(playlistId: String): List<MovieCertRow>

    data class MovieCertRow(val id: String, val certification: String?)

    // Slim projection for one-time progress migration — only fields needed
    @Query("SELECT id, lastPlayedPosition FROM movies WHERE lastPlayedPosition > 0")
    suspend fun getAllWithProgress(): List<MovieProgressItem>

    data class MovieProgressItem(val id: String, val lastPlayedPosition: Long)

    @Query("SELECT name FROM movies")
    suspend fun getAllMovieNames(): List<String>

    @Query("SELECT * FROM movies WHERE LOWER(name) IN (:lowerTitles)")
    suspend fun findMoviesByTitlesBatch(lowerTitles: List<String>): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE streamUrl = :streamUrl LIMIT 1")
    suspend fun getByStreamUrl(streamUrl: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE streamUrl LIKE '%/' || :streamId || '.%' OR streamUrl LIKE '%/' || :streamId LIMIT 1")
    suspend fun getByXtreamStreamId(streamId: String): MovieEntity?
}