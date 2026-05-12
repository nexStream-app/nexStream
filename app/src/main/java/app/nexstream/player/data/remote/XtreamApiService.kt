package app.nexstream.player.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getAccountInfo(
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAccountInfo

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XtreamCategory>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<XtreamStream>

    @GET("player_api.php?action=get_vod_streams")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamVodStream>

    @GET("player_api.php?action=get_vod_categories")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamVodCategory>

    @GET("player_api.php?action=get_short_epg")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int = 20
    ): XtreamShortEpg

    @GET("player_api.php?action=get_simple_data_table")
    suspend fun getSimpleDataTable(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int
    ): XtreamSimpleEpg

    @GET("player_api.php")
    suspend fun getMovieInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: String
    ): XtreamMovieInfo

    // Series endpoints
    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_series")
    suspend fun getAllSeries(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamSeries>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String
    ): XtreamSeriesInfo

    @GET("player_api.php")
    suspend fun getCatchUpData(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_simple_data_table",
        @Query("stream_id") streamId: String
    ): retrofit2.Response<CatchUpResponse>

    @Streaming
    @GET("player_api.php?action=get_vod_streams")
    suspend fun getVodStreamsRaw(
        @Query("username") username: String,
        @Query("password") password: String
    ): ResponseBody

    @Streaming
    @GET("player_api.php?action=get_series")
    suspend fun getAllSeriesRaw(
        @Query("username") username: String,
        @Query("password") password: String
    ): ResponseBody

    @Streaming
    @GET("player_api.php")
    suspend fun getLiveStreamsRaw(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): ResponseBody
}


