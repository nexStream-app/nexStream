package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.TmdbPosterEntity

@Dao
interface TmdbPosterDao {

    @Query("SELECT * FROM tmdb_poster_cache WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): TmdbPosterEntity?

    // Room IN clause is limited to 999 variables — chunking handled in repository/viewmodel
    @Query("SELECT * FROM tmdb_poster_cache WHERE title IN (:titles)")
    suspend fun getByTitlesChunk(titles: List<String>): List<TmdbPosterEntity>

    @Upsert
    suspend fun upsert(entity: TmdbPosterEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TmdbPosterEntity>)

    @Query("DELETE FROM tmdb_poster_cache WHERE fetchedAt < :cutoff")
    suspend fun pruneOld(cutoff: Long = System.currentTimeMillis() - 30L * 86_400_000L)
}

// Extension function to safely query in chunks of 999
suspend fun TmdbPosterDao.getByTitles(titles: List<String>): List<TmdbPosterEntity> {
    if (titles.isEmpty()) return emptyList()
    return titles.chunked(999).flatMap { chunk -> getByTitlesChunk(chunk) }
}