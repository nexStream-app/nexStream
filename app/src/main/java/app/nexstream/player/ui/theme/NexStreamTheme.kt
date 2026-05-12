package app.nexstream.player.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ─────────────────────────────────────────────────────────────
// Identity
// ─────────────────────────────────────────────────────────────

data class NexStreamIdentity(
    val appName: String = "nexStream",
    val logoMode: LogoMode = LogoMode.TEXT,
    val logoUrl: String? = null,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontRegularUrl: String? = null,
    val fontBoldUrl: String? = null,
)

data class NexStreamTypography(
    val scale: Float = 1.0f,
    val bold: Boolean = false,
)


enum class LogoMode { TEXT, IMAGE }

// ─────────────────────────────────────────────────────────────
// Sidebar colours
// ─────────────────────────────────────────────────────────────

data class NexStreamSidebarColors(
    val background: Color,
    val railBackground: Color,
    val railItemActiveBg: Color,
    val railItemFocusedBg: Color,
    val railIcon: Color,
    val railIconActive: Color,
    val panelBackground: Color,
    val categorySelectedBg: Color,
    val categoryFocusedBg: Color,
    val categoryText: Color,
    val categoryTextSelected: Color,
    val categoryTextFocused: Color,
    val divider: Color,
    val accountBarBg: Color,
    val accountBarText: Color,
    val accountBarTextSecondary: Color,
)

// ─────────────────────────────────────────────────────────────
// EPG colours
// ─────────────────────────────────────────────────────────────

data class NexStreamEpgColors(
    val background: Color,
    val headerBackground: Color,
    val headerTimeText: Color,
    val headerDayText: Color,
    val headerDivider: Color,
    val channelColumnBg: Color,
    val channelNameText: Color,
    val channelDivider: Color,
    val rowFocusedTint: Color,
    val programCellBg: Color,
    val programCellNowBg: Color,
    val programCellPlaceholderBg: Color,
    val programCellFocusedBg: Color,
    val programCellFocusedBorder: Color,
    val programText: Color,
    val programTextNow: Color,
    val programTextFocused: Color,
    val programTextPlaceholder: Color,
    val nowLine: Color,
    val divider: Color,
    val badgeCatchupBg: Color,
    val badgeCatchupText: Color,
    val badgeReminderBg: Color,
    val badgeReminderText: Color,
)

// ─────────────────────────────────────────────────────────────
// Player colours
// ─────────────────────────────────────────────────────────────

data class NexStreamPlayerColors(
    val controlsBg: Color,
    val progressPlayed: Color,
    val progressBuffered: Color,
    val progressBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

// ─────────────────────────────────────────────────────────────
// Global / Material bridge
// ─────────────────────────────────────────────────────────────

data class NexStreamGlobalColors(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
)

// ─────────────────────────────────────────────────────────────
// Top-level theme
// ─────────────────────────────────────────────────────────────

data class NexStreamTheme(
    val identity: NexStreamIdentity,
    val sidebar: NexStreamSidebarColors,
    val epg: NexStreamEpgColors,
    val player: NexStreamPlayerColors,
    val global: NexStreamGlobalColors,
    val typography: NexStreamTypography = NexStreamTypography(),
    val isDark: Boolean = true,
    val version: Int = 1,
)

// ─────────────────────────────────────────────────────────────
// CompositionLocal — used by all Compose screens
// ─────────────────────────────────────────────────────────────

val LocalNexStreamTheme = staticCompositionLocalOf<NexStreamTheme> {
    error("No NexStreamTheme provided — wrap your app in NexStreamThemeProvider")
}