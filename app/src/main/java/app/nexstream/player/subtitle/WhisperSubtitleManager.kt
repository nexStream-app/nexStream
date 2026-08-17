package app.nexstream.player.subtitle

import android.util.Log
import app.nexstream.player.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperSubtitleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG                  = "WhisperSubtitle"
        private const val TARGET_RATE          = 16000
        private const val WINDOW_SAMPLES       = 48000              // 3-second inference windows
        private const val OVERLAP_SAMPLES      = TARGET_RATE * 1   // 1-second overlap between windows

        private const val GROQ_TRANSCRIBE_URL  = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val GROQ_TRANSLATE_URL   = "https://api.groq.com/openai/v1/audio/translations"
        private const val GROQ_FAST_MODEL      = "whisper-large-v3-turbo"
        private const val GROQ_FULL_MODEL      = "whisper-large-v3"
        private const val MIN_LIVE_INTERVAL_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _isLoading          = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentText        = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _translateToEnglish = MutableStateFlow(false)
    val translateToEnglish: StateFlow<Boolean> = _translateToEnglish.asStateFlow()

    private val _isActive           = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun setTranslateToEnglish(enabled: Boolean) { _translateToEnglish.value = enabled }

    private val audioChannel  = Channel<FloatArray>(capacity = Channel.BUFFERED)
    private var inferenceJob  : Job? = null
    private var clearTextJob  : Job? = null
    @Volatile private var isLiveTV             = false
    @Volatile private var sampleCount          = 0L
    @Volatile private var initialPrompt        : String? = null
    @Volatile private var lastSubtitleResult   = ""
    @Volatile private var liveTvEpgTitle       : String? = null
    @Volatile private var liveTvEpgDesc        : String? = null
    @Volatile private var lastLiveInferenceMs  = 0L

    fun setInitialPrompt(prompt: String?) { initialPrompt = prompt }

    fun setLiveTvContext(epgTitle: String?, epgDescription: String?) {
        liveTvEpgTitle = epgTitle
        liveTvEpgDesc  = epgDescription
    }

    // ── Activation ────────────────────────────────────────────────────────────
    fun activate(isLiveTV: Boolean) {
        this.isLiveTV = isLiveTV
        if (_isActive.value && inferenceJob?.isActive == true) {
            Log.d(TAG, "activate: already active with live loop — endpoint updated to ${if (isLiveTV) "live" else "vod"}")
            return
        }
        _isActive.value = true
        sampleCount = 0L
        drainChannel()
        inferenceJob?.cancel()
        launchInferenceLoop()
        Log.i(TAG, "activate: started (endpoint=${if (isLiveTV) "live" else "vod"})")
    }

    fun deactivate() {
        if (!_isActive.value && inferenceJob == null) return
        _isActive.value      = false
        _currentText.value   = ""
        _isLoading.value     = false
        lastSubtitleResult   = ""
        liveTvEpgTitle       = null
        liveTvEpgDesc        = null
        lastLiveInferenceMs  = 0L
        clearTextJob?.cancel(); clearTextJob = null
        inferenceJob?.cancel(); inferenceJob = null
        drainChannel()
        Log.i(TAG, "deactivate: stopped")
    }

    // ── Audio input (called from WhisperTapProcessor on the audio thread) ─────
    fun processAudio(shorts: ShortArray, inputSampleRate: Int, channelCount: Int) {
        if (!_isActive.value) return
        val chunk = toMono16kFloat(shorts, inputSampleRate, channelCount)
        val count = ++sampleCount
        if (count % 200L == 1L)
            Log.d(TAG, "processAudio: chunk #$count (${chunk.size} samples @ ${TARGET_RATE}Hz)")
        audioChannel.trySend(chunk)
    }

    // ── Private ───────────────────────────────────────────────────────────────
    private fun drainChannel() {
        var n = 0; while (audioChannel.tryReceive().isSuccess) n++
        if (n > 0) Log.d(TAG, "drained $n stale audio chunks")
    }

    private fun launchInferenceLoop() {
        inferenceJob = scope.launch {
            Log.d(TAG, "inferenceLoop: started")
            val buffer = ArrayList<Float>(WINDOW_SAMPLES * 2)
            for (chunk in audioChannel) {
                if (!_isActive.value) break
                chunk.forEach { buffer.add(it) }
                if (buffer.size < WINDOW_SAMPLES) continue

                val samples = FloatArray(WINDOW_SAMPLES) { buffer[it] }
                val overlap = buffer.subList(WINDOW_SAMPLES - OVERLAP_SAMPLES, buffer.size).toMutableList()
                buffer.clear(); buffer.addAll(overlap)

                runInference(samples)
            }
            Log.d(TAG, "inferenceLoop: ended")
        }
    }

    private suspend fun runInference(samples: FloatArray) {
        if (!_isActive.value) return
        if (isLiveTV) {
            val now = System.currentTimeMillis()
            if (now - lastLiveInferenceMs < MIN_LIVE_INTERVAL_MS) return
            lastLiveInferenceMs = now
        }
        val translate = _translateToEnglish.value
        val endpoint  = if (translate) GROQ_TRANSLATE_URL else GROQ_TRANSCRIBE_URL
        val model     = if (translate) GROQ_FULL_MODEL    else GROQ_FAST_MODEL
        val prompt    = if (isLiveTV) buildLiveTvPrompt() else initialPrompt
        Log.i(TAG, "runInference: ${samples.size} samples | translate=$translate | model=$model")
        Log.i(TAG, "runInference: endpoint=$endpoint")
        Log.i(TAG, "runInference: prompt=${if (prompt.isNullOrBlank()) "<none>" else "\"${prompt.take(120)}…\""}")
        _isLoading.value = true
        try {
            val wav    = samplesToWav(samples)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", model)
                .addFormDataPart("temperature", "0")
                .addFormDataPart("response_format", "json")
                .apply { if (!translate) addFormDataPart("language", "en") }
                .apply { if (!prompt.isNullOrBlank()) addFormDataPart("prompt", prompt) }
                .build()
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .post(requestBody)
                .build()
            val text = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: "{}"
                    Log.i(TAG, "Groq response: code=${resp.code} body=${body.take(300)}")
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Groq error ${resp.code}: ${body.take(200)}"); ""
                    } else {
                        JSONObject(body).optString("text", "").trim()
                    }
                }
            }
            if (!_isActive.value) return   // deactivated while the request was in flight
            _isLoading.value = false
            if (text.isBlank()) { Log.d(TAG, "runInference: blank result"); return }
            // Hallucination guard: discard if output is largely composed of words from the prompt
            if (!prompt.isNullOrBlank()) {
                val pWords = prompt.lowercase().split(Regex("[^a-z]+")).filter { it.length > 4 }.toSet()
                val tWords = text.lowercase().split(Regex("[^a-z]+")).filter { it.length > 4 }
                if (tWords.isNotEmpty() && pWords.isNotEmpty()) {
                    val overlap = tWords.count { it in pWords }.toFloat() / tWords.size
                    if (overlap > 0.55f) {
                        Log.d(TAG, "runInference: suppressed hallucination (${(overlap * 100).toInt()}% prompt overlap): \"${text.take(80)}\"")
                        return
                    }
                }
            }
            Log.d(TAG, "Whisper: \"$text\"")
            lastSubtitleResult = text
            _currentText.value = text
            clearTextJob?.cancel()
            clearTextJob = scope.launch { delay(8_000); _currentText.value = "" }
        } catch (e: Exception) {
            _isLoading.value = false
            if (_isActive.value) Log.w(TAG, "runInference error: ${e.message}")
        }
    }

    private fun buildLiveTvPrompt(): String {
        val translate = _translateToEnglish.value
        // In translate mode, don't bias toward English — let Whisper auto-detect the source language.
        val parts = mutableListOf(if (translate) "Live TV broadcast." else "Live TV broadcast. Clear English speech.")
        val title = liveTvEpgTitle
        val desc  = liveTvEpgDesc
        if (!title.isNullOrBlank()) parts.add("Programme: $title.")
        desc?.take(200)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        val last = lastSubtitleResult
        if (last.isNotBlank()) parts.add(last.takeLast(200))
        return parts.joinToString(" ").take(896)
    }

    private fun samplesToWav(samples: FloatArray): ByteArray {
        val pcmLen = samples.size * 2
        val buf = ByteBuffer.allocate(44 + pcmLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + pcmLen)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)              // PCM
        buf.putShort(1)              // mono
        buf.putInt(TARGET_RATE)
        buf.putInt(TARGET_RATE * 2)  // byte rate
        buf.putShort(2)              // block align
        buf.putShort(16)             // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(pcmLen)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        return buf.array()
    }

    private fun toMono16kFloat(shorts: ShortArray, inputRate: Int, channels: Int): FloatArray {
        val mono = when (channels) {
            1    -> FloatArray(shorts.size) { shorts[it] / 32768f }
            2    -> FloatArray(shorts.size / 2) { i ->
                ((shorts[i * 2].toInt() + shorts[i * 2 + 1].toInt()) shr 1) / 32768f
            }
            else -> FloatArray(shorts.size / channels) { i ->
                shorts[i * channels + 2] / 32768f
            }
        }
        if (inputRate == TARGET_RATE) return mono
        val ratio  = inputRate.toDouble() / TARGET_RATE
        val outLen = (mono.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val pos = i * ratio
            val idx = pos.toInt().coerceIn(0, mono.size - 2)
            val a   = mono[idx]; val b = mono[(idx + 1).coerceAtMost(mono.size - 1)]
            (a + (pos - idx).toFloat() * (b - a))
        }
    }
}
