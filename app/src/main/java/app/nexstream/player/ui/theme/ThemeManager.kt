package app.nexstream.player.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import app.nexstream.player.data.remote.ThemeApiService
import app.nexstream.player.license.LicencePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG             = "ThemeManager"
private const val PREFS_NAME      = "nexstream_theme"
private const val KEY_DARK_VER    = "theme_dark_version"
private const val KEY_LIGHT_VER   = "theme_light_version"
private const val FILE_DARK       = "theme-dark.json"
private const val FILE_LIGHT      = "theme-light.json"
private const val BASE_URL        = "https://nexstream.uk/api/"

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeApiService: ThemeApiService,
    private val licencePrefs: LicencePreferences,
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _darkTheme  = MutableStateFlow(ThemeDefaults.dark)
    private val _lightTheme = MutableStateFlow(ThemeDefaults.light)

    val darkTheme:  StateFlow<NexStreamTheme> = _darkTheme.asStateFlow()
    val lightTheme: StateFlow<NexStreamTheme> = _lightTheme.asStateFlow()

    /** Current theme based on system dark/light mode */
    fun currentTheme(): NexStreamTheme {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) _darkTheme.value else _lightTheme.value
    }

    // ─────────────────────────────────────────────────────────
    // Initialise — call from Application.onCreate()
    // ─────────────────────────────────────────────────────────

    fun init() {
        // 1. Load cached theme files immediately (fast, no network)
        loadFromCache()
        // 2. Fetch fresh theme from API in background
        scope.launch { fetchFromApi() }
    }

    // ─────────────────────────────────────────────────────────
    // Load from local cache
    // ─────────────────────────────────────────────────────────

    private fun loadFromCache() {
        loadCachedVariant(isDark = true)
        loadCachedVariant(isDark = false)
    }

    private fun loadCachedVariant(isDark: Boolean) {
        val filename = if (isDark) FILE_DARK else FILE_LIGHT
        val cacheFile = File(context.filesDir, "themes/$filename")
        Log.d("ThemeDebug", "loadCachedVariant isDark=$isDark file=${cacheFile.absolutePath} exists=${cacheFile.exists()}")
        if (cacheFile.exists()) {
            val json = cacheFile.readText()
            Log.d("ThemeDebug", "JSON mode field: ${org.json.JSONObject(json).optString("mode")}")
            val theme = ThemeParser.parse(json, context, isDark)
            if (theme != null) {
                if (isDark) _darkTheme.value = theme else _lightTheme.value = theme
                Log.d(TAG, "Loaded cached ${if (isDark) "dark" else "light"} theme v${theme.version}")
                return
            }
        }

        // Fall back to bundled asset
        loadBundledAsset(isDark)
    }

    private fun loadBundledAsset(isDark: Boolean) {
        try {
            val filename = if (isDark) "theme-dark-default.json" else "theme-light-default.json"
            val json     = context.assets.open("themes/$filename").bufferedReader().readText()
            val theme    = ThemeParser.parse(json, context, isDark)
            if (theme != null) {
                if (isDark) _darkTheme.value = theme else _lightTheme.value = theme
                Log.d(TAG, "Loaded bundled ${if (isDark) "dark" else "light"} theme")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bundled asset, using hardcoded defaults", e)
            // Already set to ThemeDefaults by field initialisation
        }
    }

    // ─────────────────────────────────────────────────────────
    // Fetch from API
    // ─────────────────────────────────────────────────────────

    private suspend fun fetchFromApi() {
        val licenceKey = licencePrefs.getLicenceKey() ?: run {
            Log.d(TAG, "No licence key — skipping theme fetch")
            return
        }

        fetchVariant(licenceKey, isDark = true)
        fetchVariant(licenceKey, isDark = false)
    }

    private suspend fun fetchVariant(licenceKey: String, isDark: Boolean) {
        val mode      = if (isDark) "dark" else "light"
        val cachedVer = prefs.getInt(if (isDark) KEY_DARK_VER else KEY_LIGHT_VER, 0)

        try {
            val response = themeApiService.getTheme(
                bearerKey = "Bearer $licenceKey",
                mode      = mode,
            )

            if (!response.isSuccessful) {
                Log.w(TAG, "Theme API returned ${response.code()} for $mode")
                return
            }

            val body = response.body() ?: return
            if (!body.success) return

            val serverVersion = body.version
            if (serverVersion <= cachedVer) {
                Log.d(TAG, "$mode theme already up to date (v$cachedVer)")
                return
            }

            // Re-fetch as raw JSON for ThemeParser (Retrofit Map<String,Any> loses type info)
            val rawJson = fetchRawJson(licenceKey, mode) ?: return
            val theme   = ThemeParser.parse(rawJson, context, isDark, serverVersion) ?: return

            // Cache to disk
            withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "themes")
                dir.mkdirs()
                File(dir, if (isDark) FILE_DARK else FILE_LIGHT).writeText(rawJson)
            }

            // Persist version
            prefs.edit()
                .putInt(if (isDark) KEY_DARK_VER else KEY_LIGHT_VER, serverVersion)
                .apply()

            // Update live state
            if (isDark) _darkTheme.value = theme else _lightTheme.value = theme

            // Download custom font assets if URLs are present
            downloadFontIfNeeded(theme)

            Log.d(TAG, "Updated $mode theme to v$serverVersion")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch $mode theme", e)
        }
    }

    private suspend fun fetchRawJson(licenceKey: String, mode: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${BASE_URL}theme.php?mode=$mode")
                    .header("Authorization", "Bearer $licenceKey")
                    .build()
                val resp = httpClient.newCall(request).execute()
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: return@withContext null
                    // Extract the "theme" object from the wrapper response
                    val wrapper = JSONObject(bodyStr)
                    wrapper.optJSONObject("theme")?.toString()
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Raw JSON fetch failed", e)
                null
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Font download
    // ─────────────────────────────────────────────────────────

    private suspend fun downloadFontIfNeeded(theme: NexStreamTheme) {
        listOfNotNull(theme.identity.fontRegularUrl, theme.identity.fontBoldUrl).forEach { url ->
            val dest = ThemeParser.cachedFontFile(context, url)
            if (dest.exists()) return@forEach
            withContext(Dispatchers.IO) {
                try {
                    dest.parentFile?.mkdirs()
                    val request = Request.Builder().url(url).build()
                    val resp    = httpClient.newCall(request).execute()
                    resp.body?.byteStream()?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.d(TAG, "Downloaded font: $url")
                } catch (e: Exception) {
                    Log.w(TAG, "Font download failed: $url", e)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Force refresh (e.g. after licence activation)
    // ─────────────────────────────────────────────────────────

    fun refresh() {
        prefs.edit().remove(KEY_DARK_VER).remove(KEY_LIGHT_VER).apply()
        scope.launch { fetchFromApi() }
    }
}