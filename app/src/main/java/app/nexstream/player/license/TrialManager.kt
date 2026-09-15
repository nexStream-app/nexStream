package app.nexstream.player.license

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class AppAccessState {
    LOADING,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    LICENSED
}

sealed class AssignedLicenceCheck {
    object None : AssignedLicenceCheck()
    data class TrialFound(val expiresAt: String?, val daysLeft: Int) : AssignedLicenceCheck()
    object FullLicenceActivated : AssignedLicenceCheck()
}

@Singleton
class TrialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TrialApiService,
    private val licencePreferences: LicencePreferences,
    private val licenceManager: LicenceManager
) {
    private val prefs = context.getSharedPreferences("nexstream_trial", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun httpGet(url: String): String {
        val response = httpClient.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "NexStream LicenceCheck")
                .build()
        ).execute()
        val body = response.body?.string() ?: ""
        response.close()
        return body
    }

    private fun saveTrialExpiry(expiresAt: String) =
        prefs.edit().putString("trial_expires_at", expiresAt).apply()
    private fun getTrialExpiry(): String? = prefs.getString("trial_expires_at", null)
    private fun isTrialExpiredLocally(): Boolean {
        val expiry = getTrialExpiry() ?: return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
            val date = sdf.parse(expiry) ?: return false
            date.before(java.util.Date())
        } catch (e: Exception) { false }
    }

    // Check if a licence has been assigned to this device ID on the server
    // and auto-activate it if found
    suspend fun checkAndActivateAssignedLicence(deviceId: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val json = JSONObject(
                    httpGet("https://nexstream.uk/api/check_licence.php?device_id=$deviceId")
                )
                val found = json.optBoolean("found", false)
                android.util.Log.i("TrialManager", "checkAndActivateAssignedLicence: found=$found")
                if (!found) return@withContext false

                val key = json.optJSONObject("licence")?.optString("key") ?: return@withContext false
                if (key.isEmpty()) return@withContext false

                android.util.Log.i("TrialManager", "checkAndActivateAssignedLicence: activating key=${key.take(6)}…")
                val result = licenceManager.activate(key, isResellerAssigned = false)
                android.util.Log.i("TrialManager", "checkAndActivateAssignedLicence: activate result=${result::class.simpleName}")
                result is LicenceResult.Success
            } catch (e: Exception) {
                android.util.Log.i("TrialManager", "checkAndActivateAssignedLicence: exception: ${e.message}")
                false
            }
        }

    // Called once after first playlist is added to create the trial on the backend.
    // Idempotent — safe to call on every import; returns existing trial for known devices.
    suspend fun initTrial(deviceId: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = api.checkTrial(TrialRequest(deviceId))
                if (!response.isSuccessful) return@withContext
                val body = response.body() ?: return@withContext
                if (body.success != true) return@withContext
                body.expires_at?.let { saveTrialExpiry(it) }
                body.sync_key?.let { licencePreferences.saveTrialSyncKey(it) }
            } catch (e: Exception) {
                android.util.Log.e("TrialManager", "initTrial failed: ${e.message}")
            }
        }
    }

    suspend fun checkAccessState(deviceId: String): AppAccessState = withContext(Dispatchers.IO) {
        android.util.Log.i("TrialManager", "checkAccessState: deviceId=$deviceId hasLicence=${licencePreferences.hasLicence()}")

        if (licencePreferences.hasLicence()) {
            android.util.Log.i("TrialManager", "checkAccessState: licence key present → LICENSED")
            return@withContext AppAccessState.LICENSED
        }

        // Check if a licence has been assigned to this device on the server
        android.util.Log.i("TrialManager", "checkAccessState: no local key, checking server for assigned licence")
        val activated = checkAndActivateAssignedLicence(deviceId)
        if (activated) {
            android.util.Log.i("TrialManager", "checkAccessState: server assigned licence activated → LICENSED")
            return@withContext AppAccessState.LICENSED
        }

        android.util.Log.i("TrialManager", "checkAccessState: no assigned licence, calling trial API")
        return@withContext try {
            val response = api.checkTrial(TrialRequest(deviceId))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    body.expires_at?.let { saveTrialExpiry(it) }
                    body.sync_key?.let { licencePreferences.saveTrialSyncKey(it) }
                    val result = if (body.is_expired == true) AppAccessState.TRIAL_EXPIRED else AppAccessState.TRIAL_ACTIVE
                    android.util.Log.i("TrialManager", "checkAccessState: trial API → $result (expired=${body.is_expired} expires=${body.expires_at})")
                    result
                } else {
                    android.util.Log.i("TrialManager", "checkAccessState: trial API body.success=false → fallbackToLocal")
                    fallbackToLocal()
                }
            } else {
                android.util.Log.i("TrialManager", "checkAccessState: trial API HTTP ${response.code()} → fallbackToLocal")
                fallbackToLocal()
            }
        } catch (e: Exception) {
            android.util.Log.i("TrialManager", "checkAccessState: trial API exception: ${e.message} → fallbackToLocal")
            fallbackToLocal()
        }
    }

    fun getLocalAccessState(): AppAccessState = fallbackToLocal()

    private fun fallbackToLocal(): AppAccessState {
        if (licencePreferences.hasLicence()) return AppAccessState.LICENSED
        val expiry = getTrialExpiry()
        return when {
            expiry == null -> AppAccessState.TRIAL_ACTIVE
            isTrialExpiredLocally() -> AppAccessState.TRIAL_EXPIRED
            else -> AppAccessState.TRIAL_ACTIVE
        }
    }

    fun getTrialExpiresAt(): String? = getTrialExpiry()

    fun getDaysLeft(): Int {
        val expiry = getTrialExpiry() ?: return 7
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
            val date = sdf.parse(expiry) ?: return 0
            maxOf(0, Math.ceil((date.time - System.currentTimeMillis()) / 86400000.0).toInt())
        } catch (e: Exception) { 0 }
    }

    fun getDeviceId(): String = licenceManager.getDeviceId()

    // Used by the "Check for Assigned Licence" button — checks type first, only activates if non-trial.
    suspend fun checkAssignedLicenceForDisplay(deviceId: String): AssignedLicenceCheck =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val json = JSONObject(
                    httpGet("https://nexstream.uk/api/check_licence.php?device_id=$deviceId")
                )
                if (!json.optBoolean("found", false)) return@withContext AssignedLicenceCheck.None

                val licenceObj = json.optJSONObject("licence") ?: return@withContext AssignedLicenceCheck.None
                val key  = licenceObj.optString("key", "")
                val type = licenceObj.optString("type", "")
                if (key.isEmpty()) return@withContext AssignedLicenceCheck.None

                if (type == "trial") {
                    val expiresAt = licenceObj.optString("expires_at", null)
                        .takeIf { !it.isNullOrEmpty() }
                    expiresAt?.let { saveTrialExpiry(it) }
                    AssignedLicenceCheck.TrialFound(
                        expiresAt = expiresAt ?: getTrialExpiry(),
                        daysLeft  = getDaysLeft()
                    )
                } else {
                    val result = licenceManager.activate(key, isResellerAssigned = false)
                    if (result is LicenceResult.Success) AssignedLicenceCheck.FullLicenceActivated
                    else AssignedLicenceCheck.None
                }
            } catch (e: Exception) {
                android.util.Log.w("TrialManager", "checkAssignedLicenceForDisplay: ${e.message}")
                AssignedLicenceCheck.None
            }
        }
}