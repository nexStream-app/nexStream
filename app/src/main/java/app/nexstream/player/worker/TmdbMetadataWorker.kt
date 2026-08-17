package app.nexstream.player.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TmdbMetadataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PlaylistRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting TMDB metadata enrichment")
            repository.enrichMoviesWithTmdbCredits()
            repository.enrichSeriesWithTmdbCredits()
            Log.d(TAG, "TMDB metadata enrichment complete")
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message ?: ""
            if (msg.contains("503") || msg.contains("429")) {
                Log.w(TAG, "TMDB rate limited — will retry")
                return Result.retry()
            }
            Log.e(TAG, "TMDB metadata enrichment failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TmdbMetadataWorker"
        private const val WORK_NAME = "tmdb_metadata_enrichment"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TmdbMetadataWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            Log.d(TAG, "TMDB metadata enrichment enqueued")
        }
    }
}
