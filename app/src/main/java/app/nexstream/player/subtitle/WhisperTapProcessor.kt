package app.nexstream.player.subtitle

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

@OptIn(UnstableApi::class)
class WhisperTapProcessor(
    private val onSamples: (shorts: ShortArray, sampleRate: Int, channelCount: Int) -> Unit
) : AudioProcessor {

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private val outputQueue = LinkedBlockingQueue<ByteBuffer>(128)
    private var endOfStreamQueued = false
    @Volatile private var tapEnabled = false
    @Volatile private var callCount = 0L
    @Volatile private var nonEmptyCount = 0L

    fun setEnabled(enabled: Boolean) { tapEnabled = enabled }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = inputAudioFormat
        Log.d("WhisperTap", "configure: sampleRate=${inputAudioFormat.sampleRate} " +
                "channels=${inputAudioFormat.channelCount} encoding=${inputAudioFormat.encoding}")
        return inputAudioFormat // transparent passthrough — format unchanged
    }

    // Always active so ExoPlayer always routes audio through us.
    override fun isActive() = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        ++callCount

        if (remaining == 0) {
            // Empty call — padding frame from ExoPlayer; pass through nothing
            outputQueue.offer(AudioProcessor.EMPTY_BUFFER)
            return
        }

        // Log every 100th non-empty call so we can confirm real audio is flowing
        val ne = ++nonEmptyCount
        if (ne % 100L == 1L) {
            Log.d("WhisperTap", "non-empty #$ne: ${remaining}B enc=${inputFormat.encoding} " +
                    "sr=${inputFormat.sampleRate} ch=${inputFormat.channelCount} tap=$tapEnabled")
        }

        val data = ByteArray(remaining)
        inputBuffer.get(data)   // consume from inputBuffer (advances position to limit)

        if (tapEnabled && inputFormat != AudioProcessor.AudioFormat.NOT_SET) {
            val shorts: ShortArray = when (inputFormat.encoding) {
                C.ENCODING_PCM_FLOAT -> {
                    // FFmpeg / float decoder path: 32-bit float → int16
                    val floats = FloatArray(data.size / 4)
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                    ShortArray(floats.size) { (floats[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                }
                else -> {
                    // Standard PCM 16-bit path
                    ShortArray(data.size / 2).also {
                        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it)
                    }
                }
            }
            try { onSamples(shorts, inputFormat.sampleRate, inputFormat.channelCount) }
            catch (_: Exception) {}
        }

        // Passthrough: wrap our copy so downstream (downmixer / AudioTrack) gets the data
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (!outputQueue.offer(buf)) {
            // Queue full — drain oldest entry to make room (prevents stalling playback)
            outputQueue.poll()
            outputQueue.offer(buf)
        }
    }

    override fun queueEndOfStream() { endOfStreamQueued = true }
    override fun getOutput(): ByteBuffer = outputQueue.poll() ?: AudioProcessor.EMPTY_BUFFER
    override fun isEnded() = endOfStreamQueued && outputQueue.isEmpty()
    override fun flush() { endOfStreamQueued = false; outputQueue.clear() }
    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        callCount = 0L
        nonEmptyCount = 0L
        // tapEnabled intentionally not cleared — survives audio pipeline rebuilds on track changes
    }
}
