package app.nexstream.player.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class RtSearchResponse(
    @SerializedName("tomatometer_score") val tomatometerScore: Int?,
    @SerializedName("audience_score")    val audienceScore: Int?,
    @SerializedName("critics_consensus") val criticsConsensus: String?,
)

/** Lightweight result returned to the dialog after a fetch + DB persist. */
data class RtData(
    val criticsScore:  Int?,
    val audienceScore: Int?,
    val consensus:     String?,
)

interface RtApiService {
    @GET("/")
    suspend fun searchByName(@Query("name") name: String): RtSearchResponse
}
