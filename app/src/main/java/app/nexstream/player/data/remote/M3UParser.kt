package app.nexstream.player.data.remote

import app.nexstream.player.data.local.entity.ChannelEntity
import java.util.UUID

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
                    val channelId = attributes["tvg-id"] ?: UUID.randomUUID().toString()

                    channels.add(
                        ChannelEntity(
                            id = "$playlistId-$channelId",
                            name = attributes["tvg-name"] ?: displayName,
                            streamUrl = streamUrl,
                            logoUrl = attributes["tvg-logo"],
                            groupTitle = attributes["group-title"],
                            epgChannelId = attributes["tvg-id"],
                            playlistId = playlistId
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
}