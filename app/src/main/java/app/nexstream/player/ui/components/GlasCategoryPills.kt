package app.nexstream.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexstream.player.ui.theme.GlassTokens

@Composable
fun GlasCategoryPills(
    categories: List<String>,
    selectedCategory: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
    onFocusDown: () -> Unit = {},
) {
    val allItems: List<String?> = listOf(null) + categories
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(allItems) { _, category ->
            val isSelected = category == selectedCategory
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(GlassTokens.RadiusPill))
                    .background(
                        if (isSelected) GlassTokens.AccentDefault else GlassTokens.GlassSmallSolid
                    )
                    .then(
                        if (focused && !isSelected) Modifier.border(
                            GlassTokens.FocusOutlineWidth,
                            GlassTokens.AccentDefault,
                            RoundedCornerShape(GlassTokens.RadiusPill),
                        ) else Modifier
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                onSelect(category); true
                            }
                            Key.DirectionDown -> { onFocusDown(); true }
                            else -> false
                        }
                    }
                    .clickable { onSelect(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category ?: "All",
                    color = if (isSelected) GlassTokens.AccentOnDark else GlassTokens.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
