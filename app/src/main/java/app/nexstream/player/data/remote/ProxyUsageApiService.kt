package app.nexstream.player.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ProxyHeartbeatRequest(
    val device_id: String,
    val mb: Float,
)

data class ProxyHeartbeatResponse(
    val ok: Boolean = true,
    val blocked: Boolean = false,
)

interface ProxyUsageApiService {
    @POST("proxy_usage.php")
    suspend fun heartbeat(
        @Header("Authorization") auth: String,
        @Body body: ProxyHeartbeatRequest,
    ): Response<ProxyHeartbeatResponse>
}
