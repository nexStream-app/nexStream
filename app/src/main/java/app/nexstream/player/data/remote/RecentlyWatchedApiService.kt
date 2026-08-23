package app.nexstream.player.data.remote

import retrofit2.Response
import retrofit2.http.*

data class RecentlyWatchedResponse(
    val success: Boolean,
    val items: List<Map<String, String?>> = emptyList(),
    val message: String? = null
)

data class RecentlyWatchedItemRequest(
    val item_id: String,
    val type: String,
    val name: String,
    val subtitle: String? = null,
    val stream_url: String,
    val logo_url: String? = null,
    val series_id: String? = null,
    val episode_id: String? = null,
    val movie_id: String? = null,
    val watched_at: Long,
    val profile_id: String = "default"
)

data class RecentlyWatchedDeleteRequest(
    val item_id: String? = null,
    val profile_id: String = "default"
)

interface RecentlyWatchedApiService {
    @GET("recently_watched.php")
    suspend fun getList(
        @Header("Authorization") auth: String,
        @Query("profile_id") profileId: String = "default"
    ): Response<RecentlyWatchedResponse>

    @POST("recently_watched.php")
    suspend fun addItem(
        @Header("Authorization") auth: String,
        @Body body: RecentlyWatchedItemRequest
    ): Response<RecentlyWatchedResponse>

    @HTTP(method = "DELETE", path = "recently_watched.php", hasBody = true)
    suspend fun removeItem(
        @Header("Authorization") auth: String,
        @Body body: RecentlyWatchedDeleteRequest
    ): Response<RecentlyWatchedResponse>
}
