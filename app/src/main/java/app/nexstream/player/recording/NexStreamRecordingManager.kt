package app.nexstream.player.recording

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NexStreamRecordingManager {

    private const val PREFS     = "nexstream_recs"
    private const val NEXT_ID   = "next_id"
    private const val DL_PREFS  = "nexstream_downloads"
    private const val DL_PATH   = "storage_path"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Storage ───────────────────────────────────────────────────────────────

    private fun folder(ctx: Context, profileId: String): File {
        val custom = ctx.getSharedPreferences(DL_PREFS, Context.MODE_PRIVATE)
            .getString(DL_PATH, null)
        return if (!custom.isNullOrEmpty()) {
            File(custom, "NexStream/$profileId")
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NexStream/$profileId"
            )
        }.also { it.mkdirs() }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun startRecording(
        ctx: Context, url: String, title: String, profileId: String,
        posterUrl: String? = null, channelLogoUrl: String? = null,
        description: String? = null
    ): Long {
        val p    = prefs(ctx)
        val id   = p.getLong(NEXT_ID, 1L)
        val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date())
        val safe = title.replace(Regex("[^a-zA-Z0-9 ._-]"), "_").take(60)
        val file = File(folder(ctx, profileId), "${safe}_$date.ts")
        val edit = p.edit()
            .putLong(NEXT_ID, id + 1)
            .putString("title_$id",   title)
            .putString("url_$id",     url)
            .putString("path_$id",    file.absolutePath)
            .putLong("started_$id",   System.currentTimeMillis())
            .putBoolean("active_$id", true)
            .putString("profile_$id", profileId)
            .putLong("bytes_$id",     0L)
        if (posterUrl != null) edit.putString("poster_$id", posterUrl)
        if (channelLogoUrl != null) edit.putString("logo_$id", channelLogoUrl)
        if (!description.isNullOrBlank()) edit.putString("desc_$id", description)
        edit.apply()
        return id
    }

    fun getFilePath(ctx: Context, id: Long): String? = prefs(ctx).getString("path_$id", null)

    fun updateBytes(ctx: Context, id: Long, bytes: Long) {
        prefs(ctx).edit().putLong("bytes_$id", bytes).apply()
    }

    fun finalizeRecording(ctx: Context, id: Long) {
        prefs(ctx).edit().putBoolean("active_$id", false).apply()
    }

    fun deleteRecording(ctx: Context, id: Long) {
        prefs(ctx).getString("path_$id", null)?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        prefs(ctx).edit()
            .remove("title_$id").remove("url_$id").remove("path_$id")
            .remove("started_$id").remove("active_$id").remove("profile_$id")
            .remove("bytes_$id").remove("poster_$id").remove("logo_$id").remove("desc_$id")
            .apply()
    }

    fun deleteAllRecordings(ctx: Context, profileId: String) {
        val p = prefs(ctx)
        val ids = p.all.keys
            .filter { it.startsWith("title_") }
            .mapNotNull { key ->
                val id = key.removePrefix("title_").toLongOrNull() ?: return@mapNotNull null
                if ((p.getString("profile_$id", "default") ?: "default") == profileId) id else null
            }
        ids.forEach { id -> deleteRecording(ctx, id) }
    }

    fun findIdByUrl(ctx: Context, url: String): Long? {
        val p = prefs(ctx)
        return p.all.keys
            .filter { it.startsWith("url_") }
            .firstOrNull { key ->
                p.getString(key, null) == url &&
                    p.getBoolean("active_${key.removePrefix("url_")}", false)
            }
            ?.removePrefix("url_")?.toLongOrNull()
    }

    // ── Active URL set (for EPG red-dot) ─────────────────────────────────────

    fun getActiveUrls(ctx: Context): Set<String> {
        val p = prefs(ctx)
        return p.all.keys
            .filter { it.startsWith("active_") && p.getBoolean(it, false) }
            .mapNotNull { key ->
                val id = key.removePrefix("active_").toLongOrNull() ?: return@mapNotNull null
                p.getString("url_$id", null)
            }.toSet()
    }

    fun observeActiveUrls(ctx: Context): Flow<Set<String>> = flow {
        while (true) { emit(getActiveUrls(ctx)); delay(2000) }
    }.flowOn(Dispatchers.IO)

    // ── Recordings list ───────────────────────────────────────────────────────

    fun observeRecordings(ctx: Context, profileId: String): Flow<List<RecordingItem>> = flow {
        while (true) { emit(query(ctx, profileId)); delay(1000) }
    }.flowOn(Dispatchers.IO)

    private fun query(ctx: Context, profileId: String): List<RecordingItem> {
        val p = prefs(ctx)
        return p.all.keys
            .filter { it.startsWith("title_") }
            .mapNotNull { key ->
                val id = key.removePrefix("title_").toLongOrNull() ?: return@mapNotNull null
                if ((p.getString("profile_$id", "default") ?: "default") != profileId) return@mapNotNull null
                RecordingItem(
                    id             = id,
                    title          = p.getString("title_$id", "")  ?: "",
                    streamUrl      = p.getString("url_$id", "")    ?: "",
                    filePath       = p.getString("path_$id", "")   ?: "",
                    startedAt      = p.getLong("started_$id", 0L),
                    bytesWritten   = p.getLong("bytes_$id", 0L),
                    isActive       = p.getBoolean("active_$id", false),
                    profileId      = profileId,
                    posterUrl      = p.getString("poster_$id", null),
                    channelLogoUrl = p.getString("logo_$id", null),
                    description    = p.getString("desc_$id", null)
                )
            }
            .sortedByDescending { it.startedAt }
    }
}
