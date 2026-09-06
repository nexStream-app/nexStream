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
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ContentRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PlaylistRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting 24h content refresh")
        var anyFailure = false

        val playlists = try {
            repository.getAllPlaylists().first()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playlists: ${e.message}")
            return Result.retry()
        }

        for (playlist in playlists) {
            if (playlist.type != "XTREAM") continue
            val host     = playlist.xtreamHost     ?: continue
            val username = playlist.xtreamUsername ?: continue
            val password = playlist.xtreamPassword ?: continue
            if (host.isBlank() || username.isBlank() || password.isBlank()) continue

            try {
                repository.refreshChannelsAndEPG(playlist.id, host, username, password)
                Log.d(TAG, "Channels+EPG refreshed for '${playlist.name}'")
            } catch (e: Exception) {
                Log.w(TAG, "Channel refresh failed for '${playlist.name}': ${e.message}")
                anyFailure = true
            }

            try {
                repository.fetchAndStoreMovies(playlist.id, host, username, password)
                Log.d(TAG, "Movies refreshed for '${playlist.name}'")
            } catch (e: Exception) {
                Log.w(TAG, "Movie refresh failed for '${playlist.name}': ${e.message}")
                anyFailure = true
            }

            try {
                repository.fetchAndStoreSeries(playlist.id, host, username, password)
                Log.d(TAG, "Series refreshed for '${playlist.name}'")
            } catch (e: Exception) {
                Log.w(TAG, "Series refresh failed for '${playlist.name}': ${e.message}")
                anyFailure = true
            }
        }

        // Check watchlisted series for new episodes
        try {
            repository.checkForNewEpisodesInWatchlist()
            Log.d(TAG, "New-episode check complete")
        } catch (e: Exception) {
            Log.w(TAG, "New-episode check failed: ${e.message}")
        }

        Log.d(TAG, "Content refresh complete (anyFailure=$anyFailure)")
        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "ContentRefreshWorker"
        const val WORK_NAME   = "content_refresh_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ContentRefreshWorker>(
                24, TimeUnit.HOURS,
                60, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Content refresh scheduled every 24 hours")
        }
    }
}
