package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.applySyncedPlayerPrefs
import app.nexstream.player.ui.theme.applySyncedThemePrefs
import app.nexstream.player.ui.theme.collectSyncablePlayerPrefs
import app.nexstream.player.ui.theme.collectSyncableThemePrefs
import app.nexstream.player.ui.theme.getCloudSyncEnabledFlow
import app.nexstream.player.ui.theme.playerPrefsChanges
import app.nexstream.player.ui.theme.themePrefsChanges
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Singleton
class SettingsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val licencePreferences: LicencePreferences,
) {
    private val tag = "SettingsSync"
    private val baseUrl = "https://nexstream.uk/api/settings-sync.php"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Prevents push-loops after a pull applies settings to DataStore
    private val applyingPull = AtomicBoolean(false)

    init {
        syncScope.launch {
            merge(
                context.playerPrefsChanges().drop(1).map { Unit },
                context.themePrefsChanges().drop(1),
            )
            .debounce(3_000L)
            .collect {
                if (!applyingPull.get() && context.getCloudSyncEnabledFlow().first()) {
                    pushSettings()
                }
            }
        }
    }

    fun enqueuePush() {
        syncScope.launch { pushSettings() }
    }

    suspend fun pushSettings() = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            val json = buildSyncJson()
            if (json.length() == 0) return@withContext  // nothing explicitly set — don't overwrite server
            postJson(baseUrl, auth, json.toString())
            Log.d(tag, "Settings pushed to server")
        } catch (e: Exception) {
            Log.e(tag, "Push settings failed", e)
        }
    }

    suspend fun syncFromServer() = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            val raw = fetchGet(baseUrl, auth) ?: return@withContext
            val root = JSONObject(raw)
            if (!root.optBoolean("success")) return@withContext
            val settingsJson = root.optJSONObject("settings") ?: return@withContext
            val settings = settingsJson.toMap()
            applyingPull.set(true)
            context.applySyncedPlayerPrefs(settings)
            context.applySyncedThemePrefs(settings)
            // Hold the flag past the debounce window so the DataStore changes don't re-trigger a push
            syncScope.launch { delay(4_000L); applyingPull.set(false) }
            Log.d(tag, "Settings synced from server")
        } catch (e: Exception) {
            applyingPull.set(false)
            Log.e(tag, "Sync settings failed", e)
        }
    }

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey()
        return key?.let { "Bearer $it" }
    }

    private suspend fun buildSyncJson(): JSONObject {
        val playerPrefs = context.collectSyncablePlayerPrefs()
        val themePrefs  = context.collectSyncableThemePrefs()
        val json = JSONObject()
        playerPrefs.forEach { (k, v) -> json.put(k, v) }
        themePrefs.forEach  { (k, v) -> json.put(k, v) }
        return json
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { k ->
            map[k] = when (val v = opt(k)) {
                JSONObject.NULL, null -> null
                else -> v
            }
        }
        return map
    }

    private fun fetchGet(url: String, auth: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Content-Type", "application/json")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) {
            Log.e(tag, "fetchGet failed", e)
            null
        }
    }

    private fun postJson(url: String, auth: String, body: String) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", auth)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.bufferedWriter().use { it.write(body) }
        conn.responseCode
        conn.disconnect()
    }
}
