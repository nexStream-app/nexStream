package app.nexstream.player.license

import retrofit2.Response
import retrofit2.http.*

data class ValidateRequest(
    val licence_key: String,
    val device_id: String,
    val device_name: String
)

data class ValidateResponse(
    val success: Boolean,
    val message: String?,
    val licence_type: String?,
    val expires_at: String?,
    val device_limit: Int?,
    val email: String?,
    val expired: Boolean?,
    val limit_reached: Boolean?
)

data class DeviceInfo(
    val device_name: String,
    val device_id: String,
    val registered_at: String,
    val last_seen: String
)

data class DevicesResponse(
    val success: Boolean,
    val devices: List<DeviceInfo>?,
    val device_limit: Int?,
    val device_count: Int?
)

data class HeartbeatRequest(val device_id: String)
data class RemoveDeviceRequest(val device_id: String)

interface LicenceApiService {
    @POST("validate.php")
    suspend fun validate(
        @Body body: ValidateRequest
    ): Response<ValidateResponse>

    @GET("devices.php")
    suspend fun getDevices(
        @Header("Authorization") auth: String
    ): Response<DevicesResponse>

    @POST("devices.php")
    suspend fun heartbeat(
        @Header("Authorization") auth: String,
        @Body body: HeartbeatRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "devices.php", hasBody = true)
    suspend fun removeDevice(
        @Header("Authorization") auth: String,
        @Body body: RemoveDeviceRequest
    ): Response<Unit>
}