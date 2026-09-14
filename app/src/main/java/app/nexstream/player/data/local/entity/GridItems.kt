package app.nexstream.player.data.local.entity

/**
 * Lightweight projection of MovieEntity for grid display.
 * Only fetches the 6 columns needed to show a poster card,
 * avoiding plot/cast/director/rating etc. for every row.
 */
data class MovieGridItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryName: String?,
    val streamUrl: String,
    val playlistId: String,
    val lastPlayedPosition: Long = 0L,
    val certification: String? = null,
    val rating: String? = null,
    val addedAt: Long = 0L,
    val releaseDate: String? = null
)

/**
 * Lightweight projection of SeriesEntity for grid display.
 */
data class SeriesGridItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryName: String?,
    val playlistId: String,
    val seasonCount: Int,
    val certification: String? = null,
    val rating: String? = null,
    val releaseDate: String? = null,
    val hasNewEpisodes: Boolean = false,
    val addedAt: Long = 0L
)

/** Minimal projection used when pre-fetching addedAt values before a batch import. */
data class IdAddedAt(val id: String, val addedAt: Long)