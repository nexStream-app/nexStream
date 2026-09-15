package app.nexstream.player.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.nexstream.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object AutoUpdateManager {
    private const val TAG       = "AutoUpdate"
    private const val BUILD_URL = "https://nexstream.uk/build_number.txt"
    private const val AUTHORITY = "app.nexstream.player.fileprovider"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun get(url: String): String {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", "NexStream/${BuildConfig.BUILD_NUMBER} Update-Client")
                .build()
        ).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return body.trim()
    }

    private fun apkUrl(@Suppress("UNUSED_PARAMETER") build: Int) =
        "https://github.com/nexStream-app/nexStream/releases/latest/download/nexStream.apk"

    suspend fun checkNow(context: Context): String = withContext(Dispatchers.IO) {
        val remoteStr = get(BUILD_URL)
        val remote    = remoteStr.toIntOrNull() ?: return@withContext "Could not read version info (got: ${remoteStr.take(80)})"
        val local     = BuildConfig.BUILD_NUMBER_INT
        Log.d(TAG, "Local build: $local, Remote build: $remote")
        if (remote <= local) return@withContext "Already up to date (build $local)"
        Log.d(TAG, "Update available — downloading build $remote")
        val apkFile = downloadApk(context, apkUrl(remote))
            ?: return@withContext "Download failed"
        try {
            withContext(Dispatchers.Main) { installApk(context, apkFile) }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            return@withContext "Download complete — open Files app to install nexStream_update.apk from cache/updates/"
        }
        "Installing build $remote…"
    }

    private fun downloadApk(context: Context, url: String): File? {
        return try {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val out = File(dir, "nexStream_update.apk")
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "NexStream/${BuildConfig.BUILD_NUMBER} Update-Client")
                    .build()
            ).execute()
            response.body?.byteStream()?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Empty body")
            response.close()
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
