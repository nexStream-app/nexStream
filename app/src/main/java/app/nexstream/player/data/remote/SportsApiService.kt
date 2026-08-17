package app.nexstream.player.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface SportsApiService {
    @GET("sports.php")
    suspend fun getTodaySports(
        @Query("date")     date:     String? = null,
        @Query("force")    force:    Int?    = null,
        @Query("category") category: String? = null,
        @Query("replay")   replay:   Int?    = null,
    ): SportsResponse
}

data class SportsResponse(
    @SerializedName("success")               val success: Boolean,
    @SerializedName("date")                  val date: String,
    @SerializedName("events")                val events: List<SportEventDto> = emptyList(),
    @SerializedName("available_categories")  val availableCategories: List<String> = emptyList(),
    @SerializedName("attribution")           val attribution: String = "",
)

data class SportEventDto(
    @SerializedName("id")                  val id: Int,
    @SerializedName("sport_category")      val sportCategory: String,
    @SerializedName("sport_logo_url")      val sportLogoUrl: String = "",
    @SerializedName("event_name")          val eventName: String,
    @SerializedName("time_uk")             val timeUk: String,
    @SerializedName("channels")            val channels: List<String> = emptyList(),
    @SerializedName("source_category")     val sourceCategory: String = "",
    @SerializedName("group_name")          val groupName: String = "",
    @SerializedName("start_utc")           val startUtc: String? = null,
    @SerializedName("end_utc")             val endUtc: String? = null,
    @SerializedName("is_replay")           val isReplay: Int = 0,
    @SerializedName("channel_group_hint")  val channelGroupHint: String? = null,
)
