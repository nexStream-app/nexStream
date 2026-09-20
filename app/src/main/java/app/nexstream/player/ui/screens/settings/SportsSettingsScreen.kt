package app.nexstream.player.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.R
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import kotlinx.coroutines.launch

@Composable
fun SportsSettingsScreen(
    firstItemFocusRequester: FocusRequester? = null,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()
    val refreshFR = firstItemFocusRequester ?: remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    stringResource(R.string.sports_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = sTheme.categoryText
                )
            }
            HorizontalDivider(color = sTheme.divider)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.SportsSoccer,
                    contentDescription = null,
                    tint = sTheme.categoryText.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        stringResource(R.string.sports_daily_guide_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = sTheme.categoryText
                    )
                    Text(
                        stringResource(R.string.sports_daily_guide_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = sTheme.categoryText.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = sTheme.divider.copy(alpha = 0.4f))

            Text(
                stringResource(R.string.sports_refresh_label),
                style = MaterialTheme.typography.labelSmall,
                color = sTheme.categoryText.copy(alpha = 0.55f)
            )

            var isFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .height(40.dp)
                    .focusRequester(refreshFR)
                    .onFocusChanged { fs ->
                        isFocused = fs.isFocused
                        if (fs.isFocused) scope.launch { scrollState.animateScrollTo(0) }
                    }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                        ) { onRefresh(); true } else false
                    },
                border = BorderStroke(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sports_refresh_button), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
