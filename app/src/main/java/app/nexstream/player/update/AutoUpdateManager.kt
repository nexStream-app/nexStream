package app.nexstream.player.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.nexstream.player.BuildConfig
import app.nexstream.player.ui.theme.getAutoUpdateEnabledFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object AutoUpdateManager {
    private const val TAG       = "AutoUpdate"
    private const val BUILD_URL = "https://nexstream.uk/build_number.txt"
    private const val AUTHORITY = "app.nexstream.player.fileprovider"

    private fun apkUrl(build: Int) =
        "https://github.com/nexStream-app/nexStream/releases/download/v0.0.$build/nexstream-build-$build.apk"

    suspend fun checkAndPrompt(activity: Activity) = withContext(Dispatchers.IO) {
        try {
            if (!activity.getAutoUpdateEnabledFlow().first()) {
                Log.d(TAG, "Auto-update disabled — skipping")
                return@withContext
            }

            val remoteStr = URL(BUILD_URL).readText(Charsets.UTF_8).trim()
            val remote    = remoteStr.toIntOrNull() ?: return@withContext
            val local     = BuildConfig.BUILD_NUMBER_INT

            Log.d(TAG, "Local build: $local, Remote build: $remote")
            if (remote <= local) return@withContext

            Log.d(TAG, "Update available — downloading build $remote")
            val apkFile = downloadApk(activity, apkUrl(remote))
            if (apkFile != null) {
                withContext(Dispatchers.Main) {
                    installApk(activity, apkFile)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        }
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

    private fun installApk(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, AUTHORITY, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
