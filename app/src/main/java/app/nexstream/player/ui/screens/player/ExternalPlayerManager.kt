package app.nexstream.player.ui.screens.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object ExternalPlayerManager {
    data class PlayerOption(val id: String, val displayName: String)

    val NEXSTREAM = PlayerOption("nexstream", "nexStream (built-in)")

    val KNOWN_PLAYERS = listOf(
        PlayerOption("org.videolan.vlc",           "VLC"),
        PlayerOption("com.mxtech.videoplayer.ad",  "MX Player"),
        PlayerOption("com.mxtech.videoplayer.pro", "MX Player Pro"),
        PlayerOption("org.xbmc.kodi",              "Kodi"),
    )

    fun getAvailablePlayers(context: Context): List<PlayerOption> {
        val result = mutableListOf(NEXSTREAM)
        for (player in KNOWN_PLAYERS) {
            try { context.packageManager.getPackageInfo(player.id, 0); result += player }
            catch (_: Exception) {}
        }
        return result
    }

    fun launch(context: Context, packageName: String, url: String, title: String?): Boolean =
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setPackage(packageName)
                data = Uri.parse(url)
                title?.let { putExtra("title", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: Exception) { false }
}
