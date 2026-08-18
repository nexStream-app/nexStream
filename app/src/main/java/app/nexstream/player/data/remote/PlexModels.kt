package app.nexstream.player.data.remote

import com.google.gson.annotations.SerializedName

data class PlexPinResponse(
    val id: Long,
    val code: String,
    @SerializedName("auth_token") val authToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 300
)

data class PlexDevice(
    val name: String,
    val product: String = "",
    @SerializedName("clientIdentifier") val clientIdentifier: String = "",
    val connections: List<PlexConnection>? = null
)

data class PlexConnection(
    val uri: String,
    val local: Boolean = false,
    val relay: Boolean = false
)

data class PlexLibraryResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexLibraryContainer? = null
)

data class PlexLibraryContainer(
    @SerializedName("Directory") val directories: List<PlexDirectory>? = null
)

data class PlexDirectory(
    val key: String,
    val title: String,
    val type: String   // "movie", "show", "artist"
)

data class PlexMetadataResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexMetadataContainer? = null
)

data class PlexMetadataContainer(
    val size: Int = 0,
    @SerializedName("Metadata") val metadata: List<PlexMetadata>? = null
)

data class PlexMetadata(
    val ratingKey: String,
    val title: String,
    val summary: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    val type: String = "",                  // "movie", "show", "episode", "artist", "album", "track"
    val duration: Long? = null,
    val grandparentTitle: String? = null,   // series name for episodes
    val parentIndex: Int? = null,           // season number
    val index: Int? = null,                 // episode number
    val grandparentRatingKey: String? = null,
    val parentRatingKey: String? = null,
    @SerializedName("Media") val media: List<PlexMedia>? = null,
    // music fields
    val parentTitle: String? = null,        // album title for tracks
    val originalTitle: String? = null,      // artist for tracks
)

data class PlexMedia(
    @SerializedName("Part") val parts: List<PlexPart>? = null
)

data class PlexPart(
    val key: String                         // stream path, prepend server URL + token
)

// Children response (seasons of a show, episodes of a season, albums, tracks)
data class PlexChildrenResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexMetadataContainer? = null
)
