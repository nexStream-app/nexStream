package app.nexstream.player.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class ThemeResponse(
    val success: Boolean,
    val version: Int,
    val theme: Map<String, Any>?, // raw map — ThemeParser handles deep parsing
)

interface ThemeApiService {
    @GET("theme.php")
    suspend fun getTheme(
        @Header("Authorization") bearerKey: String,
        @Query("mode") mode: String = "dark",
    ): Response<ThemeResponse>
}