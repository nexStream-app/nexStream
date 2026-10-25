package app.nexstream.player.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassTokens {

    // ── Background ───────────────────────────────────────────────────────────
    val Background = Color(0xFF07080A)

    // ── Text ─────────────────────────────────────────────────────────────────
    val TextPrimary   = Color(0xFFF5F5F0)
    val TextSecondary = Color(0xFFC9CED8)
    val TextTertiary  = Color(0xFFB9BDC7)

    // ── Accent ───────────────────────────────────────────────────────────────
    val AccentDefault = Color(0xFFFFD60A)
    val AccentBlue    = Color(0xFF5B8DEF)
    val AccentGreen   = Color(0xFF3DD68C)
    val AccentRed     = Color(0xFFF2555A)
    val AccentOnDark  = Color(0xFF07080A)  // text colour drawn on accent fill

    // ── Live badge ────────────────────────────────────────────────────────────
    val LiveBadge = Color(0xFFE5342A)

    // ── Glass surfaces ────────────────────────────────────────────────────────
    // Glass-on values (backdrop blur 24/32 px — API 31+ only)
    val GlassSmall  = Color(0xFF1C1E26).copy(alpha = 0.55f)  // nav, chips, buttons
    val GlassPanel  = Color(0xFF101218).copy(alpha = 0.70f)  // guide, player bar, sheets
    val GlassBorder = Color.White.copy(alpha = 0.12f)         // 1 px border everywhere

    // Glass-off fallback — same hue, 0.94 alpha, no blur (default on TV hardware)
    val GlassSmallSolid = Color(0xFF1C1E26).copy(alpha = 0.94f)
    val GlassPanelSolid = Color(0xFF101218).copy(alpha = 0.94f)

    // ── Corner radii ─────────────────────────────────────────────────────────
    val RadiusPill:  Dp = 999.dp
    val RadiusTile:  Dp = 14.dp
    val RadiusCard:  Dp = 18.dp
    val RadiusPanel: Dp = 28.dp

    // ── Spacing ───────────────────────────────────────────────────────────────
    val ScreenGutter: Dp = 48.dp
    val RailGap:      Dp = 16.dp
    val PanelPadding: Dp = 24.dp

    // ── Sizes ─────────────────────────────────────────────────────────────────
    val ButtonHeight:    Dp = 52.dp
    val IconButtonSize:  Dp = 48.dp
    val MinFocusTarget:  Dp = 44.dp

    // ── Focus ring ────────────────────────────────────────────────────────────
    val FocusOutlineWidth:  Dp = 3.dp
    val FocusOutlineOffset: Dp = 3.dp
    val FocusScaleMin         = 1.03f
    val FocusScaleMax         = 1.06f
    val FocusScaleDurationMs  = 150

    // ── Typography ────────────────────────────────────────────────────────────
    // Outfit TTF is not bundled yet; replaced in a later build.
    val OutfitFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
}

/** True = render blur via RenderEffect; false = use solid Glass-off fallback. */
val LocalGlassEffectsEnabled = staticCompositionLocalOf { false }
