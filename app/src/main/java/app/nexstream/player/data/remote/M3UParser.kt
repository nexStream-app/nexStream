package app.nexstream.player.data.remote

import app.nexstream.player.data.local.entity.ChannelEntity
import java.security.MessageDigest

object M3UParser {

    private val extInfRegex = Regex("""#EXTINF:-?\d+(.*)""")
    private val attributeRegex = Regex("""([\w-]+)="([^"]*?)"""")

    fun parse(content: String, playlistId: String): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF")) {
                val attributes = parseAttributes(line)
                val displayName = line.substringAfterLast(",").trim()

                // Advance to URL line
                i++
                while (i < lines.size && lines[i].isBlank()) i++
                val streamUrl = lines.getOrNull(i)?.trim() ?: ""

                if (streamUrl.isNotEmpty() && !streamUrl.startsWith("#")) {
                    // Use tvg-id when available; otherwise hash the URL for a stable ID
                    // across re-imports (avoids watchlist pruning for channels without tvg-id).
                    val channelId = attributes["tvg-id"] ?: urlHash(streamUrl)

                    channels.add(
                        ChannelEntity(
                            id = "$playlistId-$channelId",
                            name = attributes["tvg-name"] ?: displayName,
                            streamUrl = streamUrl,
                            logoUrl = attributes["tvg-logo"],
                            groupTitle = attributes["group-title"],
                            epgChannelId = attributes["tvg-id"],
                            playlistId = playlistId,
                            sortIndex = channels.size
                        )
                    )
                }
            }
            i++
        }

        return channels
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val matchResult = extInfRegex.find(line) ?: return emptyMap()
        val attrString = matchResult.groupValues[1]
        return attributeRegex.findAll(attrString)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun urlHash(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
}