package app.nexstream.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.GlassTokens

@Composable
fun <T> GlassRail(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    tileWidth: Dp = 240.dp,
    firstItemFocusRequester: FocusRequester? = null,
    onFocusUp: () -> Unit = {},
    itemContent: @Composable (index: Int, item: T, tileModifier: Modifier) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = title,
            color = GlassTokens.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.RailGap),
            contentPadding = PaddingValues(end = GlassTokens.ScreenGutter),
        ) {
            itemsIndexed(items) { index, item ->
                val tileModifier = Modifier
                    .width(tileWidth)
                    .then(
                        if (index == 0 && firstItemFocusRequester != null)
                            Modifier.focusRequester(firstItemFocusRequester)
                        else Modifier
                    )
                    .then(
                        if (index == 0) Modifier.onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                onFocusUp(); true
                            } else false
                        } else Modifier
                    )
                itemContent(index, item, tileModifier)
            }
        }
    }
}
