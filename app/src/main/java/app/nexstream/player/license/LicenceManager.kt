package app.nexstream.player.license

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
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
    private val _activationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activationEvents: SharedFlow<Unit> = _activationEvents

    fun notifyActivated() { _activationEvents.tryEmit(Unit) }

    fun getDeviceId(): String = prefs.getOrCreateStableDeviceId()

    private fun getDeviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun activate(licenceKey: String, isResellerAssigned: Boolean = false): LicenceResult = withContext(Dispatchers.IO) {
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
                    prefs.saveResellerId(body.reseller_id)
                    prefs.saveIsResellerAssigned(isResellerAssigned || body.reseller_id != null)
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
            val resp = api.heartbeat(
                auth = "Bearer $key",
                body = HeartbeatRequest(device_id = getDeviceId(), device_name = getDeviceName())
            )
            android.util.Log.i("LicenceManager", "heartbeat: code=${resp.code()} success=${resp.body()?.success}")
        } catch (e: Exception) {
            android.util.Log.w("LicenceManager", "heartbeat failed: ${e.message}")
        }
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
    fun getStoredLicenceKey()   = prefs.getLicenceKey()
    fun getStoredEmail()        = prefs.getEmail()
    fun getStoredLicenceType()  = prefs.getLicenceType()
    fun getStoredExpiresAt()    = prefs.getExpiresAt()
    fun getStoredDeviceLimit()  = prefs.getDeviceLimit()
    fun getStoredResellerId()   = prefs.getResellerId()
    fun isResellerAssigned()    = prefs.isResellerAssigned()
}