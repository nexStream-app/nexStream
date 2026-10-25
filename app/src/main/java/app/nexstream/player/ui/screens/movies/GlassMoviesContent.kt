package app.nexstream.player.ui.screens.movies

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
import app.nexstream.player.data.local.entity.MovieGridItem
import app.nexstream.player.ui.components.GlasCategoryPills
import app.nexstream.player.ui.components.GlasPosterTile
import app.nexstream.player.ui.theme.GlassTokens

@Composable
fun GlassMoviesContent(
    movies: List<MovieGridItem>,
    selectedCategory: String?,
    firstItemFocusRequester: FocusRequester? = null,
    onFocusNavBar: () -> Unit = {},
    onMovieClick: (MovieGridItem) -> Unit = {},
    onCategorySelect: (String?) -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel(),
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
            itemsIndexed(movies) { index, movie ->
                GlasPosterTile(
                    imageUrl = movie.posterUrl,
                    title = movie.name,
                    rating = movie.rating?.toDoubleOrNull()?.let { "%.1f".format(it) }
                        ?: movie.rating,
                    ageRating = movie.certification,
                    modifier = if (index == 0) Modifier
                        .focusRequester(firstTileFR)
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                onFocusNavBar(); true
                            } else false
                        }
                    else Modifier,
                    onClick = { onMovieClick(movie) },
                )
            }
        }
    }
}
