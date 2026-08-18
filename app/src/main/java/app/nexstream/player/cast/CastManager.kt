package app.nexstream.player.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class CastManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _castState = MutableStateFlow<CastState>(CastState.Idle)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, CastDevice>()

    // Chromecast
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var mediaRouter: MediaRouter? = null
    private var routerCallback: MediaRouter.Callback? = null

    // DLNA
    private var ssdpJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var activeDlnaControlUrl: String? = null

    // AirPlay
    private var nsdManager: NsdManager? = null
    private val nsdDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var activeAirPlayHost: String? = null
    private var activeAirPlayPort: Int = 7000

    init {
        try {
            castContext = CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            android.util.Log.d("CastManager", "Google Cast unavailable: ${e.message}")
        }
    }

    // ── Discovery ──────────────────────────────────────────────────────────────

    fun startDiscovery() {
        _isScanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()
        discoverChromecast()
        discoverDlna()
        discoverAirPlay()
    }

    fun stopDiscovery() {
        _isScanning.value = false
        stopChromecastDiscovery()
        stopDlnaDiscovery()
        stopAirPlayDiscovery()
    }

    private fun discoverChromecast() {
        if (castContext == null) return
        try {
            val router = MediaRouter.getInstance(context)
            mediaRouter = router
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                ))
                .build()
            val callback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    if (!route.isDefault) addDevice(CastDevice(route.id, route.name, CastType.CHROMECAST, "", 0))
                }
                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    removeDevice(route.id)
                }
                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    if (!route.isDefault) addDevice(CastDevice(route.id, route.name, CastType.CHROMECAST, "", 0))
                }
            }
            routerCallback = callback
            mainHandler.post {
                router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            }
        } catch (e: Exception) {
            android.util.Log.d("CastManager", "Chromecast discovery error: ${e.message}")
        }
    }

    private fun stopChromecastDiscovery() {
        val router = mediaRouter ?: return
        val cb = routerCallback ?: return
        mainHandler.post { router.removeCallback(cb) }
        routerCallback = null
    }

    private fun discoverDlna() {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("NexStreamDlna")?.also { it.acquire() }
        multicastLock = lock

        ssdpJob = scope.launch {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 3000
                val msg = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
                val data = msg.toByteArray()
                val dest = InetAddress.getByName("239.255.255.250")
                socket.send(DatagramPacket(data, data.size, dest, 1900))

                val buf = ByteArray(4096)
                val pkt = DatagramPacket(buf, buf.size)
                val deadline = System.currentTimeMillis() + 5000L
                while (System.currentTimeMillis() < deadline) {
                    try {
                        socket.receive(pkt)
                        val resp = String(pkt.data, 0, pkt.length)
                        val host = pkt.address.hostAddress ?: continue
                        val location = resp.lines()
                            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                            ?.substringAfter(":")?.trim() ?: continue
                        scope.launch { parseDlnaDevice(host, location) }
                    } catch (_: java.net.SocketTimeoutException) { break }
                }
                socket.close()
            } catch (e: Exception) {
                android.util.Log.d("CastManager", "SSDP error: ${e.message}")
            } finally {
                lock?.release()
                multicastLock = null
            }
        }
    }

    private suspend fun parseDlnaDevice(host: String, locationUrl: String) {
        try {
            val xml = withContext(Dispatchers.IO) {
                val conn = URL(locationUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val text = conn.inputStream.reader().readText()
                conn.disconnect()
                text
            }
            // Case-insensitive matching to handle Samsung/LG XML variations
            val lowerXml = xml.lowercase()

            val fnStart = lowerXml.indexOf("<friendlyname>")
            val fnEnd   = lowerXml.indexOf("</friendlyname>")
            if (fnStart < 0 || fnEnd <= fnStart) return
            val name = xml.substring(fnStart + 14, fnEnd).trim()
            if (name.isBlank()) return

            // Locate the AVTransport service block (case-insensitive service type)
            val avtIdx = lowerXml.indexOf("avtransport:1")
            if (avtIdx < 0) return
            val serviceEnd = lowerXml.indexOf("</service>", avtIdx).takeIf { it > 0 } ?: lowerXml.length
            val lowerBlock = lowerXml.substring(avtIdx, serviceEnd)
            val ctrlStart  = lowerBlock.indexOf("<controlurl>")
            val ctrlEnd    = lowerBlock.indexOf("</controlurl>")
            if (ctrlStart < 0 || ctrlEnd <= ctrlStart) return
            val controlPath = xml.substring(avtIdx + ctrlStart + 12, avtIdx + ctrlEnd).trim()
            if (controlPath.isBlank()) return

            val locUrl = URL(locationUrl)
            val port   = locUrl.port.takeIf { it > 0 } ?: 80
            val base   = "${locUrl.protocol}://$host:$port"
            val controlUrl = if (controlPath.startsWith("/")) "$base$controlPath" else "$base/$controlPath"
            addDevice(CastDevice("dlna-$host", name, CastType.DLNA, controlUrl, port))
        } catch (e: Exception) {
            android.util.Log.d("CastManager", "DLNA parse failed for $locationUrl: ${e.message}")
        }
    }

    private fun stopDlnaDiscovery() {
        ssdpJob?.cancel()
        ssdpJob = null
        multicastLock?.release()
        multicastLock = null
    }

    private fun discoverAirPlay() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, e: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val ip = info.host?.hostAddress ?: return
                        addDevice(CastDevice("airplay-$ip:${info.port}", info.serviceName, CastType.AIRPLAY, ip, info.port))
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                deviceMap.keys.filter { it.startsWith("airplay-") && deviceMap[it]?.name == info.serviceName }
                    .forEach { removeDevice(it) }
            }
        }
        nsdDiscoveryListeners.add(listener)
        nsd.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun stopAirPlayDiscovery() {
        val nsd = nsdManager ?: return
        nsdDiscoveryListeners.forEach { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        nsdDiscoveryListeners.clear()
    }

    // ── Device list ────────────────────────────────────────────────────────────

    private fun addDevice(device: CastDevice) {
        deviceMap[device.id] = device
        _devices.value = deviceMap.values.sortedBy { it.type.ordinal }
    }

    private fun removeDevice(id: String) {
        deviceMap.remove(id)
        _devices.value = deviceMap.values.sortedBy { it.type.ordinal }
    }

    // ── Connect ────────────────────────────────────────────────────────────────

    fun connect(device: CastDevice, url: String, title: String?, positionMs: Long, isLive: Boolean = false) {
        _castState.value = CastState.Connecting(device)
        when (device.type) {
            CastType.CHROMECAST -> connectChromecast(device, url, title, positionMs, isLive)
            CastType.DLNA       -> connectDlna(device, url, positionMs)
            CastType.AIRPLAY    -> connectAirPlay(device, url, positionMs)
        }
    }

    private fun loadToSession(session: CastSession, url: String, title: String?, positionMs: Long, isLive: Boolean) {
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
            .also { title?.let { t -> it.putString(MediaMetadata.KEY_TITLE, t) } }
        val streamType = if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
        val info = MediaInfo.Builder(url)
            .setStreamType(streamType)
            .setMetadata(meta)
            .build()
        session.remoteMediaClient?.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setCurrentTime(if (isLive) 0L else positionMs)
                .setAutoplay(true)
                .build()
        )
    }

    private fun connectChromecast(device: CastDevice, url: String, title: String?, positionMs: Long, isLive: Boolean) {
        val router = mediaRouter ?: run { _castState.value = CastState.Idle; return }
        val route  = router.routes.firstOrNull { it.id == device.id }
            ?: run { _castState.value = CastState.Idle; return }

        // If a session is already active, load media directly without waiting for onSessionStarted
        val existingSession = castContext?.sessionManager?.currentCastSession
        if (existingSession != null && existingSession.isConnected) {
            castSession = existingSession
            _castState.value = CastState.Active(device)
            loadToSession(existingSession, url, title, positionMs, isLive)
            return
        }

        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(s: CastSession) {}
            override fun onSessionStarted(s: CastSession, id: String) {
                castSession = s
                _castState.value = CastState.Active(device)
                loadToSession(s, url, title, positionMs, isLive)
                castContext?.sessionManager?.removeSessionManagerListener(this, CastSession::class.java)
            }
            override fun onSessionStartFailed(s: CastSession, e: Int) {
                _castState.value = CastState.Idle
                castContext?.sessionManager?.removeSessionManagerListener(this, CastSession::class.java)
            }
            override fun onSessionEnding(s: CastSession) {}
            override fun onSessionEnded(s: CastSession, e: Int) {
                if (_castState.value is CastState.Active) _castState.value = CastState.Idle
            }
            override fun onSessionResuming(s: CastSession, id: String) {}
            override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) {}
            override fun onSessionResumeFailed(s: CastSession, e: Int) {}
            override fun onSessionSuspended(s: CastSession, reason: Int) {}
        }
        castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        mainHandler.post { router.selectRoute(route) }
    }

    private fun connectDlna(device: CastDevice, url: String, positionMs: Long) {
        val controlUrl = device.host
        scope.launch {
            try {
                val escapedUrl = url.replace("&", "&amp;")
                val metadata = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" +
                    " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
                    " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                    "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                    "<dc:title>Stream</dc:title>" +
                    "<upnp:class>object.item.videoItem</upnp:class>" +
                    "<res protocolInfo=\"http-get:*:video/mpeg:*\">$escapedUrl</res>" +
                    "</item></DIDL-Lite>"
                soapPost(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body><u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                    "<InstanceID>0</InstanceID>" +
                    "<CurrentURI>$escapedUrl</CurrentURI>" +
                    "<CurrentURIMetaData><![CDATA[$metadata]]></CurrentURIMetaData>" +
                    "</u:SetAVTransportURI></s:Body></s:Envelope>"
                )
                if (positionMs > 0) {
                    soapPost(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Seek",
                        """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${msToHms(positionMs)}</Target></u:Seek></s:Body></s:Envelope>"""
                    )
                }
                soapPost(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play",
                    """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play></s:Body></s:Envelope>"""
                )
                activeDlnaControlUrl = controlUrl
                _castState.value = CastState.Active(device)
            } catch (e: Exception) {
                android.util.Log.e("CastManager", "DLNA connect failed: ${e.message}")
                _castState.value = CastState.Idle
            }
        }
    }

    private fun connectAirPlay(device: CastDevice, url: String, positionMs: Long) {
        scope.launch {
            try {
                val body = "Content-Location: $url\nStart-Position: ${positionMs / 1000.0}\n"
                val conn = URL("http://${device.host}:${device.port}/play").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "text/parameters")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    activeAirPlayHost = device.host
                    activeAirPlayPort = device.port
                    _castState.value = CastState.Active(device)
                } else {
                    _castState.value = CastState.Idle
                }
            } catch (e: Exception) {
                android.util.Log.e("CastManager", "AirPlay connect failed: ${e.message}")
                _castState.value = CastState.Idle
            }
        }
    }

    // ── Playback control ───────────────────────────────────────────────────────

    fun play() {
        when (val s = _castState.value) {
            is CastState.Active -> when (s.device.type) {
                CastType.CHROMECAST -> castSession?.remoteMediaClient?.play()
                CastType.DLNA       -> activeDlnaControlUrl?.let { scope.launch { soapPost(it, "urn:schemas-upnp-org:service:AVTransport:1#Play", dlnaPlayBody()) } }
                CastType.AIRPLAY    -> scope.launch { airPlayRate(1.0) }
            }
            else -> Unit
        }
    }

    fun pause() {
        when (val s = _castState.value) {
            is CastState.Active -> when (s.device.type) {
                CastType.CHROMECAST -> castSession?.remoteMediaClient?.pause()
                CastType.DLNA       -> activeDlnaControlUrl?.let { scope.launch { soapPost(it, "urn:schemas-upnp-org:service:AVTransport:1#Pause", """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:Pause></s:Body></s:Envelope>""") } }
                CastType.AIRPLAY    -> scope.launch { airPlayRate(0.0) }
            }
            else -> Unit
        }
    }

    fun seekTo(positionMs: Long) {
        when (val s = _castState.value) {
            is CastState.Active -> when (s.device.type) {
                CastType.CHROMECAST -> castSession?.remoteMediaClient?.seek(
                    MediaSeekOptions.Builder().setPosition(positionMs).build()
                )
                CastType.DLNA -> activeDlnaControlUrl?.let { url ->
                    scope.launch {
                        soapPost(url, "urn:schemas-upnp-org:service:AVTransport:1#Seek",
                            """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${msToHms(positionMs)}</Target></u:Seek></s:Body></s:Envelope>"""
                        )
                    }
                }
                CastType.AIRPLAY -> scope.launch { airPlayScrub(positionMs / 1000.0) }
            }
            else -> Unit
        }
    }

    fun getApproximatePositionMs(): Long {
        return if (_castState.value is CastState.Active &&
            (_castState.value as CastState.Active).device.type == CastType.CHROMECAST
        ) {
            castSession?.remoteMediaClient?.approximateStreamPosition ?: 0L
        } else 0L
    }

    fun disconnect() {
        when (val s = _castState.value) {
            is CastState.Active -> when (s.device.type) {
                CastType.CHROMECAST -> castContext?.sessionManager?.endCurrentSession(true)
                CastType.DLNA       -> {
                    activeDlnaControlUrl?.let { url ->
                        scope.launch {
                            try { soapPost(url, "urn:schemas-upnp-org:service:AVTransport:1#Stop", """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:Stop></s:Body></s:Envelope>""") }
                            catch (_: Exception) {}
                        }
                    }
                    activeDlnaControlUrl = null
                }
                CastType.AIRPLAY -> {
                    val host = activeAirPlayHost
                    val port = activeAirPlayPort
                    if (host != null) {
                        scope.launch {
                            try {
                                val conn = URL("http://$host:$port/stop").openConnection() as HttpURLConnection
                                conn.requestMethod = "POST"; conn.connect(); conn.disconnect()
                            } catch (_: Exception) {}
                        }
                    }
                    activeAirPlayHost = null
                }
            }
            else -> Unit
        }
        _castState.value = CastState.Idle
    }

    fun destroy() {
        stopDiscovery()
        scope.cancel()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun soapPost(url: String, action: String, body: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPACTION", "\"$action\"")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) android.util.Log.w("CastManager", "SOAP $action returned $code")
        conn.disconnect()
    }

    private fun dlnaPlayBody() =
        """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play></s:Body></s:Envelope>"""

    private fun airPlayRate(rate: Double) {
        val host = activeAirPlayHost ?: return
        try {
            val conn = URL("http://$host:$activeAirPlayPort/rate?value=$rate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.connect(); conn.disconnect()
        } catch (_: Exception) {}
    }

    private fun airPlayScrub(seconds: Double) {
        val host = activeAirPlayHost ?: return
        try {
            val conn = URL("http://$host:$activeAirPlayPort/scrub?position=$seconds").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.connect(); conn.disconnect()
        } catch (_: Exception) {}
    }

    private fun msToHms(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return "%02d:%02d:%02d".format(h, m, sec)
    }
}
