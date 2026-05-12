package app.nexstream.player.data.remote

import app.nexstream.player.data.local.entity.ProgramEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

class XmltvParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(xmlContent: String): List<ProgramEntity> {
        val programs = mutableListOf<ProgramEntity>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var inProgramme = false
            var programChannelId = ""
            var programStart = 0L
            var programEnd = 0L
            var programTitle = ""
            var programDesc: String? = null
            var currentTag = ""

            // Use StringBuilders to accumulate text across multiple TEXT events
            val titleBuilder = StringBuilder()
            val descBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "programme") {
                            inProgramme = true
                            programChannelId = parser.getAttributeValue(null, "channel") ?: ""

                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            programStart = if (startStr != null) parseDate(startStr) else 0L
                            programEnd = if (stopStr != null) parseDate(stopStr) else 0L

                            // Reset accumulators for each new programme
                            programTitle = ""
                            programDesc = null
                            titleBuilder.clear()
                            descBuilder.clear()
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty() && inProgramme) {
                            when (currentTag) {
                                "title" -> titleBuilder.append(text)
                                "desc" -> descBuilder.append(text)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            // Commit accumulated text when closing tag is hit
                            "title" -> programTitle = titleBuilder.toString().trim()
                            "desc" -> programDesc = descBuilder.toString().trim().ifEmpty { null }
                            "programme" -> {
                                if (programChannelId.isNotEmpty() && programTitle.isNotEmpty()) {
                                    programs.add(
                                        ProgramEntity(
                                            id = "${programChannelId}_${programStart}",
                                            channelId = programChannelId,
                                            title = programTitle,
                                            description = programDesc,
                                            startTime = programStart,
                                            endTime = programEnd,
                                            category = null,
                                            icon = null
                                        )
                                    )
                                }
                                inProgramme = false
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }

            android.util.Log.d("XmltvParser", "Parsed ${programs.size} programs")
            programs.take(3).forEach {
                android.util.Log.d("XmltvParser", "Sample: channelId=[${it.channelId}] title=[${it.title}]")
            }

        } catch (e: Exception) {
            android.util.Log.e("XmltvParser", "Parse error", e)
        }

        return programs
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}