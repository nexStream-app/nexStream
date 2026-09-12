package app.nexstream.player.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.theme.LocalNsAccent
import app.nexstream.player.ui.theme.LocalNsBackground
import app.nexstream.player.ui.theme.LocalNsDivider
import app.nexstream.player.ui.theme.LocalNsSurface
import app.nexstream.player.ui.theme.LocalNsTextPrimary
import app.nexstream.player.ui.theme.LocalNsTextSecondary

@Composable
fun ModernSearchContent(
    query: String,
    results: SearchResults,
    selectedType: String?,
    searchHint: String,
    searchFocusRequester: FocusRequester,
    onOpenKeyboard: () -> Unit,
    onClearQuery: () -> Unit,
    onChannelClick: (streamUrl: String, channelName: String) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onPersonClick: (PersonResult) -> Unit = {},
    onCastMovieClick: (MovieEntity) -> Unit = {},
    onCastSeriesClick: (SeriesEntity) -> Unit = {},
    onProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = {},
    onRequestSidebarFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val background    = LocalNsBackground.current
    val accent        = LocalNsAccent.current
    val surface       = LocalNsSurface.current
    val divider       = LocalNsDivider.current
    val textPrimary   = LocalNsTextPrimary.current
    val textSecondary = LocalNsTextSecondary.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        // ── Pill search bar ───────────────────────────────────────────────────
        var searchBarFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(surface)
                .border(
                    width = 1.5.dp,
                    color = if (searchBarFocused) accent else divider,
                    shape = RoundedCornerShape(50.dp),
                )
                .focusRequester(searchFocusRequester)
                .onFocusChanged { searchBarFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onOpenKeyboard(); true }
                        Key.DirectionLeft -> { onRequestSidebarFocus(); true }
                        else -> false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenKeyboard() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = if (searchBarFocused) accent else textSecondary,
                    modifier           = Modifier.size(18.dp),
                )
                Text(
                    text     = query.ifEmpty { searchHint },
                    fontSize = 14.sp,
                    color    = if (query.isEmpty()) textSecondary.copy(alpha = 0.55f)
                               else textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint               = textSecondary,
                        modifier           = Modifier
                            .size(16.dp)
                            .clickable { onClearQuery() },
                    )
                }
            }
        }

        // ── Results ───────────────────────────────────────────────────────────
        when {
            query.length < 2 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Search, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint     = textSecondary.copy(alpha = 0.35f),
                        )
                        Text(
                            text     = when (selectedType) {
                                "People" -> "Search by director or cast member name"
                                else     -> "Type to search"
                            },
                            fontSize = 14.sp,
                            color    = textSecondary,
                        )
                    }
                }
            }

            selectedType == null -> {
                ModernSearchAllResults(
                    results           = results,
                    firstFR           = searchFocusRequester,
                    accent            = accent,
                    divider           = divider,
                    onChannelClick    = onChannelClick,
                    onMovieClick      = onMovieClick,
                    onSeriesClick     = onSeriesClick,
                    onPersonClick     = onPersonClick,
                    onCastMovieClick  = onCastMovieClick,
                    onCastSeriesClick = onCastSeriesClick,
                    onProgrammeClick  = onProgrammeClick,
                )
            }

            else -> {
                ModernSearchPanelGrid(
                    results           = results,
                    selectedType      = selectedType,
                    firstFR           = searchFocusRequester,
                    query             = query,
                    textSecondary     = textSecondary,
                    onChannelClick    = onChannelClick,
                    onMovieClick      = onMovieClick,
                    onSeriesClick     = onSeriesClick,
                    onPersonClick     = onPersonClick,
                    onCastMovieClick  = onCastMovieClick,
                    onCastSeriesClick = onCastSeriesClick,
                    onProgrammeClick  = onProgrammeClick,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// All-results view: LazyColumn with merged header+row Column items per section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModernSearchAllResults(
    results: SearchResults,
    firstFR: FocusRequester,
    accent: androidx.compose.ui.graphics.Color,
    divider: androidx.compose.ui.graphics.Color,
    onChannelClick: (String, String) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onPersonClick: (PersonResult) -> Unit = {},
    onCastMovieClick: (MovieEntity) -> Unit = {},
    onCastSeriesClick: (SeriesEntity) -> Unit = {},
    onProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = {},
) {
    val textSecondary = LocalNsTextSecondary.current

    val hasAny = results.channels.isNotEmpty() || results.programmes.isNotEmpty() ||
                 results.movies.isNotEmpty()   || results.series.isNotEmpty() ||
                 results.people.isNotEmpty()   ||
                 results.peopleMovies.isNotEmpty() || results.peopleSeries.isNotEmpty()

    if (!hasAny) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results", fontSize = 14.sp, color = textSecondary)
        }
        return
    }

    val firstSection = when {
        results.channels.isNotEmpty()     -> "channels"
        results.programmes.isNotEmpty()   -> "programmes"
        results.movies.isNotEmpty()       -> "movies"
        results.series.isNotEmpty()       -> "series"
        results.people.isNotEmpty()       -> "people"
        results.peopleMovies.isNotEmpty() -> "pmovies"
        else                              -> "pseries"
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (results.channels.isNotEmpty()) {
            item(key = "section_ch") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Channels (${results.channels.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.channels, key = { _, ch -> "sch_${ch.id}" }) { idx, ch ->
                            ContentCard(
                                name           = ch.name,
                                posterUrl      = ch.logoUrl,
                                defaultIcon    = Icons.Default.Tv,
                                focusRequester = if (firstSection == "channels" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onChannelClick(ch.streamUrl, ch.name) },
                            )
                        }
                    }
                }
            }
        }
        if (results.programmes.isNotEmpty()) {
            item(key = "section_prog") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Programmes (${results.programmes.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.programmes, key = { _, p -> "sprog_${p.id}" }) { idx, prog ->
                            ContentCard(
                                name           = prog.title,
                                posterUrl      = prog.icon,
                                defaultIcon    = Icons.Default.CalendarToday,
                                focusRequester = if (firstSection == "programmes" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onProgrammeClick(prog) },
                            )
                        }
                    }
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            item(key = "section_mov") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Movies (${results.movies.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.movies, key = { _, m -> "smov_${m.id}" }) { idx, movie ->
                            ContentCard(
                                name           = movie.name,
                                posterUrl      = movie.posterUrl,
                                defaultIcon    = Icons.Default.Movie,
                                focusRequester = if (firstSection == "movies" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onMovieClick(movie) },
                            )
                        }
                    }
                }
            }
        }
        if (results.series.isNotEmpty()) {
            item(key = "section_ser") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Series (${results.series.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.series, key = { _, s -> "sser_${s.id}" }) { idx, s ->
                            ContentCard(
                                name           = s.name,
                                posterUrl      = s.posterUrl,
                                badge          = if (s.seasonCount > 0) "${s.seasonCount}S" else null,
                                defaultIcon    = Icons.Default.VideoLibrary,
                                focusRequester = if (firstSection == "series" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onSeriesClick(s) },
                            )
                        }
                    }
                }
            }
        }
        if (results.people.isNotEmpty()) {
            item(key = "section_people") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("People (${results.people.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.people, key = { _, p -> "sp_${p.name}" }) { idx, person ->
                            ContentCard(
                                name           = person.name,
                                posterUrl      = person.imageUrl,
                                defaultIcon    = Icons.Default.Person,
                                focusRequester = if (firstSection == "people" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onPersonClick(person) },
                            )
                        }
                    }
                }
            }
        }
        if (results.peopleMovies.isNotEmpty()) {
            item(key = "section_pm") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Movies by cast/director (${results.peopleMovies.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.peopleMovies, key = { _, m -> "spm_${m.id}" }) { idx, movie ->
                            ContentCard(
                                name           = movie.name,
                                posterUrl      = movie.posterUrl,
                                defaultIcon    = Icons.Default.Movie,
                                focusRequester = if (firstSection == "pmovies" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onCastMovieClick(movie) },
                            )
                        }
                    }
                }
            }
        }
        if (results.peopleSeries.isNotEmpty()) {
            item(key = "section_ps") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSectionHeader("Series by cast/director (${results.peopleSeries.size})", accent, divider)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding        = PaddingValues(horizontal = 4.dp),
                    ) {
                        itemsIndexed(results.peopleSeries, key = { _, s -> "sps_${s.id}" }) { idx, s ->
                            ContentCard(
                                name           = s.name,
                                posterUrl      = s.posterUrl,
                                defaultIcon    = Icons.Default.VideoLibrary,
                                focusRequester = if (firstSection == "pseries" && idx == 0) firstFR else null,
                                onFocused      = {},
                                onClick        = { onCastSeriesClick(s) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Type-specific grid view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModernSearchPanelGrid(
    results: SearchResults,
    selectedType: String,
    firstFR: FocusRequester,
    query: String,
    textSecondary: androidx.compose.ui.graphics.Color,
    onChannelClick: (String, String) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onPersonClick: (PersonResult) -> Unit = {},
    onCastMovieClick: (MovieEntity) -> Unit = {},
    onCastSeriesClick: (SeriesEntity) -> Unit = {},
    onProgrammeClick: (app.nexstream.player.data.local.entity.ProgramEntity) -> Unit = {},
) {
    data class GridItem(
        val id: String,
        val name: String,
        val posterUrl: String?,
        val badge: String?,
        val icon: ImageVector,
        val onClick: () -> Unit,
    )

    val gridItems: List<GridItem> = when (selectedType) {
        "Live TV" -> results.channels.map { ch ->
            GridItem(ch.id, ch.name, ch.logoUrl, null, Icons.Default.Tv) { onChannelClick(ch.streamUrl, ch.name) }
        } + results.programmes.map { prog ->
            GridItem(prog.id, prog.title, prog.icon, null, Icons.Default.CalendarToday) {
                onProgrammeClick(prog)
            }
        }
        "Movies"  -> results.movies.map { m ->
            GridItem(m.id, m.name, m.posterUrl, null, Icons.Default.Movie) { onMovieClick(m) }
        }
        "Series"  -> results.series.map { s ->
            GridItem(s.id, s.name, s.posterUrl, if (s.seasonCount > 0) "${s.seasonCount}S" else null, Icons.Default.VideoLibrary) { onSeriesClick(s) }
        }
        "People"  -> results.people.map { p ->
            GridItem("p_${p.name}", p.name, p.imageUrl, null, Icons.Default.Person) { onPersonClick(p) }
        }
        else -> emptyList()
    }

    if (gridItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text     = when {
                    selectedType == "People" && query.isBlank() -> "Search by director or cast member name"
                    selectedType == "People" -> "No people found for \"$query\""
                    else -> "No results for \"$query\""
                },
                fontSize = 14.sp,
                color    = textSecondary,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 120.dp),
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
    ) {
        gridItemsIndexed(gridItems, key = { _, gi -> "pg_${gi.id}" }) { idx, gi ->
            ContentCard(
                name           = gi.name,
                posterUrl      = gi.posterUrl,
                badge          = gi.badge,
                defaultIcon    = gi.icon,
                focusRequester = if (idx == 0) firstFR else null,
                onFocused      = {},
                onClick        = gi.onClick,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header with accent label + subtle rule
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModernSectionHeader(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    divider: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text       = title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = accent,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider.copy(alpha = 0.4f)),
        )
    }
}
