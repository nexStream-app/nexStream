package app.nexstream.player.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nexstream.player.data.local.dao.TmdbPosterDao
import app.nexstream.player.data.local.dao.getByTitles
import app.nexstream.player.data.local.entity.TmdbPosterEntity
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PlaylistRepository,
    private val tmdbDao:    TmdbPosterDao
) : CoroutineWorker(context, workerParams) {

    private val tmdbToken  = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJhYzA2NjBlNjM2MGVlNDE0NmQyNDA3MzUyOTQ2ZmRkYiIsIm5iZiI6MTc3Njg3MzQ2OS4wNjA5OTk5LCJzdWIiOiI2OWU4ZWZmZDQyM2ZhZDJlYzhlMGNmY2QiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.1gbVrIUZezfVmRoFk692A__HsaO9GahlPeMT6h2JBKo"
    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {

        // ── Step 1: EPG refresh ───────────────────────────────────────────────
        try {
            Log.d(TAG, "Starting EPG refresh")
            val playlists = repository.getAllPlaylists().first()
            playlists.forEach { playlist ->
                val host = playlist.xtreamHost ?: return@forEach
                val user = playlist.xtreamUsername ?: return@forEach
                val pass = playlist.xtreamPassword ?: return@forEach
                if (playlist.type == "XTREAM" && host.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    repository.fetchAndStoreEPG(host, user, pass)
                }
            }
            Log.d(TAG, "EPG refresh completed")
        } catch (e: Exception) {
            Log.e(TAG, "EPG refresh failed: ${e.message}")
            val msg = e.message ?: ""
            if (msg.contains("503") || msg.contains("429")) {
                Log.w(TAG, "Rate limited — waiting for next schedule")
                return Result.success()
            }
            return Result.retry()
        }

        // ── Step 2: Prune old TMDB cache (>30 days) ───────────────────────────
        try {
            tmdbDao.pruneOld()
            Log.d(TAG, "Pruned old TMDB cache entries")
        } catch (e: Exception) {
            Log.w(TAG, "Prune failed: ${e.message}")
        }

        // ── Step 3: Pre-fetch TMDB posters for catchup programmes ─────────────
        try {
            val now   = System.currentTimeMillis()
            val start = now - 7 * 86_400_000L

            val allTitles = mutableSetOf<String>()
            repository.getAllPlaylists().first().forEach { playlist ->
                repository.getChannelsByPlaylist(playlist.id).first()
                    .filter { it.tvArchive == 1 }
                    .forEach { channel ->
                        val epgId = channel.epgChannelId ?: return@forEach
                        try {
                            repository.getProgramsForChannelInRange(epgId, start, now)
                                .first()
                                .filter { it.endTime <= now }
                                .forEach { allTitles.add(it.title) }
                        } catch (_: Exception) {}
                    }
            }

            Log.d(TAG, "TMDB: ${allTitles.size} unique titles")
            val cached   = tmdbDao.getByTitles(allTitles.toList()).map { it.title }.toSet()
            val uncached = allTitles.filter { it !in cached }
            Log.d(TAG, "TMDB: ${cached.size} cached, ${uncached.size} to fetch")

            // CoroutineWorker already runs on Dispatchers.Default — OkHttp execute() is blocking
            // so we run it directly here (CoroutineWorker handles background thread)
            val newEntries = mutableListOf<TmdbPosterEntity>()
            uncached.forEach { title ->
                try {
                    val enc = URLEncoder.encode(title, "UTF-8")
                    val endpoints = listOf(
                        "https://api.themoviedb.org/3/search/tv?query=$enc&include_adult=false&language=en-GB&page=1",
                        "https://api.themoviedb.org/3/search/multi?query=$enc&include_adult=false&language=en-US&page=1"
                    )
                    for (endpoint in endpoints) {
                        val response = httpClient.newCall(
                            Request.Builder().url(endpoint).get()
                                .addHeader("accept", "application/json")
                                .addHeader("Authorization", "Bearer $tmdbToken")
                                .build()
                        ).execute()
                        val body = response.body?.string()
                        response.close()
                        if (response.isSuccessful && body != null) {
                            val path = Regex(""""poster_path":"(/[^"]+)"""").find(body)?.groupValues?.get(1)
                            if (path != null) {
                                newEntries.add(TmdbPosterEntity(title, "https://image.tmdb.org/t/p/w300$path"))
                                Log.d(TAG, "TMDB ✓ $title")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TMDB failed for '$title': ${e::class.simpleName}")
                }
            }

            if (newEntries.isNotEmpty()) {
                tmdbDao.upsertAll(newEntries)
                Log.d(TAG, "Saved ${newEntries.size} new posters to cache")
            }

        } catch (e: Exception) {
            Log.e(TAG, "TMDB step failed: ${e.message}")
            // Non-critical — don't fail the job
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "EpgRefreshWorker"
        const val WORK_NAME   = "epg_refresh_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "EPG + TMDB refresh scheduled every 6 hours")
        }
    }
}