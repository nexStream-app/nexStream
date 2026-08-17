package app.nexstream.player.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import app.nexstream.player.R
import org.json.JSONObject
import java.io.File

private const val TAG = "ThemeParser"

object ThemeParser {

    fun parse(json: String, context: Context, isDark: Boolean, version: Int = 1): NexStreamTheme? {
        return try {
            val root = JSONObject(json)
            NexStreamTheme(
                isDark     = isDark,
                version    = version,
                identity   = parseIdentity(root.optJSONObject("identity"), context),
                sidebar    = parseSidebar(root.getJSONObject("sidebar")),
                epg        = parseEpg(root.getJSONObject("epg")),
                player     = parsePlayer(root.optJSONObject("player")),
                global     = parseGlobal(root.getJSONObject("global")),
                typography = parseTypography(root.optJSONObject("typography")),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theme JSON", e)
            null
        }
    }

    // ── Identity ──────────────────────────────────────────────

    private fun parseIdentity(obj: JSONObject?, context: Context): NexStreamIdentity {
        if (obj == null) return NexStreamIdentity()
        val fontKey        = obj.optString("font_family", "exo2")
        val fontRegularUrl = obj.optString("font_regular_url").ifEmpty { null }
        val fontBoldUrl    = obj.optString("font_bold_url").ifEmpty { null }
        val logoUrl        = obj.optString("logo_url").ifEmpty { null }
        val logoModeStr    = obj.optString("logo_mode", "text")

        val fontFamily = resolveFontFamily(fontKey, fontRegularUrl, context)

        return NexStreamIdentity(
            appName        = obj.optString("app_name", "nexStream"),
            logoMode       = if (logoModeStr == "image" && logoUrl != null) LogoMode.IMAGE else LogoMode.TEXT,
            logoUrl        = logoUrl,
            fontFamily     = fontFamily,
            fontRegularUrl = fontRegularUrl,
            fontBoldUrl    = fontBoldUrl,
        )
    }

    /**
     * Resolve font family: bundled (exo2/default) or downloaded TTF.
     * Downloaded fonts are loaded from the app's files dir if already cached.
     */
    private fun resolveFontFamily(key: String, remoteUrl: String?, context: Context): FontFamily {
        // 1. Check for cached downloaded font file
        if (remoteUrl != null) {
            val cachedFile = cachedFontFile(context, remoteUrl)
            if (cachedFile.exists()) {
                return try {
                    FontFamily(Typeface.createFromFile(cachedFile))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load cached font from file", e)
                    bundledFontFamily(key, context)
                }
            }
        }
        // 2. Fall back to bundled font
        return bundledFontFamily(key, context)
    }

    private fun bundledFontFamily(key: String, context: Context): FontFamily {
        return when (key.lowercase()) {
            "exo2" -> try {
                FontFamily(
                    androidx.compose.ui.text.font.Font(R.font.exo2_regular,  FontWeight.Normal),
                    androidx.compose.ui.text.font.Font(R.font.exo2_semibold, FontWeight.SemiBold),
                    androidx.compose.ui.text.font.Font(R.font.exo2_bold,     FontWeight.Bold),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load exo2 font resource", e)
                FontFamily.Default
            }
            "default" -> FontFamily.Default
            "sans"    -> FontFamily.SansSerif
            "mono"    -> FontFamily.Monospace
            else      -> FontFamily.Default
        }
    }

    fun cachedFontFile(context: Context, url: String): File {
        val name = url.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(context.filesDir, "fonts/$name")
    }

    // ── Sidebar ───────────────────────────────────────────────

    private fun parseSidebar(obj: JSONObject) = NexStreamSidebarColors(
        background              = obj.color("background"),
        railBackground          = obj.color("rail_background"),
        railItemActiveBg        = obj.color("rail_item_active_bg"),
        railItemFocusedBg       = obj.color("rail_item_focused_bg"),
        railIcon                = obj.color("rail_icon"),
        railIconActive          = obj.color("rail_icon_active"),
        panelBackground         = obj.color("panel_background"),
        categorySelectedBg      = obj.color("category_selected_bg"),
        categoryFocusedBg       = obj.color("category_focused_bg"),
        categoryText            = obj.color("category_text"),
        categoryTextSelected    = obj.color("category_text_selected"),
        categoryTextFocused     = obj.color("category_text_focused"),
        divider                 = obj.color("divider"),
        accountBarBg            = obj.color("account_bar_bg"),
        accountBarText          = obj.color("account_bar_text"),
        accountBarTextSecondary = obj.color("account_bar_text_secondary"),
    )

    // ── EPG ───────────────────────────────────────────────────

    private fun parseEpg(obj: JSONObject) = NexStreamEpgColors(
        background               = obj.color("background"),
        headerBackground         = obj.color("header_background"),
        headerTimeText           = obj.color("header_time_text"),
        headerDayText            = obj.color("header_day_text"),
        headerDivider            = obj.color("header_divider"),
        channelColumnBg          = obj.color("channel_column_bg"),
        channelNameText          = obj.color("channel_name_text"),
        channelDivider           = obj.color("channel_divider"),
        rowFocusedTint           = obj.color("row_focused_tint"),
        programCellBg            = obj.color("program_cell_bg"),
        programCellNowBg         = obj.color("program_cell_now_bg"),
        programCellPlaceholderBg = obj.color("program_cell_placeholder_bg"),
        programCellFocusedBg     = obj.color("program_cell_focused_bg"),
        programCellFocusedBorder = obj.color("program_cell_focused_border"),
        programText              = obj.color("program_text"),
        programTextNow           = obj.color("program_text_now"),
        programTextFocused       = obj.color("program_text_focused"),
        programTextPlaceholder   = obj.color("program_text_placeholder"),
        nowLine                  = obj.color("now_line"),
        divider                  = obj.color("divider"),
        badgeCatchupBg           = obj.color("badge_catchup_bg"),
        badgeCatchupText         = obj.color("badge_catchup_text"),
        badgeReminderBg          = obj.color("badge_reminder_bg"),
        badgeReminderText        = obj.color("badge_reminder_text"),
    )

    // ── Player ────────────────────────────────────────────────

    private fun parsePlayer(obj: JSONObject?): NexStreamPlayerColors {
        val d = ThemeDefaults.dark.player
        if (obj == null) return d
        return NexStreamPlayerColors(
            controlsBg       = obj.color("controls_bg",       d.controlsBg),
            progressPlayed   = obj.color("progress_played",   d.progressPlayed),
            progressBuffered = obj.color("progress_buffered", d.progressBuffered),
            progressBg       = obj.color("progress_bg",       d.progressBg),
            textPrimary      = obj.color("text_primary",      d.textPrimary),
            textSecondary    = obj.color("text_secondary",    d.textSecondary),
        )
    }

    // ── Global ────────────────────────────────────────────────

    private fun parseGlobal(obj: JSONObject) = NexStreamGlobalColors(
        primary            = obj.color("primary"),
        primaryContainer   = obj.color("primary_container"),
        onPrimary          = obj.color("on_primary"),
        onPrimaryContainer = obj.color("on_primary_container"),
        surface            = obj.color("surface"),
        surfaceVariant     = obj.color("surface_variant"),
        onSurface          = obj.color("on_surface"),
        onSurfaceVariant   = obj.color("on_surface_variant"),
        outline            = obj.color("outline"),
        error              = obj.color("error"),
    )

    private fun parseTypography(obj: JSONObject?): NexStreamTypography {
        if (obj == null) return NexStreamTypography()
        val w = obj.optString("weight", "normal").lowercase()
        return NexStreamTypography(
            scale  = obj.optDouble("scale", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            weight = when (w) { "bold", "semibold" -> w else -> "normal" },
        )
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun JSONObject.color(key: String, fallback: Color = Color.Magenta): Color {
        val hex = optString(key, "").trim()
        return parseHexColor(hex) ?: fallback
    }

    fun parseHexColor(hex: String): Color? {
        if (hex.isBlank()) return null
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            null
        }
    }
}