package app.nexstream.player.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class DownloadItem(
    val downloadId: Long,
    val title: String,
    val fileName: String,
    val filePath: String,
    val status: DownloadStatus,
    val progressPercent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val startedAt: Long,
    val posterUrl: String? = null
)

enum class DownloadStatus { PENDING, RUNNING, PAUSED, FAILED, COMPLETED }

object NexStreamDownloadManager {

    private const val NEXSTREAM_FOLDER = "NexStream"
    private const val PREFS_NAME = "nexstream_downloads"

    // Store poster URLs keyed by download ID since DownloadManager has no custom data field
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    fun savePosterUrl(context: Context, downloadId: Long, posterUrl: String?) {
        if (!posterUrl.isNullOrBlank()) {
            prefs(context).edit().putString("poster_$downloadId", posterUrl).apply()
        }
    }

    fun getPosterUrl(context: Context, downloadId: Long): String? =
        prefs(context).getString("poster_$downloadId", null)

    fun deletePosterUrl(context: Context, downloadId: Long) {
        prefs(context).edit().remove("poster_$downloadId").apply()
    }

    fun startDownload(context: Context, streamUrl: String, title: String): Long {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 ._-]"), "_").take(80)
        val fileName = "$safeTitle.mp4"

        val request = DownloadManager.Request(Uri.parse(streamUrl)).apply {
            setTitle(title)
            setDescription("Downloading via NexStream")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$NEXSTREAM_FOLDER/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            addRequestHeader("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun startDownload(context: Context, streamUrl: String, title: String, posterUrl: String?): Long {
        val downloadId = startDownload(context, streamUrl, title)
        savePosterUrl(context, downloadId, posterUrl)
        return downloadId
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    fun deleteDownload(context: Context, downloadId: Long, filePath: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        try { File(filePath).delete() } catch (_: Exception) {}
        deletePosterUrl(context, downloadId)
    }

    // Polls Android DownloadManager for progress — emits every second while any download is active
    fun observeDownloads(context: Context): Flow<List<DownloadItem>> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            emit(queryAllDownloads(context, dm))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private fun queryAllDownloads(context: Context, dm: DownloadManager): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        val query = DownloadManager.Query()
        val cursor = dm.query(query) ?: return items

        cursor.use { c ->
            val idCol       = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol    = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol   = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesCol    = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol    = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localCol    = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val modifiedCol = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)

            while (c.moveToNext()) {
                val id           = c.getLong(idCol)
                val title        = c.getString(titleCol) ?: "Unknown"
                val statusInt    = c.getInt(statusCol)
                val downloaded   = c.getLong(bytesCol)
                val total        = c.getLong(totalCol)
                val localUri     = c.getString(localCol) ?: ""
                val modified     = c.getLong(modifiedCol)
                // DownloadManager returns "file:///path" — strip scheme to get clean path
                val filePath     = when {
                    localUri.startsWith("file:///") -> localUri.removePrefix("file://")
                    localUri.startsWith("file://")  -> localUri.removePrefix("file://")
                    else                            -> localUri
                }
                val rawFileName  = filePath.substringAfterLast("/")
                val fileName     = try {
                    java.net.URLDecoder.decode(rawFileName, "UTF-8")
                } catch (_: Exception) { rawFileName }
                val progress     = if (total > 0) ((downloaded * 100) / total).toInt() else 0

                val status = when (statusInt) {
                    DownloadManager.STATUS_PENDING    -> DownloadStatus.PENDING
                    DownloadManager.STATUS_RUNNING    -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_PAUSED     -> DownloadStatus.PAUSED
                    DownloadManager.STATUS_FAILED     -> DownloadStatus.FAILED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                    else                              -> DownloadStatus.FAILED
                }

                // Only include downloads in the NexStream folder
                if (filePath.contains("NexStream", ignoreCase = true) || title.isNotEmpty()) {
                    items.add(DownloadItem(
                        downloadId       = id,
                        title            = title,
                        fileName         = fileName,
                        filePath         = filePath,
                        status           = status,
                        progressPercent  = progress,
                        downloadedBytes  = downloaded,
                        totalBytes       = total,
                        startedAt        = modified,
                        posterUrl        = getPosterUrl(context, id)
                    ))
                }
            }
        }

        return items.sortedByDescending { it.startedAt }
    }

    fun getDownloadsFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), NEXSTREAM_FOLDER)
            .also { it.mkdirs() }
}