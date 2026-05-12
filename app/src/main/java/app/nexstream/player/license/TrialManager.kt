package app.nexstream.player.license

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class AppAccessState {
    LOADING,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    LICENSED
}

@Singleton
class TrialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TrialApiService,
    private val licencePreferences: LicencePreferences,
    private val licenceManager: LicenceManager
) {
    private val prefs = context.getSharedPreferences("nexstream_trial", Context.MODE_PRIVATE)

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
                    URL("https://nexstream.uk/api/check_licence.php?device_id=$deviceId")
                        .readText()
                )
                if (!json.optBoolean("found", false)) return@withContext false

                val key = json.optJSONObject("licence")?.optString("key") ?: return@withContext false
                if (key.isEmpty()) return@withContext false

                android.util.Log.d("TrialManager", "Found assigned licence: $key")
                val result = licenceManager.activate(key)
                result is LicenceResult.Success
            } catch (e: Exception) {
                android.util.Log.e("TrialManager", "Licence check failed: ${e.message}")
                false
            }
        }

    suspend fun checkAccessState(deviceId: String): AppAccessState = withContext(Dispatchers.IO) {
        android.util.Log.d("TrialManager", "Checking access for device: $deviceId")

        if (licencePreferences.hasLicence()) return@withContext AppAccessState.LICENSED

        // Check if a licence has been assigned to this device on the server
        val activated = checkAndActivateAssignedLicence(deviceId)
        if (activated) return@withContext AppAccessState.LICENSED

        return@withContext try {
            val response = api.checkTrial(TrialRequest(deviceId))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    body.expires_at?.let { saveTrialExpiry(it) }
                    if (body.is_expired == true) AppAccessState.TRIAL_EXPIRED
                    else AppAccessState.TRIAL_ACTIVE
                } else {
                    fallbackToLocal()
                }
            } else {
                fallbackToLocal()
            }
        } catch (e: Exception) {
            fallbackToLocal()
        }
    }

    private fun fallbackToLocal(): AppAccessState {
        if (licencePreferences.hasLicence()) return AppAccessState.LICENSED
        val expiry = getTrialExpiry()
        return when {
            expiry == null -> AppAccessState.TRIAL_ACTIVE
            isTrialExpiredLocally() -> AppAccessState.TRIAL_EXPIRED
            else -> AppAccessState.TRIAL_ACTIVE
        }
    }

    fun getDaysLeft(): Int {
        val expiry = getTrialExpiry() ?: return 7
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.UK)
            val date = sdf.parse(expiry) ?: return 0
            maxOf(0, Math.ceil((date.time - System.currentTimeMillis()) / 86400000.0).toInt())
        } catch (e: Exception) { 0 }
    }

    fun getDeviceId(): String = licenceManager.getDeviceId()
}