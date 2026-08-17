package app.nexstream.player.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private const val KEY_STORAGE_PATH = "storage_path"
    private const val KEY_STORAGE_LABEL = "storage_label"
    private const val KEY_RECORDING_PREFIX = "rec_"
    private const val KEY_REC_URL_PREFIX = "rec_url_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private const val MIN_FREE_BYTES = 250L * 1024 * 1024  // 250 MB

    // ── Storage location ──────────────────────────────────────────────────────

    fun setDownloadsLocation(context: Context, path: String) {
        prefs(context).edit().putString(KEY_STORAGE_PATH, path).apply()
    }

    fun getDownloadsLocation(context: Context): String =
        prefs(context).getString(KEY_STORAGE_PATH, null) ?: ""

    fun setDownloadsLabel(context: Context, label: String) {
        prefs(context).edit().putString(KEY_STORAGE_LABEL, label).apply()
    }

    fun getDownloadsLabel(context: Context): String =
        prefs(context).getString(KEY_STORAGE_LABEL, null) ?: "Internal Storage"

    fun getPathFromTreeUri(treeUri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val volumeId = split.getOrNull(0) ?: return null
            val subPath = split.getOrElse(1) { "" }
            val base = when (volumeId) {
                "primary" -> Environment.getExternalStorageDirectory().absolutePath
                else -> "/storage/$volumeId"
            }
            if (subPath.isEmpty()) base else "$base/$subPath"
        } catch (_: Exception) { null }
    }

    fun getFreeBytes(context: Context): Long {
        val path = getDownloadsLocation(context)
        val dir = if (path.isNotEmpty()) File(path)
                  else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return try { StatFs(dir.absolutePath).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
    }

    fun hasEnoughSpace(context: Context): Boolean = getFreeBytes(context) >= MIN_FREE_BYTES

    // ── Poster URLs ───────────────────────────────────────────────────────────

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

    // ── Recording tags ────────────────────────────────────────────────────────

    fun markAsRecording(context: Context, downloadId: Long, streamUrl: String? = null) {
        prefs(context).edit()
            .putBoolean("$KEY_RECORDING_PREFIX$downloadId", true)
            .also { if (streamUrl != null) it.putString("$KEY_REC_URL_PREFIX$downloadId", streamUrl) }
            .apply()
    }

    fun isRecording(context: Context, downloadId: Long): Boolean =
        prefs(context).getBoolean("$KEY_RECORDING_PREFIX$downloadId", false)

    fun getRecordingStreamUrl(context: Context, downloadId: Long): String? =
        prefs(context).getString("$KEY_REC_URL_PREFIX$downloadId", null)

    fun getActiveRecordingUrls(context: Context): Set<String> {
        val p = prefs(context)
        return p.all.keys
            .filter { it.startsWith(KEY_RECORDING_PREFIX) }
            .mapNotNull { key ->
                val id = key.removePrefix(KEY_RECORDING_PREFIX).toLongOrNull() ?: return@mapNotNull null
                p.getString("$KEY_REC_URL_PREFIX$id", null)
            }
            .toSet()
    }

    fun observeActiveRecordingUrls(context: Context): Flow<Set<String>> = flow {
        while (true) {
            emit(getActiveRecordingUrls(context))
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    fun stopRecording(context: Context, downloadId: Long) {
        prefs(context).edit()
            .remove("$KEY_RECORDING_PREFIX$downloadId")
            .remove("$KEY_REC_URL_PREFIX$downloadId")
            .apply()
    }

    fun findDownloadIdByUrl(context: Context, streamUrl: String): Long? {
        val p = prefs(context)
        return p.all.keys
            .filter { it.startsWith(KEY_REC_URL_PREFIX) }
            .firstOrNull { key -> p.getString(key, null) == streamUrl }
            ?.removePrefix(KEY_REC_URL_PREFIX)
            ?.toLongOrNull()
    }

    private fun clearRecordingTag(context: Context, downloadId: Long) {
        prefs(context).edit()
            .remove("$KEY_RECORDING_PREFIX$downloadId")
            .remove("$KEY_REC_URL_PREFIX$downloadId")
            .apply()
    }

    // ── Downloads ─────────────────────────────────────────────────────────────

    fun startDownload(context: Context, streamUrl: String, title: String, profileId: String = "default"): Long {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 ._-]"), "_").take(80)
        val fileName = "$safeTitle.mp4"
        val subDir = "$NEXSTREAM_FOLDER/$profileId"
        val customPath = getDownloadsLocation(context)

        val request = DownloadManager.Request(Uri.parse(streamUrl)).apply {
            setTitle(title)
            setDescription("Downloading via NexStream")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (customPath.isNotEmpty()) {
                setDestinationUri(Uri.fromFile(File(customPath, "$subDir/$fileName")))
            } else {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$subDir/$fileName")
            }
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            addRequestHeader("User-Agent", "VLC/3.0.0 LibVLC/3.0.0")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun startDownload(context: Context, streamUrl: String, title: String, posterUrl: String?, profileId: String = "default"): Long {
        val downloadId = startDownload(context, streamUrl, title, profileId)
        savePosterUrl(context, downloadId, posterUrl)
        return downloadId
    }

    // ── Recordings ────────────────────────────────────────────────────────────

    fun startRecording(context: Context, streamUrl: String, title: String, profileId: String = "default"): Long {
        val date = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
        val titledWithDate = "$title-$date"
        val downloadId = startDownload(context, streamUrl, titledWithDate, profileId)
        markAsRecording(context, downloadId, streamUrl)
        return downloadId
    }

    fun observeRecordings(context: Context, profileId: String = "default"): Flow<List<DownloadItem>> =
        observeDownloads(context, profileId).map { items -> items.filter { isRecording(context, it.downloadId) } }

    fun cancelDownload(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
    }

    fun deleteDownload(context: Context, downloadId: Long, filePath: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        try { File(filePath).delete() } catch (_: Exception) {}
        deletePosterUrl(context, downloadId)
        clearRecordingTag(context, downloadId)
    }

    // Polls Android DownloadManager for progress — emits every second while any download is active
    fun observeDownloads(context: Context, profileId: String = "default"): Flow<List<DownloadItem>> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            emit(queryAllDownloads(context, dm, profileId))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    private fun queryAllDownloads(context: Context, dm: DownloadManager, profileId: String = "default"): List<DownloadItem> {
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
                if (filePath.contains("NexStream/$profileId", ignoreCase = true)) {
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

    fun getDownloadsFolder(profileId: String = "default"): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$NEXSTREAM_FOLDER/$profileId")
            .also { it.mkdirs() }

    fun deleteAllDownloads(context: Context, profileId: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val cursor = dm.query(query)
        cursor?.use { c ->
            val idCol    = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val localCol = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (c.moveToNext()) {
                val id       = c.getLong(idCol)
                val localUri = c.getString(localCol) ?: ""
                if (localUri.contains("NexStream/$profileId", ignoreCase = true)) {
                    dm.remove(id)
                    deletePosterUrl(context, id)
                    clearRecordingTag(context, id)
                }
            }
        }
        val customPath = getDownloadsLocation(context)
        val folder = if (customPath.isNotEmpty()) {
            File(customPath, "$NEXSTREAM_FOLDER/$profileId")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$NEXSTREAM_FOLDER/$profileId")
        }
        if (folder.exists()) {
            folder.listFiles()?.forEach { try { it.delete() } catch (_: Exception) {} }
        }
    }
}