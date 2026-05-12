package app.nexstream.player.subtitle

import retrofit2.http.GET
import retrofit2.http.Query

data class SubDLSearchResponse(
    val status: Boolean,
    val subtitles: List<SubDLSubtitle>?
)

data class SubDLSubtitle(
    val sd_id: Int,
    val lang: String,
    val language: String,
    val url: String,
    val full_name: String?,
    val release_name: String?,
    val hi: Boolean?,
    val ratings: Float?
)

interface SubtitleApiService {
    @GET("api/v1/subtitles")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("film_name") filmName: String,
        @Query("type") type: String = "movie",
        @Query("year") year: String? = null,
        @Query("languages") languages: String = "en",
        @Query("subs_per_page") subsPerPage: Int = 10
    ): SubDLSearchResponse

    @GET("api/v1/subtitles")
    suspend fun searchEpisode(
        @Query("api_key") apiKey: String,
        @Query("film_name") filmName: String,
        @Query("type") type: String = "tv",
        @Query("season_number") seasonNumber: Int,
        @Query("episode_number") episodeNumber: Int,
        @Query("languages") languages: String = "en",
        @Query("subs_per_page") subsPerPage: Int = 10
    ): SubDLSearchResponse
}