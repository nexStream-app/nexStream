package app.nexstream.player.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// -- Auth -----------------------------------------------------------------

data class JellyfinAuthBody(
    @SerializedName("Username") val username: String,
    @SerializedName("Pw") val password: String
)

data class JellyfinAuthResponse(
    @SerializedName("AccessToken") val accessToken: String,
    @SerializedName("User") val user: JellyfinUserInfo
)

data class JellyfinUserInfo(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String
)

// -- Items ----------------------------------------------------------------

data class JellyfinItemsResponse(
    @SerializedName("Items") val items: List<JellyfinItem> = emptyList(),
    @SerializedName("TotalRecordCount") val totalRecordCount: Int = 0
)

data class JellyfinItem(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String,
    @SerializedName("Type") val type: String,
    @SerializedName("SeriesId") val seriesId: String? = null,
    @SerializedName("SeasonId") val seasonId: String? = null,
    @SerializedName("IndexNumber") val indexNumber: Int? = null,
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerializedName("ProductionYear") val productionYear: Int? = null,
    @SerializedName("Overview") val overview: String? = null,
    @SerializedName("CommunityRating") val communityRating: Float? = null,
    @SerializedName("OfficialRating") val officialRating: String? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    // Music-specific fields
    @SerializedName("Artists") val artists: List<String>? = null,
    @SerializedName("AlbumArtist") val albumArtist: String? = null,
    @SerializedName("Album") val album: String? = null,
    @SerializedName("AlbumId") val albumId: String? = null,
    @SerializedName("HasLyrics") val hasLyrics: Boolean? = null,
)

// -- Lyrics ---------------------------------------------------------------

data class LyricsResponse(
    @SerializedName("Lyrics") val lyrics: List<LyricLine> = emptyList()
)

data class LyricLine(
    @SerializedName("Start") val startTicks: Long = 0L,
    @SerializedName("Text") val text: String = ""
) {
    val startMs: Long get() = startTicks / 10_000L
}

// -- Service --------------------------------------------------------------

interface JellyfinApiService {

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("Users/AuthenticateByName")
    suspend fun authenticate(
        @Header("X-Emby-Authorization") authorization: String,
        @Body body: JellyfinAuthBody
    ): JellyfinAuthResponse

    @GET("Users/{userId}/Views")
    suspend fun getLibraries(
        @Header("X-Emby-Token") token: String,
        @Path("userId") userId: String
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Header("X-Emby-Token") token: String,
        @Path("userId") userId: String,
        @Query("Ids") ids: String? = null,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") types: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Overview,Genres,ProductionYear,CommunityRating,OfficialRating,ImageTags",
        @Query("Limit") limit: Int = 200,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null
    ): JellyfinItemsResponse

    @GET("Audio/{itemId}/Lyrics")
    suspend fun getLyrics(
        @Header("X-Emby-Token") token: String,
        @Path("itemId") itemId: String
    ): LyricsResponse
}
