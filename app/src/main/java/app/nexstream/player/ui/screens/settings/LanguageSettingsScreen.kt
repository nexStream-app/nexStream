package app.nexstream.player.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexstream.player.R
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.getAppLanguageFlow
import app.nexstream.player.ui.theme.saveAppLanguage

@Composable
fun LanguageSettingsScreen(firstItemFocusRequester: FocusRequester? = null) {
    val context = LocalContext.current
    val sTheme = LocalNexStreamTheme.current.sidebar

    val currentLang by context.getAppLanguageFlow().collectAsState(initial = "")

    val systemDefault = stringResource(R.string.settings_language_system_default)
    val note = stringResource(R.string.settings_language_note)

    val languages = remember(systemDefault) {
        listOf(
            Triple("",   "🌐", systemDefault),
            Triple("en", "🇬🇧", "English"),
            Triple("fr", "🇫🇷", "Français"),
            Triple("de", "🇩🇪", "Deutsch"),
            Triple("nl", "🇳🇱", "Nederlands"),
            Triple("sv", "🇸🇪", "Svenska"),
            Triple("it", "🇮🇹", "Italiano"),
            Triple("tr", "🇹🇷", "Türkçe"),
            Triple("pl", "🇵🇱", "Polski"),
            Triple("es", "🇪🇸", "Español"),
            Triple("pt", "🇵🇹", "Português"),
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = sTheme.categoryText.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        itemsIndexed(languages) { index, (code, flag, name) ->
            LanguageRow(
                flag = flag,
                name = name,
                isSelected = code == currentLang,
                focusRequester = if (index == 0) firstItemFocusRequester else null,
                onClick = {
                    context.saveAppLanguage(code)
                    (context as? Activity)?.recreate()
                }
            )
        }
    }
}

@Composable
private fun LanguageRow(
    flag: String,
    name: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val sTheme = LocalNexStreamTheme.current.sidebar
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused  -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else       -> Color.Transparent
                }
            )
            .then(
                if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter)
                ) { onClick(); true } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = flag, style = MaterialTheme.typography.titleMedium)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else sTheme.categoryText,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary
            )
        }
    }
}
