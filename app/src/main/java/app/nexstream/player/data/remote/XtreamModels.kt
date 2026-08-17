package app.nexstream.player.data.remote

import com.google.gson.annotations.SerializedName

data class XtreamAccountInfo(
    @SerializedName("user_info") val userInfo: XtreamUserInfo,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo
)

data class XtreamUserInfo(
    @SerializedName("username") val username: String?,
    @SerializedName("password") val password: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("auth") val auth: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class XtreamServerInfo(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val serverProtocol: String?,
    @SerializedName("rtmp_port") val rtmpPort: String?,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("timestamp_now") val timestampNow: Long?
)

data class XtreamCategory(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String
)

data class XtreamStream(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val icon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?,
    @SerializedName("stream_type") val streamType: String,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int = 0
)

data class XtreamVodStream(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val icon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("rating_5based") val rating5Based: Double?,
    @SerializedName("added") val added: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("original_language") val originalLanguage: String? = null
)

data class XtreamVodCategory(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String,
    @SerializedName("parent_id") val parentId: Int?
)

data class XtreamShortEpg(
    @SerializedName("epg_listings") val epgListings: List<XtreamEpgListing>?
)

data class XtreamEpgListing(
    @SerializedName("id") val id: String?,
    @SerializedName("epg_id") val epgId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("lang") val lang: String?,
    @SerializedName("start") val start: String?,
    @SerializedName("end") val end: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("channel_id") val channelId: String?,
    @SerializedName("start_timestamp") val startTimestamp: Long?,
    @SerializedName("stop_timestamp") val stopTimestamp: Long?
)

data class XtreamSimpleEpg(
    @SerializedName("epg_listings") val epgListings: List<XtreamEpgListing>?
)

data class XtreamMovieInfo(
    @SerializedName("info") val info: MovieInfo?,
    @SerializedName("movie_data") val movieData: MovieData?
)

data class MovieInfo(
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("youtube_trailer") val youtubeTrailer: String?,
    @SerializedName("original_language") val originalLanguage: String? = null
)

data class MovieData(
    @SerializedName("stream_id") val streamId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("rating") val rating: String?
)

// Series models
data class XtreamSeries(
    @SerializedName("series_id") val seriesId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
)

data class XtreamSeriesInfo(
    @SerializedName("info") val info: XtreamSeriesInfoDetail? = null,
    @SerializedName("episodes") val episodes: Map<String, List<XtreamEpisode>>? = null
)

data class XtreamSeriesInfoDetail(
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
)

data class XtreamEpisode(
    @SerializedName("id") val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mkv",
    @SerializedName("info") val info: XtreamEpisodeInfo? = null,
    @SerializedName("season") val season: Int = 0
)

data class XtreamEpisodeInfo(
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("movie_image") val cover: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null
)

data class CatchUpResponse(
    @com.google.gson.annotations.SerializedName("epg_listings")
    val epgListings: List<CatchUpListing> = emptyList()
)

data class CatchUpListing(
    @com.google.gson.annotations.SerializedName("id") val id: String = "",
    @com.google.gson.annotations.SerializedName("epg_id") val epgId: String = "",
    @com.google.gson.annotations.SerializedName("title") val title: String = "",
    @com.google.gson.annotations.SerializedName("lang") val lang: String = "",
    @com.google.gson.annotations.SerializedName("start") val start: String = "",
    @com.google.gson.annotations.SerializedName("end") val end: String = "",
    @com.google.gson.annotations.SerializedName("description") val description: String = "",
    @com.google.gson.annotations.SerializedName("channel_id") val channelId: String = "",
    @com.google.gson.annotations.SerializedName("start_timestamp") val startTimestamp: String = "",
    @com.google.gson.annotations.SerializedName("stop_timestamp") val stopTimestamp: String = ""
)
