package app.nexstream.player.data.remote

/** Ratings data returned from the OMDb backend proxy. */
data class RtData(
    val criticsScore:  Int?,
    val audienceScore: Int?,
    val consensus:     String?,
    val metascore:     Int?,
)
