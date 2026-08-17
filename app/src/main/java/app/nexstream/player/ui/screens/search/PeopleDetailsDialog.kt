package app.nexstream.player.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.ui.components.ContentCard
import app.nexstream.player.ui.theme.LocalNsAccent
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun PeopleDetailsDialog(
    person: PersonResult,
    detail: PersonDetail?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreditClick: (MovieEntity?, SeriesEntity?) -> Unit = { _, _ -> }
) {
    val accent = LocalNsAccent.current

    val dialogFocus = remember { FocusRequester() }
    var zone by remember { mutableStateOf(0) }
    val creditsFR = remember { FocusRequester() }
    val closeFR   = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        try { dialogFocus.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = true
        )
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.LaunchedEffect(Unit) { dialogWindow?.setDimAmount(0.85f) }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(14.dp))
        ) {
            // Backdrop
            val backdropUrl = detail?.knownForCredits?.firstOrNull { it.posterUrl != null }?.posterUrl
                ?: person.imageUrl
            AsyncImage(
                model              = backdropUrl,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
                alignment          = Alignment.TopCenter
            )

            // Gradient overlay
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f  to Color.Black.copy(alpha = 0.35f),
                            0.25f to Color.Black.copy(alpha = 0.60f),
                            0.55f to Color.Black.copy(alpha = 0.85f),
                            1.0f  to Color.Black.copy(alpha = 0.97f),
                        )
                    )
                )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(dialogFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.Back, Key.Escape -> { onDismiss(); true }
                            Key.DirectionDown -> {
                                if (zone == 0 && detail?.knownForCredits?.isNotEmpty() == true) {
                                    zone = 1
                                    try { creditsFR.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (zone == 1) {
                                    zone = 0
                                    try { closeFR.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
            ) {
                // Header row: photo + info column + close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    // Profile photo
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        AsyncImage(
                            model              = person.imageUrl ?: detail?.profileUrl,
                            contentDescription = person.name,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                        if (person.imageUrl == null && detail?.profileUrl == null) {
                            Icon(
                                imageVector        = Icons.Default.Person,
                                contentDescription = null,
                                tint               = Color.White.copy(alpha = 0.4f),
                                modifier           = Modifier.size(48.dp).align(Alignment.Center)
                            )
                        }
                    }

                    // Info column — name, chips, location, biography
                    Column(
                        modifier            = Modifier.weight(1f).padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text       = person.name,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = accent
                            )
                        } else if (detail != null) {
                            // Chips row: department + birthday
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                detail.knownFor?.let {
                                    InfoChip(Icons.Default.Star, it, accent)
                                }
                                detail.birthday?.let {
                                    InfoChip(Icons.Default.Cake, it, Color.White.copy(alpha = 0.7f))
                                }
                            }

                            // Birthplace
                            detail.birthplace?.let { place ->
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint     = Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text     = place,
                                        fontSize = 12.sp,
                                        color    = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Biography — placed next to photo inside the info column
                            if (detail.biography?.isNotEmpty() == true) {
                                Text(
                                    text       = detail.biography,
                                    fontSize   = 12.sp,
                                    fontStyle  = FontStyle.Italic,
                                    color      = Color.White.copy(alpha = 0.70f),
                                    maxLines   = 6,
                                    overflow   = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp,
                                    modifier   = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Close button
                    PeopleCloseButton(
                        focusRequester = closeFR,
                        accent         = accent,
                        onClose        = onDismiss
                    )
                }

                // Known for section — immediately after header
                if (!isLoading && detail?.knownForCredits?.isNotEmpty() == true) {
                    Text(
                        text       = "Known for",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = accent,
                        modifier   = Modifier.padding(start = 28.dp, bottom = 8.dp)
                    )

                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            detail.knownForCredits,
                            key = { idx, credit -> "cred_${credit.id}_$idx" }
                        ) { idx, credit ->
                            ContentCard(
                                name           = credit.title,
                                posterUrl      = credit.posterUrl,
                                badge          = if (credit.mediaType == "tv") "TV" else null,
                                defaultIcon    = if (credit.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie,
                                focusRequester = if (idx == 0) creditsFR else null,
                                onFocused      = { if (zone != 1) zone = 1 },
                                onClick        = { onCreditClick(credit.localMovie, credit.localSeries) }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier              = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PeopleCloseButton(
    focusRequester: FocusRequester,
    accent: Color,
    onClose: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (focused) accent else Color.Black.copy(alpha = 0.55f))
            .border(
                width = if (focused) 0.dp else 1.dp,
                color = if (focused) Color.Transparent else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClose() }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClose(); true }
                    else -> false
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector        = Icons.Default.Close,
            contentDescription = null,
            tint               = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text       = "Close",
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (focused) Color.White else Color.White.copy(alpha = 0.85f)
        )
    }
}
