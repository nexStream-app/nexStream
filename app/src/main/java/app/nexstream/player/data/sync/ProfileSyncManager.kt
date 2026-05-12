package app.nexstream.player.data.sync

import android.util.Log
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.entity.ProfileCategoryFilter
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.license.LicencePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSyncManager @Inject constructor(
    private val dao: ProfileDao,
    private val licencePreferences: LicencePreferences
) {
    private val tag = "ProfileSync"
    private val baseUrl = "https://nexstream.uk/api/profiles.php"

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey()
        Log.d(tag, "getLicenceKey() = $key")
        if (key == null) Log.e(tag, "No licence key found in preferences")
        return key?.let { "Bearer $it" }
    }

    var onSyncComplete: (() -> Unit)? = null

    suspend fun sendFcmToken(token: String) = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext
        try {
            val body = JSONObject().apply { put("token", token) }.toString()
            postJson("https://nexstream.uk/api/fcm_tokens.php", auth, body)
            Log.d(tag, "FCM token sent to server")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send FCM token", e)
        }
    }

    suspend fun syncFromServer() = withContext(Dispatchers.IO) {
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
                    if (localDefault != null && localDefault.id != serverId) {
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