package app.nexstream.player.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AdminMessage(val title: String, val body: String)

@Singleton
class AdminNotificationManager @Inject constructor() {
    private val _messages = MutableSharedFlow<AdminMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<AdminMessage> = _messages.asSharedFlow()

    fun emit(message: AdminMessage) {
        _messages.tryEmit(message)
    }
}
