package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.SeriesGridItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSeries(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getSeriesByPlaylist(playlistId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE categoryName = :category ORDER BY name ASC")
    fun getSeriesByCategory(category: String): Flow<List<SeriesEntity>>

    @Query("SELECT DISTINCT categoryName FROM series WHERE categoryName IS NOT NULL")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNum ASC, episodeNum ASC")
    fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNum = :season ORDER BY episodeNum ASC")
    fun getEpisodesForSeason(seriesId: String, season: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT DISTINCT seasonNum FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNum ASC")
    fun getSeasonsForSeries(seriesId: String): Flow<List<Int>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun getEpisodeById(episodeId: String): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNum = :season AND episodeNum = :episodeNum")
    fun getNextEpisode(seriesId: String, season: Int, episodeNum: Int): Flow<EpisodeEntity?>

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesByPlaylist(playlistId: String)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    suspend fun deleteEpisodesByPlaylist(playlistId: String)

    @Query("SELECT * FROM series WHERE id = :seriesId")
    fun getSeriesById(seriesId: String): Flow<SeriesEntity?>

    @Query("UPDATE episodes SET lastPlayedPosition = :position, lastPlayedTimestamp = :timestamp WHERE id = :episodeId")
    suspend fun updateEpisodePosition(episodeId: String, position: Long, timestamp: Long)

    @Query("UPDATE episodes SET lastPlayedPosition = 0, lastPlayedTimestamp = 0 WHERE id = :episodeId")
    suspend fun clearEpisodePosition(episodeId: String)

    @Query("SELECT COUNT(*) FROM episodes WHERE seriesId = :seriesId AND lastPlayedPosition > 0")
    fun getWatchedEpisodeCount(seriesId: String): Flow<Int>

    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 50")
    fun searchSeries(query: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SeriesEntity?

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisodeByIdOnce(id: String): EpisodeEntity?

    @Query("SELECT seriesId, COUNT(*) as count FROM episodes WHERE lastPlayedPosition > 0 GROUP BY seriesId")
    fun getAllWatchedEpisodeCounts(): Flow<List<WatchedCountRow>>

    data class WatchedCountRow(val seriesId: String, val count: Int)

    @Query("SELECT id, name, posterUrl, categoryName, playlistId, seasonCount FROM series WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getSeriesGridItems(playlistId: String): Flow<List<SeriesGridItem>>

    @Query("SELECT id, name, posterUrl, categoryName, playlistId, seasonCount FROM series WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name ASC")
    fun getSeriesGridItemsByCategory(playlistId: String, category: String): Flow<List<SeriesGridItem>>

    // Slim projection — only fields needed for progress migration
    @Query("SELECT id, seriesId, lastPlayedPosition FROM episodes WHERE lastPlayedPosition > 0")
    suspend fun getAllEpisodesWithProgress(): List<EpisodeProgressItem>

    data class EpisodeProgressItem(val id: String, val seriesId: String, val lastPlayedPosition: Long)
}