package tf.monochrome.android.audio.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class MixBusProcessor @Inject constructor() : AudioProcessor {

    private var enginePtr: Long = 0L
    private var pendingFormat = AudioFormat.NOT_SET
    private var inputFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var enabled = false

    // Scratch float arrays — allocated once per format change
    private var scratchInL = FloatArray(0)
    private var scratchInR = FloatArray(0)
    private var scratchOutL = FloatArray(0)
    private var scratchOutR = FloatArray(0)

    // JNI native methods
    private external fun nativeCreate(sampleRate: Int, maxBlockSize: Int): Long
    private external fun nativeDestroy(enginePtr: Long)
    private external fun nativeProcess(
        enginePtr: Long,
        inputL: FloatArray, inputR: FloatArray,
        outputL: FloatArray, outputR: FloatArray,
        numFrames: Int
    )

    external fun nativeSetBusGain(enginePtr: Long, busIndex: Int, gainDb: Float)
    external fun nativeSetBusPan(enginePtr: Long, busIndex: Int, pan: Float)
    external fun nativeSetBusMute(enginePtr: Long, busIndex: Int, muted: Boolean)
    external fun nativeSetBusSolo(enginePtr: Long, busIndex: Int, soloed: Boolean)
    external fun nativeAddPlugin(enginePtr: Long, busIndex: Int, slotIndex: Int, pluginType: Int): Int
    external fun nativeRemovePlugin(enginePtr: Long, busIndex: Int, slotIndex: Int)
    external fun nativeMovePlugin(enginePtr: Long, busIndex: Int, fromSlot: Int, toSlot: Int)
    external fun nativeSetParameter(enginePtr: Long, busIndex: Int, slotIndex: Int, paramIndex: Int, value: Float)
    external fun nativeSetPluginBypassed(enginePtr: Long, busIndex: Int, slotIndex: Int, bypassed: Boolean)
    external fun nativeGetStateJson(enginePtr: Long): String
    external fun nativeLoadStateJson(enginePtr: Long, stateJson: String)

    companion object {
        init {
            System.loadLibrary("monochrome_dsp")
        }
        const val MAX_BLOCK_SIZE = 4096
    }

    fun getEnginePtr(): Long = enginePtr
    fun isEnabled(): Boolean = enabled
    fun setEnabled(e: Boolean) { enabled = e }

    // ── AudioProcessor implementation ────────────────────────────────────

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        // Accept 16-bit PCM or float PCM, stereo
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        pendingFormat = inputAudioFormat
        return inputAudioFormat  // same format in and out
    }

    override fun isActive(): Boolean = enabled && pendingFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled || enginePtr == 0L) {
            // Pass through
            val size = inputBuffer.remaining()
            if (outputBuffer.capacity() < size) {
                outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputFormat.encoding
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = bytesPerSample * 2  // stereo
        val numFrames = inputBuffer.remaining() / frameSize

        if (numFrames <= 0) return

        // Ensure scratch arrays are big enough
        if (scratchInL.size < numFrames) {
            scratchInL = FloatArray(numFrames)
            scratchInR = FloatArray(numFrames)
            scratchOutL = FloatArray(numFrames)
            scratchOutR = FloatArray(numFrames)
        }

        // Deinterleave input to L/R float arrays
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val fb = inputBuffer.asFloatBuffer()
            for (i in 0 until numFrames) {
                scratchInL[i] = fb.get()
                scratchInR[i] = fb.get()
            }
        } else {
            // PCM16
            val sb = inputBuffer.asShortBuffer()
            for (i in 0 until numFrames) {
                scratchInL[i] = sb.get().toFloat() / 32768f
                scratchInR[i] = sb.get().toFloat() / 32768f
            }
        }
        inputBuffer.position(inputBuffer.position() + numFrames * frameSize)

        // Process through native engine
        nativeProcess(enginePtr, scratchInL, scratchInR, scratchOutL, scratchOutR, numFrames)

        // Interleave output back to ByteBuffer
        val outBytes = numFrames * frameSize
        if (outputBuffer.capacity() < outBytes) {
            outputBuffer = ByteBuffer.allocateDirect(outBytes).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (encoding == C.ENCODING_PCM_FLOAT) {
            val fb = outputBuffer.asFloatBuffer()
            for (i in 0 until numFrames) {
                fb.put(scratchOutL[i])
                fb.put(scratchOutR[i])
            }
        } else {
            val sb = outputBuffer.asShortBuffer()
            for (i in 0 until numFrames) {
                sb.put((scratchOutL[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort())
                sb.put((scratchOutR[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        outputBuffer.position(0)
        outputBuffer.limit(outBytes)
    }

    override fun getOutput(): ByteBuffer {
        val buf = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buf
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false

        // (Re)create native engine if format changed
        if (pendingFormat != AudioFormat.NOT_SET) {
            if (enginePtr != 0L) {
                nativeDestroy(enginePtr)
            }
            inputFormat = pendingFormat
            enginePtr = nativeCreate(inputFormat.sampleRate, MAX_BLOCK_SIZE)
        }
    }

    override fun reset() {
        flush()
        if (enginePtr != 0L) {
            nativeDestroy(enginePtr)
            enginePtr = 0L
        }
        pendingFormat = AudioFormat.NOT_SET
        inputFormat = AudioFormat.NOT_SET
        enabled = false
    }
}
