package app.nexstream.player.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.local.dao.ChannelDao
import app.nexstream.player.data.local.dao.ProgramDao
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.remote.SportsApiService
import app.nexstream.player.ui.theme.getSportsCategoryOrderFlow
import app.nexstream.player.ui.theme.getSportsHiddenCategoriesFlow
import app.nexstream.player.ui.theme.saveSportsCategoryOrder
import app.nexstream.player.ui.theme.saveSportsHiddenCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChannelMatch(
    val displayName: String,
    val channelName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val epgChannelId: String? = null,
    val sortIndex: Int = 0,
)

data class MatchedSportEvent(
    val id: Int,
    val sportCategory: String,
    val sportLogoUrl: String,
    val eventName: String,
    val timeUk: String,
    val channels: List<String>,
    val matchedChannels: List<ChannelMatch>,
    val groupName: String = "",
    val sourceCategory: String = "",
    val startUtc: String? = null,
    val endUtc: String? = null,
    val isReplay: Boolean = false,
    val endEpochMs: Long? = null,
)

@HiltViewModel
class HomePageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val sportsApiService: SportsApiService,
    private val programDao: ProgramDao,
) : ViewModel() {

    private val _sportsEvents = MutableStateFlow<List<MatchedSportEvent>>(emptyList())
    val sportsEvents: StateFlow<List<MatchedSportEvent>> = _sportsEvents

    private val _isLoadingSports = MutableStateFlow(false)
    val isLoadingSports: StateFlow<Boolean> = _isLoadingSports

    private val _debugStatus = MutableStateFlow("Not loaded yet")
    val debugStatus: StateFlow<String> = _debugStatus

    private val _currentUkMinutes = MutableStateFlow(getCurrentUkMinutes())
    val currentUkMinutes: StateFlow<Int> = _currentUkMinutes

    val sportsCategories: StateFlow<List<String>> = _sportsEvents
        .map { events -> events.map { it.sportCategory }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedSportCategory = MutableStateFlow<String?>(null)

    val sportsHiddenCategories: StateFlow<Set<String>> =
        context.getSportsHiddenCategoriesFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val sportsCategoryOrder: StateFlow<List<String>> =
        context.getSportsCategoryOrderFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredSportsEvents: StateFlow<List<MatchedSportEvent>> = combine(
        _sportsEvents, selectedSportCategory, sportsHiddenCategories, _currentUkMinutes
    ) { events, category, hidden, currentUkMinutes ->
        val now = System.currentTimeMillis()
        val filtered = events
            .filter { it.sportCategory !in hidden }
            .filter { ev ->
                val endMs = ev.endEpochMs
                if (endMs != null) {
                    now <= endMs
                } else {
                    // No UTC data — fall back to timeUk + 120 min window
                    val parts = ev.timeUk.split(":")
                    if (parts.size == 2) {
                        val startMin = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                        currentUkMinutes <= startMin + 120
                    } else true
                }
            }
            .sortedBy { ev -> parseUtcIso(ev.startUtc) ?: Long.MAX_VALUE }
        if (category == null) filtered else filtered.filter { it.sportCategory == category }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveSportsPreferences(hidden: Set<String>, order: List<String>) {
        viewModelScope.launch {
            context.saveSportsHiddenCategories(hidden)
            context.saveSportsCategoryOrder(order)
        }
    }

    init {
        loadSports()
        viewModelScope.launch {
            while (true) {
                val msToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(msToNextMinute)
                _currentUkMinutes.value = getCurrentUkMinutes()
            }
        }
    }

    fun refresh() = loadSports()

    fun refreshSports() = loadSports(force = false)

    fun selectSportCategory(category: String?) {
        selectedSportCategory.value = category
    }

    private fun loadSports(force: Boolean = false) {
        viewModelScope.launch {
            _isLoadingSports.value = true
            _debugStatus.value = "Fetching sports from API…"
            Log.i(TAG, "loadSports: starting")
            try {
                val response = sportsApiService.getTodaySports(force = if (force) 1 else null)
                Log.i(TAG, "loadSports: API response — success=${response.success}, date=${response.date}, events=${response.events.size}")

                if (!response.success) {
                    _debugStatus.value = "API returned success=false (date=${response.date})"
                    Log.e(TAG, "loadSports: API success=false for date=${response.date}")
                    return@launch
                }

                if (response.events.isEmpty()) {
                    _debugStatus.value = "API returned 0 events for date=${response.date}"
                    Log.w(TAG, "loadSports: 0 events for date=${response.date}")
                    return@launch
                }

                _debugStatus.value = "API: ${response.events.size} events — loading channels…"
                val channels = channelDao.getAllChannels().first()
                Log.i(TAG, "loadSports: ${channels.size} channels in DB to match against")

                if (channels.isEmpty()) {
                    _debugStatus.value = "No channels in DB — add a playlist first"
                    Log.w(TAG, "loadSports: channel DB is empty, cannot match")
                    return@launch
                }

                // Channel matching is CPU-intensive — run off the main thread
                val matched = withContext(Dispatchers.Default) {
                    val globalIndex = buildChannelIndex(channels)
                    // Cache group-filtered indices to avoid rebuilding per event
                    val groupIndexCache = mutableMapOf<String, ChannelIndex>()

                    // Channels from "Today's Live Events" (or equivalent) group — used as fallback
                    // for events that have no specific broadcaster listed on the page.
                    // Sorted by channel name so 01, 02, 03… appear in order.
                    val liveEventsChannels: List<ChannelMatch> = channels
                        .filter { ch -> isLiveEventsGroup(ch.groupTitle) }
                        .map { ch -> ChannelMatch(ch.name, ch.name, ch.streamUrl, ch.logoUrl, ch.epgChannelId, ch.sortIndex) }
                        .distinctBy { it.streamUrl }
                        .sortedBy { it.channelName }
                    Log.i(TAG, "loadSports: liveEventsChannels=${liveEventsChannels.size} (group 'Today's Live Events')")

                    response.events.map { dto ->
                        val expandedChannels = dto.channels.flatMap { expandChannelName(it) }.distinct()
                        val hint = dto.channelGroupHint
                        val index = if (!hint.isNullOrBlank()) {
                            groupIndexCache.getOrPut(hint) {
                                buildChannelIndex(channels.filter { ch ->
                                    ch.groupTitle?.equals(hint, ignoreCase = true) == true
                                })
                            }
                        } else {
                            globalIndex
                        }
                        val matchedChannels = if (expandedChannels.isEmpty()) {
                            // No broadcaster listed on the page — use "Today's Live Events" numbered
                            // channels if the group exists in the playlist; otherwise nothing.
                            liveEventsChannels
                        } else {
                            expandedChannels.flatMap { scraped ->
                                findAllChannelMatchesIndexed(scraped, index)
                            }.distinctBy { it.streamUrl }.sortedBy { it.sortIndex }
                        }
                        Log.d(TAG, "  event '${dto.eventName}': scraped=${dto.channels}, hint=$hint, matched=${matchedChannels.size}")
                        MatchedSportEvent(
                            id              = dto.id,
                            sportCategory   = dto.sportCategory,
                            sportLogoUrl    = dto.sportLogoUrl,
                            eventName       = dto.eventName,
                            timeUk          = dto.timeUk,
                            channels        = dto.channels,
                            matchedChannels = matchedChannels,
                            groupName       = dto.groupName,
                            sourceCategory  = dto.sourceCategory,
                            startUtc        = dto.startUtc,
                            endUtc          = dto.endUtc,
                            isReplay        = dto.isReplay == 1,
                        )
                    }
                }

                val withChannels = matched.filter { it.matchedChannels.isNotEmpty() }

                // EPG enrichment: resolve accurate end times from the programme guide.
                // Priority: EPG endTime > end_utc from API > startUtc + 120 min fallback.
                val enriched = withContext(Dispatchers.IO) {
                    withChannels.map { event ->
                        val startMs = parseUtcIso(event.startUtc) ?: return@map event
                        val epgIds  = event.matchedChannels.mapNotNull { it.epgChannelId }.distinct()
                        val epgEnd  = if (epgIds.isNotEmpty()) {
                            programDao.findProgramNearStartTime(
                                channelIds = epgIds,
                                fromMs     = startMs - 45 * 60_000L,
                                toMs       = startMs + 45 * 60_000L,
                            )?.endTime
                        } else null
                        val resolvedEnd = epgEnd
                            ?: parseUtcIso(event.endUtc)
                            ?: (startMs + 120 * 60_000L)
                        event.copy(endEpochMs = resolvedEnd)
                    }
                }

                Log.i(TAG, "loadSports: done — ${matched.size} events, ${withChannels.size} have matched channels")
                _debugStatus.value = "${matched.size} events fetched, ${withChannels.size} matched to your channels"
                _sportsEvents.value = enriched
            } catch (e: Exception) {
                val msg = "Error: ${e.javaClass.simpleName}: ${e.message}"
                _debugStatus.value = msg
                Log.e(TAG, "loadSports: failed — $msg", e)
            } finally {
                _isLoadingSports.value = false
            }
        }
    }

    companion object {
        private const val TAG = "HomePageVM"
    }

    /**
     * Expand compound channel names like "TSN 2 & 5" → ["TSN 2", "TSN 5"]
     * and "Select 1 & 2" → ["Select 1", "Select 2"].
     */
    private fun expandChannelName(name: String): List<String> {
        // Split on " & " or " and " when preceded by a word+number pattern
        val parts = name.split(Regex("\\s*[&]\\s*|\\s+and\\s+", RegexOption.IGNORE_CASE))
        if (parts.size < 2) return listOf(name)

        val first = parts[0].trim()
        // Extract the prefix (everything before the trailing number/word in the first part)
        val prefixMatch = Regex("^(.*?)\\s*(\\d+)$").find(first)
        return if (prefixMatch != null) {
            val prefix = prefixMatch.groupValues[1].trim()
            val firstNum = prefixMatch.groupValues[2]
            val expanded = mutableListOf("$prefix $firstNum".trim())
            for (i in 1 until parts.size) {
                val part = parts[i].trim()
                expanded.add(if (part.all { it.isDigit() }) "$prefix $part".trim() else part)
            }
            expanded
        } else {
            parts.map { it.trim() }
        }
    }

    // Private index class
    private data class ChannelIndex(
        val exactMap: Map<String, ChannelEntity>,
        val substringList: List<Pair<String, ChannelEntity>>,
        val tokenMap: Map<String, List<ChannelEntity>>,
    )

    // UK regional qualifiers that appear in regional channel names (e.g. "BBC One Channel Islands",
    // "ITV West Country East") but never in the national equivalents we want to match instead.
    private val regionalQualifiers = listOf(
        "channel islands", "west country", "south west", "south east", "north west", "north east",
        "east midlands", "west midlands", "northern ireland", "tyne tees",
        "scotland", "wales", "yorkshire", "anglia", "meridian", "south", "north"
    )

    private fun isRegionalVariant(channelNorm: String): Boolean =
        regionalQualifiers.any { containsWholePhrase(channelNorm, it) }

    private fun hasNonLatinScript(name: String): Boolean =
        name.any { it.code in 0x0400..0x04FF   // Cyrillic (incl. homoglyphs like РіВНе)
                || it.code in 0x0600..0x06FF    // Arabic
                || it.code in 0x4E00..0x9FFF }  // CJK

    private fun buildChannelIndex(channels: List<ChannelEntity>): ChannelIndex {
        val exactMap  = mutableMapOf<String, ChannelEntity>()
        val tokenMap  = mutableMapOf<String, MutableList<ChannelEntity>>()
        for (ch in channels) {
            if (hasNonLatinScript(ch.name)) continue  // skip foreign-script variants (e.g. "ITV (Рівне)")
            val norm = normalize(ch.name)
            exactMap[norm] = ch
            significantTokens(norm).forEach { tok -> tokenMap.getOrPut(tok) { mutableListOf() }.add(ch) }
        }
        val substringList = channels
            .filter { !hasNonLatinScript(it.name) }
            .map { Pair(normalize(it.name), it) }
            .sortedByDescending { it.first.length }
        return ChannelIndex(exactMap, substringList, tokenMap)
    }

    private fun findAllChannelMatchesIndexed(scraped: String, index: ChannelIndex): List<ChannelMatch> {
        val scrapedNorm = normalize(scraped)
        if (scrapedNorm.isEmpty()) return emptyList()
        val trailingNumber = scrapedNorm.split(" ").lastOrNull { it.all(Char::isDigit) && it.isNotEmpty() }
        // Only fan-out when the scraped name includes a channel number — otherwise "ITV" would match ITV2, ITV3 etc.
        if (trailingNumber != null) {
            val allMatches = index.substringList
                .filter { (norm, _) ->
                    val phraseOk = (containsWholePhrase(norm, scrapedNorm) && scrapedNorm.length >= 3) ||
                                   (containsWholePhrase(scrapedNorm, norm) && norm.length >= 3)
                    phraseOk && trailingNumber in norm.split(" ")
                }
                .map { (_, ch) -> ChannelMatch(scraped, ch.name, ch.streamUrl, ch.logoUrl, ch.epgChannelId, ch.sortIndex) }
            if (allMatches.isNotEmpty()) {
                val national = allMatches.filter { !isRegionalVariant(normalize(it.channelName)) }
                return if (national.isNotEmpty()) national else allMatches
            }
        }
        return findBestChannelMatchIndexed(scraped, index)?.let { listOf(it) } ?: emptyList()
    }

    private fun findBestChannelMatchIndexed(scraped: String, index: ChannelIndex): ChannelMatch? {
        val scrapedNorm = normalize(scraped)
        if (scrapedNorm.isEmpty()) return null

        // 1. Exact
        index.exactMap[scrapedNorm]?.let { return ChannelMatch(scraped, it.name, it.streamUrl, it.logoUrl, it.epgChannelId, it.sortIndex) }

        // 2. Substring — word-boundary-aware; collect all matches, prefer closest length
        // (avoids "BBC One" → "BBC One Channel Islands", "ITV1" → "ITV West Country")
        // Number guard: if the scraped name ends with a channel number (e.g. "TNT Sports 3"),
        // only accept channels whose normalized name also contains that digit — prevents
        // "TNT Sports 3" from matching a channel simply named "TNT".
        val trailingNumber = scrapedNorm.split(" ").lastOrNull { it.all(Char::isDigit) && it.isNotEmpty() }
        val subCandidates = index.substringList
            .filter { (norm, _) ->
                val phraseOk = (containsWholePhrase(norm, scrapedNorm) && scrapedNorm.length >= 3) ||
                               (containsWholePhrase(scrapedNorm, norm) && norm.length >= 3)
                phraseOk && (trailingNumber == null || trailingNumber in norm.split(" "))
            }
        // Prefer national channels over regional variants (e.g. "BBC One HD" over "BBC One Channel Islands")
        val preferred = subCandidates.filter { (norm, _) -> !isRegionalVariant(norm) }
        val subMatch = (if (preferred.isNotEmpty()) preferred else subCandidates)
            .minByOrNull { (norm, _) -> abs(norm.length - scrapedNorm.length) }
            ?.second
        if (subMatch != null) return ChannelMatch(scraped, subMatch.name, subMatch.streamUrl, subMatch.logoUrl, subMatch.epgChannelId, subMatch.sortIndex)

        val scrapedTokens  = significantTokens(scrapedNorm)
        val numberToken    = scrapedNorm.split(" ").firstOrNull { it.all(Char::isDigit) && it.isNotEmpty() }

        // 3. Single strong token + number
        if (scrapedTokens.size == 1 && numberToken != null) {
            val singleToken = scrapedTokens.first()
            (index.tokenMap[singleToken] ?: emptyList()).firstOrNull { ch ->
                val n = normalize(ch.name)
                singleToken in significantTokens(n) && numberToken in n.split(" ")
            }?.let { return ChannelMatch(scraped, it.name, it.streamUrl, it.logoUrl, it.epgChannelId, it.sortIndex) }
        }

        // 4. Token overlap ≥ 2 — only examine channels sharing ≥1 token
        if (scrapedTokens.size >= 2) {
            val candidates = scrapedTokens.flatMap { index.tokenMap[it] ?: emptyList() }.distinct()
            val best = candidates.maxByOrNull { ch -> scrapedTokens.count { it in significantTokens(normalize(ch.name)) } }
            if (best != null && scrapedTokens.count { it in significantTokens(normalize(best.name)) } >= 2)
                return ChannelMatch(scraped, best.name, best.streamUrl, best.logoUrl, best.epgChannelId, best.sortIndex)
        }

        return null
    }

    private fun parseUtcIso(s: String?): Long? = try {
        if (s == null) null else java.time.Instant.parse(s).toEpochMilli()
    } catch (_: Exception) { null }

    private fun getCurrentUkMinutes(): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"))
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        var start = 0
        while (true) {
            val idx = haystack.indexOf(needle, start)
            if (idx == -1) return false
            val leftOk  = idx == 0 || haystack[idx - 1] == ' '
            val rightIdx = idx + needle.length
            val rightOk = rightIdx == haystack.length || haystack[rightIdx] == ' '
            if (leftOk && rightOk) return true
            start = idx + 1
        }
    }

    private val numberWords = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
    )

    private fun normalize(s: String): String {
        var result = s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("([a-z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([a-z])"), "$1 $2")
            .replace(Regex("\\s+"), " ")
            .trim()
        numberWords.forEach { (word, digit) ->
            result = result.replace(Regex("\\b$word\\b"), digit)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private val stopWords = setOf("hd", "sd", "the", "a", "an", "uk", "tv", "channel", "sports", "sport")

    private fun significantTokens(s: String): Set<String> =
        s.split(" ").filter { it.length > 1 && it !in stopWords }.toSet()

    // Match group titles like "Today's Live Events", "Todays Live Events",
    // "Live Events Today", "Daily Live Events", etc.
    private fun isLiveEventsGroup(groupTitle: String?): Boolean {
        if (groupTitle.isNullOrBlank()) return false
        val norm = groupTitle.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
        return (norm.contains("live") && norm.contains("event")) ||
               (norm.contains("today") && norm.contains("live"))
    }
}
