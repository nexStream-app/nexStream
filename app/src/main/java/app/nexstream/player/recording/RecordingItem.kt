package app.nexstream.player.recording

data class RecordingItem(
    val id: Long,
    val title: String,
    val streamUrl: String,
    val filePath: String,
    val startedAt: Long,
    val bytesWritten: Long = 0L,
    val isActive: Boolean = true,
    val profileId: String = "default",
    val posterUrl: String? = null,
    val channelLogoUrl: String? = null,
    val description: String? = null
)
