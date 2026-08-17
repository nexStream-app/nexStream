package app.nexstream.player.data.sync

import android.content.Context
import android.util.Log
import app.nexstream.player.data.local.dao.ChannelGroupDao
import app.nexstream.player.data.local.entity.ChannelGroupEntity
import app.nexstream.player.data.local.entity.ChannelGroupMemberEntity
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.getCloudSyncEnabledFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelGroupSyncManager @Inject constructor(
    private val channelGroupDao: ChannelGroupDao,
    private val licencePreferences: LicencePreferences,
    @ApplicationContext private val context: Context,
) {
    private val tag = "ChannelGroupSync"
    private val baseUrl = "https://nexstream.uk/api/channel_groups.php"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingDeletes: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun enqueuePush() { syncScope.launch { pushGroups() } }

    fun enqueueDelete(groupId: String) {
        pendingDeletes.add(groupId)
        enqueuePush()
    }

    private fun authHeader(): String? {
        val key = licencePreferences.getLicenceKey() ?: licencePreferences.getTrialSyncKey()
        return key?.let { "Bearer $it" }
    }

    suspend fun pushGroups() = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            val allGroups = channelGroupDao.getAllGroupsOnce()
            val groupsArray = JSONArray()
            for (group in allGroups) {
                val members = channelGroupDao.getMembersForGroup(group.id)
                val membersArray = JSONArray()
                for (m in members) {
                    membersArray.put(JSONObject().apply {
                        put("channel_id",   m.channelId)
                        put("channel_name", m.channelName)
                        put("logo_url",     m.logoUrl ?: "")
                        put("sort_order",   m.sortOrder)
                    })
                }
                groupsArray.put(JSONObject().apply {
                    put("id",          group.id)
                    put("profile_id",  group.profileId)
                    put("name",        group.name)
                    put("sort_order",  group.sortOrder)
                    put("is_expanded", group.isExpanded)
                    put("updated_at",  group.updatedAt)
                    put("members",     membersArray)
                })
            }
            val deletesToSend = pendingDeletes.toList()
            val deletedIdsArray = JSONArray()
            for (id in deletesToSend) deletedIdsArray.put(id)

            val body = JSONObject().apply {
                put("groups", groupsArray)
                put("deleted_ids", deletedIdsArray)
            }.toString()
            postJson(baseUrl, auth, body)
            pendingDeletes.removeAll(deletesToSend.toSet())
            Log.d(tag, "Pushed ${allGroups.size} groups, ${deletesToSend.size} deletes to server")
        } catch (e: Exception) {
            Log.e(tag, "Push groups failed", e)
        }
    }

    suspend fun syncFromServer() = withContext(Dispatchers.IO) {
        if (!context.getCloudSyncEnabledFlow().first()) return@withContext
        val auth = authHeader() ?: return@withContext
        try {
            val raw = fetchGet(baseUrl, auth) ?: return@withContext
            val json = JSONObject(raw)
            if (!json.optBoolean("success")) return@withContext

            val groups = json.optJSONArray("groups") ?: return@withContext
            val serverGroupIds = mutableSetOf<String>()

            for (i in 0 until groups.length()) {
                val g = groups.getJSONObject(i)
                val profileId = g.getString("profile_id")
                if (profileId.isBlank()) continue

                val group = ChannelGroupEntity(
                    id         = g.getString("id"),
                    profileId  = profileId,
                    name       = g.getString("name"),
                    sortOrder  = g.optInt("sort_order", 0),
                    isExpanded = g.optBoolean("is_expanded", true),
                    updatedAt  = g.optLong("updated_at", System.currentTimeMillis())
                )
                serverGroupIds.add(group.id)
                channelGroupDao.upsertGroup(group)
                channelGroupDao.deleteMembersForGroup(group.id)

                val members = g.optJSONArray("members") ?: continue
                val memberEntities = mutableListOf<ChannelGroupMemberEntity>()
                for (j in 0 until members.length()) {
                    val m = members.getJSONObject(j)
                    memberEntities.add(ChannelGroupMemberEntity(
                        groupId     = group.id,
                        channelId   = m.getString("channel_id"),
                        channelName = m.getString("channel_name"),
                        logoUrl     = m.optString("logo_url").takeIf { it.isNotEmpty() },
                        sortOrder   = m.optInt("sort_order", 0)
                    ))
                }
                if (memberEntities.isNotEmpty()) channelGroupDao.upsertMembers(memberEntities)
            }

            // Apply server-confirmed deletes from other devices immediately
            val serverDeletedIds = json.optJSONArray("deleted_ids")
            if (serverDeletedIds != null) {
                for (i in 0 until serverDeletedIds.length()) {
                    val deletedId = serverDeletedIds.getString(i)
                    if (deletedId !in serverGroupIds) {
                        channelGroupDao.deleteMembersForGroup(deletedId)
                        channelGroupDao.deleteGroup(deletedId)
                    }
                }
                Log.d(tag, "Applied ${serverDeletedIds.length()} server-side deletes")
            }

            // Stale cleanup: remove groups absent from server for over 30 days
            // (safety net for devices offline longer than the 30-day delete-tracking window)
            val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
            val localGroups = channelGroupDao.getAllGroupsOnce()
            for (local in localGroups) {
                if (local.id !in serverGroupIds && local.updatedAt < threshold) {
                    channelGroupDao.deleteMembersForGroup(local.id)
                    channelGroupDao.deleteGroup(local.id)
                }
            }

            Log.d(tag, "Synced ${groups.length()} groups from server")
        } catch (e: Exception) {
            Log.e(tag, "Sync groups failed", e)
        }
    }

    private fun fetchGet(url: String, auth: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("Content-Type", "application/json")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) { Log.e(tag, "fetchGet failed", e); null }
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
