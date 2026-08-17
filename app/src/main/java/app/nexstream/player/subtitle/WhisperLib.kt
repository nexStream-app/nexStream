package app.nexstream.player.subtitle

import android.util.Log

object WhisperLib {
    private var loaded = false

    init {
        try {
            System.loadLibrary("nexwhisper")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w("WhisperLib", "Native library not available: ${e.message}")
        }
    }

    val isAvailable: Boolean get() = loaded

    /**
     * Load a whisper.cpp GGML model file from disk.
     * Returns a native context pointer, or 0 on failure.
     */
    @JvmStatic external fun initContext(modelPath: String): Long

    /** Free a native context previously returned by initContext. */
    @JvmStatic external fun freeContext(contextPtr: Long)

    /**
     * Signal a running transcribe() call to abort early.
     * Sets an internal flag checked by whisper_full's abort callback.
     * Safe to call from any thread; no-op if no inference is running.
     */
    @JvmStatic external fun abortTranscription()

    /**
     * Transcribe or translate 16kHz mono float32 PCM audio.
     * @param language  BCP-47 hint (e.g. "en"), or empty string for auto-detect
     * @param translate true → output is always English (Whisper's built-in translate task)
     * @param numThreads CPU threads to use (≥1)
     * @return trimmed transcript/translation, or empty string on failure
     */
    @JvmStatic external fun transcribe(
        contextPtr: Long,
        samples: FloatArray,
        language: String,
        translate: Boolean,
        numThreads: Int
    ): String
}
