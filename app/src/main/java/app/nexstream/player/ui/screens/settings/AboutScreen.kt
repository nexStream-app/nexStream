package app.nexstream.player.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.nexstream.player.BuildConfig
import app.nexstream.player.R
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.ui.theme.LocalNexStreamTheme
import app.nexstream.player.ui.theme.UiStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val licenceManager: LicenceManager
) : ViewModel() {
    val deviceId: String = licenceManager.getDeviceId()
}

@Composable
fun AboutScreen(
    firstItemFocusRequester: FocusRequester? = null,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val nsTheme = LocalNexStreamTheme.current
    val sTheme = nsTheme.sidebar
    val headerHeight = (56 * nsTheme.typography.scale.coerceIn(0.85f, 1.5f)).dp
    val uiStyle = rememberUiStyle()

    val currentDeviceId = viewModel.deviceId

    var copied by remember { mutableStateOf(false) }

    val firstFR = firstItemFocusRequester ?: remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiStyle != UiStyle.MODERN) {
            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium, color = sTheme.categoryText)
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
            // ── App section ───────────────────────────────────────────────────
            SettingsSectionContainer(title = stringResource(R.string.about_section_app), icon = Icons.Default.Info, uiStyle = uiStyle) {
                SettingsInfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                SettingsInfoRow(stringResource(R.string.about_build),   BuildConfig.BUILD_NUMBER)
            }

            // ── Device ID section ─────────────────────────────────────────────
            SettingsSectionContainer(title = stringResource(R.string.about_section_device_id), icon = Icons.Default.Fingerprint, uiStyle = uiStyle) {
                SettingsInfoRow(stringResource(R.string.about_id), currentDeviceId)
                Spacer(Modifier.height(8.dp))
                var copyFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Device ID", currentDeviceId))
                        copied = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(firstFR)
                        .onFocusChanged { copyFocused = it.isFocused },
                    border = BorderStroke(
                        width = if (copyFocused) 2.dp else 1.dp,
                        color = if (copyFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (copyFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (copyFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) stringResource(R.string.about_copied) else stringResource(R.string.about_copy_device_id))
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Attributions section ──────────────────────────────────────────
            SettingsSectionContainer(title = stringResource(R.string.about_section_attributions), icon = Icons.Default.Movie, uiStyle = uiStyle) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_tmdb),
                        contentDescription = stringResource(R.string.about_tmdb_content_desc),
                        modifier = Modifier.height(20.dp).wrapContentWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_tmdb_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Legacy InfoSection — kept for any other callers ───────────────────────────

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, warning: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (warning) FontWeight.Bold else FontWeight.Normal,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
