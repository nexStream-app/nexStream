package app.nexstream.player.license

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class TrialRequest(val device_id: String)

data class TrialResponse(
    val success: Boolean,
    val is_new: Boolean?,
    val expires_at: String?,
    val is_expired: Boolean?,
    val days_left: Int?,
    val sync_key: String?,
)

interface TrialApiService {
    @POST("trial.php")
    suspend fun checkTrial(@Body body: TrialRequest): Response<TrialResponse>
}