package app.nexstream.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_appearance")
data class ProfileAppearanceEntity(
    @PrimaryKey val profileId: String,
    @ColumnInfo(name = "theme_mode")          val themeMode: String = "DARK",
    @ColumnInfo(name = "font_scale")          val fontScale: Float? = null,
    @ColumnInfo(name = "font_weight")         val fontWeight: String? = null,
    @ColumnInfo(name = "ui_style")            val uiStyle: String = "CLASSIC",
    @ColumnInfo(name = "tv_aspect_ratio")     val tvAspectRatio: String = "FILL",
    @ColumnInfo(name = "movie_aspect_ratio")  val movieAspectRatio: String = "FIT",
    @ColumnInfo(name = "series_aspect_ratio") val seriesAspectRatio: String = "FIT",
    @ColumnInfo(name = "epg_mini_player")     val epgMiniPlayer: Boolean = true,
    @ColumnInfo(name = "keyboard_font_scale") val keyboardFontScale: Float = 1.0f,
    @ColumnInfo(name = "keyboard_bold")       val keyboardBold: Boolean = true,
    @ColumnInfo(name = "updated_at")          val updatedAt: Long = System.currentTimeMillis()
)
