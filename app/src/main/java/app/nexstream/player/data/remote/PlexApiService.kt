package app.nexstream.player.data.remote

import retrofit2.http.*

// plex.tv — auth only
interface PlexAuthApiService {
    @POST("api/v2/pins")
    @FormUrlEncoded
    suspend fun createPin(
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Header("X-Plex-Product") product: String = "NexStream",
        @Header("X-Plex-Version") version: String = "1",
        @Header("X-Plex-Platform") platform: String = "Android",
        @Header("Accept") accept: String = "application/json",
        @Field("strong") strong: Boolean = true
    ): PlexPinResponse

    @GET("api/v2/pins/{id}")
    suspend fun getPin(
        @Path("id") id: Long,
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexPinResponse

    @GET("api/v2/resources")
    suspend fun getResources(
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json",
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1
    ): List<PlexDevice>
}

// Per Plex Media Server
interface PlexMediaApiService {
    @GET("library/sections")
    suspend fun getLibrarySections(
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexLibraryResponse

    @GET("library/sections/{sectionKey}/all")
    suspend fun getSectionItems(
        @Path("sectionKey") sectionKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json",
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 500
    ): PlexMetadataResponse

    @GET("library/metadata/{ratingKey}/children")
    suspend fun getChildren(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexChildrenResponse
}
