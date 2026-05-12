import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.nexstream.player.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PlaylistRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val playlists = repository.getAllPlaylists().first()
            playlists.filter { it.type == "XTREAM" }.forEach { playlist ->
                repository.fetchAndStoreEPG(
                    playlist.xtreamHost ?: return@forEach,
                    playlist.xtreamUsername ?: return@forEach,
                    playlist.xtreamPassword ?: return@forEach
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}