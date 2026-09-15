package app.nexstream.player.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.nexstream.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object AutoUpdateManager {
    private const val TAG       = "AutoUpdate"
    private const val BUILD_URL = "https://nexstream.uk/build_number.txt"
    private const val AUTHORITY = "app.nexstream.player.fileprovider"

    private fun apkUrl(@Suppress("UNUSED_PARAMETER") build: Int) =
        "https://github.com/nexStream-app/nexStream/releases/latest/download/nexStream.apk"

    suspend fun checkNow(context: Context): String = withContext(Dispatchers.IO) {
        val remoteStr = URL(BUILD_URL).readText(Charsets.UTF_8).trim()
        val remote    = remoteStr.toIntOrNull() ?: return@withContext "Could not read version info"
        val local     = BuildConfig.BUILD_NUMBER_INT
        Log.d(TAG, "Local build: $local, Remote build: $remote")
        if (remote <= local) return@withContext "Already up to date (build $local)"
        Log.d(TAG, "Update available — downloading build $remote")
        val apkFile = downloadApk(context, apkUrl(remote)) ?: return@withContext "Download failed"
        withContext(Dispatchers.Main) { installApk(context, apkFile) }
        "Updating to build $remote…"
    }

    private fun downloadApk(context: Context, url: String): File? {
        return try {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val out = File(dir, "nexStream_update.apk")
            URL(url).openStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "APK downloaded to ${out.absolutePath} (${out.length()} bytes)")
            out
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    private fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
