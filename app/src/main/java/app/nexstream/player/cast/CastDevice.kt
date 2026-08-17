package app.nexstream.player.cast

data class CastDevice(
    val id: String,
    val name: String,
    val type: CastType,
    val host: String,
    val port: Int
)

enum class CastType { CHROMECAST, DLNA, AIRPLAY }

sealed class CastState {
    object Idle : CastState()
    data class Connecting(val device: CastDevice) : CastState()
    data class Active(val device: CastDevice) : CastState()
}
