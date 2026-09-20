package app.nexstream.player.data

import android.content.Context
import app.nexstream.player.ui.theme.getProxyHostFlow
import app.nexstream.player.ui.theme.getProxyModeFlow
import app.nexstream.player.ui.theme.getProxyPasswordFlow
import app.nexstream.player.ui.theme.getProxyPortFlow
import app.nexstream.player.ui.theme.getProxyTypeFlow
import app.nexstream.player.ui.theme.getProxyUsernameFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxySettingsCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile var mode: String = "OFF"
    @Volatile var host: String = ""
    @Volatile var port: Int = 8080
    @Volatile var type: String = "HTTP"
    @Volatile var username: String = ""
    @Volatile var password: String = ""

    val isActive: Boolean get() = mode != "OFF"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { context.getProxyModeFlow().collect     { mode     = it } }
        scope.launch { context.getProxyHostFlow().collect     { host     = it } }
        scope.launch { context.getProxyPortFlow().collect     { port     = it } }
        scope.launch { context.getProxyTypeFlow().collect     { type     = it } }
        scope.launch { context.getProxyUsernameFlow().collect { username = it } }
        scope.launch { context.getProxyPasswordFlow().collect { password = it } }
    }
}
