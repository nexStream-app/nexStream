package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.dao.ProfileAppearanceDao
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.entity.ProfileAppearanceEntity
import app.nexstream.player.data.local.entity.ProfileCategoryFilter
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.getCloudSyncEnabledFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSyncManager @Inject constructor(
    private val dao: ProfileDao,
    private val profileAppearanceDao: ProfileAppearanceDao,
    private val licencePreferences: LicencePreferences,
    @ApplicationContext private val context: Context,
) {
    private val tag = "ProfileSync"
    private val baseUrl = "https://nexstream.uk/api/profiles.php"

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey()
        Log.d(tag, "authKey = $key")
        if (key == null) Log.e(tag, "No licence key or trial sync key found in preferences")
        return key?.let { "Bearer $it" }
    }

    var onSyncComplete: (() -> Unit)? = null

    suspend fun sendFcmToken(token: String, deviceId: String? = null) = withContext(Dispatchers.IO) {
        // Always resolve deviceId so the server can upgrade trial rows to licensed rows.
        // FCM registration uses only the real licence key — trial devices fall through to
        // the device_id-only path on the server.
        val resolvedDeviceId = deviceId ?: licencePreferences.getOrCreateStableDeviceId()
        val auth = licencePreferences.getLicenceKey()?.let { "Bearer $it" }
        if (auth == null && resolvedDeviceId.isEmpty()) {
            Log.w(tag, "No licence key and no device_id — skipping FCM token registration")
            return@withContext
        }
        try {
            val body = JSONObject().apply {
                put("token", token)
                put("device_id", resolvedDeviceId)
            }.toString()
            postJson("https://nexstream.uk/api/fcm_tokens.php", auth, body)
            Log.d(tag, "FCM token sent to server (deviceId=$resolvedDeviceId, hasAuth=${auth != null})")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send FCM token", e)
        }
    }

    suspend fun syncFromServer() = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        Log.d(tag, "syncFromServer() called")

        val auth = authHeader() ?: run {
            Log.e(tag, "SYNC ABORTED: no auth header")
            return@withContext
        }
        Log.d(tag, "Auth header ready")

        try {
            Log.d(tag, "Fetching from $baseUrl")
            val raw = fetchGet(baseUrl, auth)
            if (raw == null) {
                Log.e(tag, "SYNC ABORTED: null response from server")
                return@withContext
            }
            Log.d(tag, "Raw response: $raw")

            val json = JSONObject(raw)
            val success = json.optBoolean("success")
            Log.d(tag, "success flag = $success")

            if (!success) {
                Log.e(tag, "SYNC ABORTED: success=false")
                return@withContext
            }

            val profiles = json.optJSONArray("profiles")
            Log.d(tag, "profiles array = $profiles")

            if (profiles == null) {
                Log.e(tag, "SYNC ABORTED: no profiles array in response")
                return@withContext
            }

            Log.d(tag, "Profile count from server: ${profiles.length()}")

            val serverIds = (0 until profiles.length()).map {
                profiles.getJSONObject(it).getString("id")
            }.toSet()

            for (i in 0 until profiles.length()) {
                val p            = profiles.getJSONObject(i)
                val serverId     = p.getString("id")
                val isDefault    = p.optBoolean("is_default", false)
                val isRestricted = p.optBoolean("is_restricted", false)
                val serverTime   = p.optLong("updated_at", System.currentTimeMillis())

                Log.d(tag, "Processing profile: ${p.getString("name")} ($serverId) default=$isDefault restricted=$isRestricted")

                if (isDefault) {
                    val localDefault = dao.getDefaultProfile()
                    // Only delete the local default if it's not one of the profiles the server
                    // sent — i.e. it's an auto-generated fresh-install placeholder.
                    if (localDefault != null && localDefault.id != serverId && localDefault.id !in serverIds) {
                        Log.d(tag, "Replacing stale local default '${localDefault.name}' (${localDefault.id}) with server default '${p.getString("name")}' ($serverId)")
                        dao.forceDeleteProfile(localDefault.id)
                        dao.deleteFiltersForProfile(localDefault.id)
                    }
                }

                val entity = ProfileEntity(
                    id           = serverId,
                    name         = p.getString("name"),
                    emoji        = p.getString("emoji"),
                    pinHash      = p.optString("pin_hash").ifEmpty { null },
                    isDefault    = isDefault,
                    isRestricted = isRestricted,
                    sortOrder    = p.optInt("sort_order", 0),
                    updatedAt    = serverTime
                )
                dao.upsertProfile(entity)
                dao.deleteFiltersForProfile(entity.id)

                // Sync appearance — isolated try-catch so a bad value can't abort the profile loop
                try {
                    val appearance = p.optJSONObject("appearance")
                    if (appearance != null) {
                        profileAppearanceDao.upsertAppearance(ProfileAppearanceEntity(
                            profileId          = serverId,
                            themeMode          = appearance.optString("theme_mode", "DARK"),
                            fontScale          = if (appearance.has("font_scale") && !appearance.isNull("font_scale")) appearance.getDouble("font_scale").toFloat() else null,
                            fontWeight         = appearance.optString("font_weight").takeIf { it.isNotEmpty() },
                            uiStyle            = appearance.optString("ui_style", "CLASSIC"),
                            tvAspectRatio      = appearance.optString("tv_aspect_ratio", "FILL"),
                            movieAspectRatio   = appearance.optString("movie_aspect_ratio", "FIT"),
                            seriesAspectRatio  = appearance.optString("series_aspect_ratio", "FIT"),
                            epgMiniPlayer      = appearance.optBoolean("epg_mini_player", true),
                            keyboardFontScale  = appearance.optDouble("keyboard_font_scale", 1.0).toFloat(),
                            updatedAt          = serverTime
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Appearance sync failed for profile $serverId — skipping", e)
                }

                val filters = p.optJSONArray("filters") ?: continue
                val filterEntities = mutableListOf<ProfileCategoryFilter>()
                for (j in 0 until filters.length()) {
                    val f = filters.getJSONObject(j)
                    filterEntities.add(ProfileCategoryFilter(
                        profileId    = entity.id,
                        categoryType = f.getString("category_type"),
                        categoryName = f.getString("category_name"),
                        isAllowed    = f.optInt("is_allowed", 1) != 0,
                        updatedAt    = f.optLong("updated_at", System.currentTimeMillis())
                    ))
                }
                Log.d(tag, "Profile ${entity.name}: ${filterEntities.size} filters from server")
                if (filterEntities.isNotEmpty()) dao.upsertFilters(filterEntities)
            }

            val allLocal = dao.getAllProfilesOnce()
            allLocal.filter { !it.isDefault && it.id !in serverIds }.forEach { stale ->
                Log.d(tag, "Removing stale local profile '${stale.name}' (${stale.id})")
                dao.forceDeleteProfile(stale.id)
                dao.deleteFiltersForProfile(stale.id)
            }

            Log.d(tag, "Sync complete — ${profiles.length()} profiles synced from server")
            onSyncComplete?.invoke()

        } catch (e: Exception) {
            Log.e(tag, "Sync from server failed", e)
        }
    }

    suspend fun pushProfiles(
        tvCategories: List<String> = emptyList(),
        movieCategories: List<String> = emptyList(),
        seriesCategories: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        Log.d(tag, "pushProfiles called")
        try {
            val profiles = dao.getAllProfilesOnce()
            val profilesArray = JSONArray()
            profiles.forEach { profile ->
                val filters = dao.getFiltersForProfileOnce(profile.id)
                val filtersArray = JSONArray()
                filters.forEach { f ->
                    filtersArray.put(JSONObject().apply {
                        put("category_type", f.categoryType)
                        put("category_name", f.categoryName)
                        put("is_allowed", f.isAllowed)
                        put("updated_at", f.updatedAt)
                    })
                }
                val appearance = profileAppearanceDao.getAppearance(profile.id)
                val appearanceObj = JSONObject().apply {
                    put("theme_mode",          appearance?.themeMode ?: "DARK")
                    put("font_scale",          appearance?.fontScale)
                    put("font_weight",         appearance?.fontWeight ?: "")
                    put("ui_style",            appearance?.uiStyle ?: "CLASSIC")
                    put("tv_aspect_ratio",     appearance?.tvAspectRatio ?: "FILL")
                    put("movie_aspect_ratio",  appearance?.movieAspectRatio ?: "FIT")
                    put("series_aspect_ratio", appearance?.seriesAspectRatio ?: "FIT")
                    put("epg_mini_player",     appearance?.epgMiniPlayer ?: true)
                    put("keyboard_font_scale", appearance?.keyboardFontScale ?: 1.0f)
                }
                profilesArray.put(JSONObject().apply {
                    put("id",            profile.id)
                    put("name",          profile.name)
                    put("emoji",         profile.emoji)
                    put("pin_hash",      profile.pinHash ?: "")
                    put("is_default",    profile.isDefault)
                    put("is_restricted", profile.isRestricted)
                    put("sort_order",    profile.sortOrder)
                    put("updated_at",    profile.updatedAt)
                    put("filters",       filtersArray)
                    put("appearance",    appearanceObj)
                })
            }
            val catsObj = JSONObject().apply {
                put("TV",     JSONArray(tvCategories))
                put("MOVIE",  JSONArray(movieCategories))
                put("SERIES", JSONArray(seriesCategories))
            }
            val body = JSONObject().apply {
                put("profiles",   profilesArray)
                put("categories", catsObj)
            }.toString()
            postJson(baseUrl, auth, body)
            Log.d(tag, "Pushed ${profiles.size} profiles to server")
        } catch (e: Exception) {
            Log.e(tag, "Push profiles failed", e)
        }
    }

    private fun fetchGet(url: String, auth: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Content-Type", "application/json")
            val code = conn.responseCode
            Log.d(tag, "GET $url → HTTP $code")
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                Log.d(tag, "Response: $response")
                response
            } else {
                Log.e(tag, "Non-200 response: $code")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchGet failed", e)
            null
        }
    }

    private fun postJson(url: String, auth: String?, body: String) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        if (auth != null) conn.setRequestProperty("Authorization", auth)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.bufferedWriter().use { it.write(body) }
        conn.responseCode
        conn.disconnect()
    }
}