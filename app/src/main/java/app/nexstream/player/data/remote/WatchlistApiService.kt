package app.nexstream.player.data.remote

import retrofit2.Response
import retrofit2.http.*

data class WatchlistResponse(
    val success: Boolean,
    val items: List<Map<String, String?>> = emptyList(),
    val message: String? = null
)

data class AddChannelRequest(
    val channel_id: String,
    val channel_name: String,
    val stream_url: String,
    val logo_url: String?,
    val category: String?,
    val profile_id: String = "default"  // ADD THIS
)

data class AddVodRequest(
    val vod_id: String,
    val title: String,
    val stream_url: String,
    val poster_url: String?,
    val category: String?,
    val profile_id: String = "default"  // ADD THIS
)

data class AddSeriesRequest(
    val series_id: String,
    val title: String,
    val poster_url: String?,
    val category: String?,
    val current_season: Int = 1,
    val current_episode: Int = 1,
    val profile_id: String = "default"  // ADD THIS
)

data class DeleteRequest(
    val item_id: String,
    val profile_id: String = "default"  // ADD THIS
)

interface WatchlistApiService {
    @GET("mylist.php")
    suspend fun getList(
        @Header("Authorization") auth: String,
        @Query("type") type: String,
        @Query("profile_id") profileId: String = "default"  // ADD THIS
    ): Response<WatchlistResponse>

    @POST("mylist.php")
    suspend fun addChannel(
        @Header("Authorization") auth: String,
        @Query("type") type: String = "channels",
        @Body body: AddChannelRequest
    ): Response<WatchlistResponse>

    @POST("mylist.php")
    suspend fun addVod(
        @Header("Authorization") auth: String,
        @Query("type") type: String = "vod",
        @Body body: AddVodRequest
    ): Response<WatchlistResponse>

    @POST("mylist.php")
    suspend fun addSeries(
        @Header("Authorization") auth: String,
        @Query("type") type: String = "series",
        @Body body: AddSeriesRequest
    ): Response<WatchlistResponse>

    // Fixed: @DELETE with @Body not allowed in Retrofit — use @HTTP instead
    @HTTP(method = "DELETE", path = "mylist.php", hasBody = true)
    suspend fun removeItem(
        @Header("Authorization") auth: String,
        @Query("type") type: String,
        @Body body: DeleteRequest
    ): Response<WatchlistResponse>
}