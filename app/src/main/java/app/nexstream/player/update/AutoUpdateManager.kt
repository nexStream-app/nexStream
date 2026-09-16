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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object AutoUpdateManager {
    private const val TAG          = "AutoUpdate"
    private const val RELEASES_URL = "https://api.github.com/repos/nexStream-app/nexStream/releases/latest"
    private const val APK_URL      = "https://github.com/nexStream-app/nexStream/releases/latest/download/nexStream.apk"
    private const val AUTHORITY    = "app.nexstream.player.fileprovider"

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
                .header("Accept", "application/vnd.github+json")
                .build()
        ).execute()
        val body = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return body.trim()
    }

    private fun fetchRemoteBuild(): Int {
        val json = JSONObject(get(RELEASES_URL))
        val name = json.optString("name", "")
        val match = Regex("""\(Build (\d+)\)""").find(name)
            ?: throw Exception("Could not parse build from release title: $name")
        return match.groupValues[1].toInt()
    }

    suspend fun checkNow(
        context: Context,
        onStatus: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        suspend fun status(msg: String) {
            onStatus?.let { withContext(Dispatchers.Main) { it(msg) } }
        }

        status("Checking for update…")

        val remote = try {
            fetchRemoteBuild()
        } catch (e: Exception) {
            Log.e(TAG, "Version check failed", e)
            return@withContext "Could not check for updates"
        }

        val local = BuildConfig.BUILD_NUMBER_INT
        Log.d(TAG, "Local build: $local, Remote build: $remote")

        if (remote <= local) return@withContext "On the latest version (Build $local)"

        Log.d(TAG, "Update available — downloading Build $remote")
        status("Downloading Build $remote…")

        val apkFile = downloadApk(context, APK_URL)
            ?: return@withContext "Download failed"

        try {
            withContext(Dispatchers.Main) { installApk(context, apkFile) }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            return@withContext "Download complete — open Files app to install nexStream_update.apk"
        }
        "Installing Build $remote…"
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
