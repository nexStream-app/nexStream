package app.nexstream.player.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.data.local.entity.SeriesGridItem
import app.nexstream.player.ui.components.GlasCategoryPills
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.theme.GlassTokens

@Composable
fun GlassSeriesContent(
    seriesList: List<SeriesGridItem>,
    selectedCategory: String?,
    firstItemFocusRequester: FocusRequester? = null,
    onFocusNavBar: () -> Unit = {},
    onSeriesClick: (SeriesGridItem) -> Unit = {},
    onCategorySelect: (String?) -> Unit = {},
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val categories by remember(viewModel) { viewModel.getCategories() }
        .collectAsState(initial = emptyList())
    val firstTileFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize().background(GlassTokens.Background)) {
        GlasCategoryPills(
            categories = categories,
            selectedCategory = selectedCategory.takeIf {
                it != "__favourites__" && it != "__recent__"
            },
            modifier = Modifier.padding(horizontal = GlassTokens.ScreenGutter, vertical = 12.dp),
            onSelect = onCategorySelect,
            onFocusDown = { try { firstTileFR.requestFocus() } catch (_: Exception) {} },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = GlassTokens.ScreenGutter,
                vertical = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
        ) {
            itemsIndexed(seriesList) { index, series ->
                GlasPosterTile(
                    imageUrl = series.posterUrl,
                    title = series.name,
                    rating = series.rating?.toDoubleOrNull()?.let { "%.1f".format(it) }
                        ?: series.rating,
                    ageRating = series.certification,
                    hasNewEpisode = series.hasNewEpisodes,
                    modifier = if (index == 0) Modifier
                        .focusRequester(firstTileFR)
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                onFocusNavBar(); true
                            } else false
                        }
                    else Modifier,
                    onClick = { onSeriesClick(series) },
                )
            }
        }
    }
}
