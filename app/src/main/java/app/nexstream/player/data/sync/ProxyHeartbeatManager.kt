package app.nexstream.player.data.sync

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import app.nexstream.player.data.ProxySettingsCache
import app.nexstream.player.data.remote.ProxyHeartbeatRequest
import app.nexstream.player.data.remote.ProxyUsageApiService
import app.nexstream.player.license.LicencePreferences
import app.nexstream.player.ui.theme.saveProxyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProxyHeartbeat"
private const val INTERVAL_MS = 5 * 60 * 1000L

@Singleton
class ProxyHeartbeatManager @Inject constructor(
    private val api: ProxyUsageApiService,
    private val licencePreferences: LicencePreferences,
    private val proxySettingsCache: ProxySettingsCache,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private var lastTxBytes = -1L
    private var lastRxBytes = -1L

    fun start() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (true) {
                delay(INTERVAL_MS)
                if (proxySettingsCache.isActive) sendHeartbeat()
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeat() {
        val key = licencePreferences.getLicenceKey()
            ?: licencePreferences.getTrialSyncKey()
            ?: return
        val deviceId = licencePreferences.getOrCreateStableDeviceId()

        val uid = Process.myUid()
        val txNow = TrafficStats.getUidTxBytes(uid)
        val rxNow = TrafficStats.getUidRxBytes(uid)
        val deltaMb = if (lastTxBytes >= 0 && lastRxBytes >= 0 &&
                txNow != TrafficStats.UNSUPPORTED.toLong() &&
                rxNow != TrafficStats.UNSUPPORTED.toLong()) {
            ((txNow - lastTxBytes) + (rxNow - lastRxBytes)).coerceAtLeast(0L) / (1024f * 1024f)
        } else 0f
        lastTxBytes = txNow
        lastRxBytes = rxNow

        try {
            val response = api.heartbeat(
                auth = "Bearer $key",
                body = ProxyHeartbeatRequest(device_id = deviceId, mb = deltaMb),
            )
            if (response.isSuccessful && response.body()?.blocked == true) {
                Log.w(TAG, "Device blocked from proxy by admin — switching to OFF")
                context.saveProxyMode("OFF")
            }
        } catch (e: Exception) {
            Log.w(TAG, "heartbeat failed: ${e.message}")
        }
    }
}
