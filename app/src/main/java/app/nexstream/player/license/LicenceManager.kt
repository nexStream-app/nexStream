package app.nexstream.player.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class LicenceResult {
    data class Success(
        val email: String,
        val licenceType: String,
        val expiresAt: String?,
        val deviceLimit: Int
    ) : LicenceResult()
    data class Error(val message: String) : LicenceResult()
    object DeviceLimitReached : LicenceResult()
    object Expired : LicenceResult()
}

@Singleton
class LicenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LicenceApiService,
    private val prefs: LicencePreferences
) {
    fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun getDeviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun activate(licenceKey: String): LicenceResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = ValidateRequest(
                licence_key = licenceKey,
                device_id   = getDeviceId(),
                device_name = getDeviceName()
            )
            val response = api.validate(request)
            val body     = response.body()
            when {
                !response.isSuccessful || body == null -> {
                    val errorBody = response.errorBody()?.string()
                    when {
                        errorBody?.contains("limit_reached") == true -> LicenceResult.DeviceLimitReached
                        errorBody?.contains("expired") == true       -> LicenceResult.Expired
                        else -> LicenceResult.Error(body?.message ?: "Activation failed")
                    }
                }
                body.success == true -> {
                    prefs.saveLicenceKey(licenceKey)
                    prefs.saveEmail(body.email ?: "")
                    prefs.saveLicenceType(body.licence_type ?: "lifetime")
                    prefs.saveExpiresAt(body.expires_at)
                    prefs.saveDeviceLimit(body.device_limit ?: 1)
                    LicenceResult.Success(
                        email       = body.email ?: "",
                        licenceType = body.licence_type ?: "lifetime",
                        expiresAt   = body.expires_at,
                        deviceLimit = body.device_limit ?: 1
                    )
                }
                else -> LicenceResult.Error(body.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            LicenceResult.Error("Could not connect to licence server")
        }
    }

    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val key = prefs.getLicenceKey() ?: return@withContext
        try {
            api.heartbeat(auth = "Bearer $key", body = HeartbeatRequest(getDeviceId()))
        } catch (_: Exception) { }
    }

    suspend fun getDevices(): DevicesResponse? = withContext(Dispatchers.IO) {
        val key = prefs.getLicenceKey() ?: return@withContext null
        return@withContext try {
            api.getDevices("Bearer $key").body()
        } catch (_: Exception) { null }
    }

    // Remove this device from the server before clearing local prefs
    suspend fun removeDevice() = withContext(Dispatchers.IO) {
        val key = prefs.getLicenceKey() ?: return@withContext
        try {
            api.removeDevice(
                auth = "Bearer $key",
                body = RemoveDeviceRequest(getDeviceId())
            )
        } catch (_: Exception) { }
    }

    fun deactivate()            = prefs.clearAll()
    fun isActivated()           = prefs.hasLicence()
    fun getStoredEmail()        = prefs.getEmail()
    fun getStoredLicenceType()  = prefs.getLicenceType()
    fun getStoredExpiresAt()    = prefs.getExpiresAt()
    fun getStoredDeviceLimit()  = prefs.getDeviceLimit()
}