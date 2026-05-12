package app.nexstream.player.data.remote

import retrofit2.Response
import retrofit2.http.*

data class ProgressResponse(
    val success: Boolean,
    val items: List<Map<String, String?>> = emptyList(),
    val message: String? = null
)

data class ProgressItem(
    val item_id:     String,
    val item_type:   String,   // "movie" or "episode"
    val series_id:   String?,
    val position_ms: Long,
    val duration_ms: Long,
    val profile_id:  String,
    val updated_at:  Long
)

data class ProgressBatchRequest(
    val profile_id: String,
    val items: List<ProgressItem>
)

interface ProgressApiService {

    @GET("progress.php")
    suspend fun getProgress(
        @Header("Authorization") auth: String,
        @Query("profile_id") profileId: String
    ): Response<ProgressResponse>

    @POST("progress.php")
    suspend fun pushProgress(
        @Header("Authorization") auth: String,
        @Body body: ProgressItem
    ): Response<ProgressResponse>

    @POST("progress.php")
    suspend fun pushBatch(
        @Header("Authorization") auth: String,
        @Query("action") action: String,
        @Body body: ProgressBatchRequest
    ): Response<ProgressResponse>
}