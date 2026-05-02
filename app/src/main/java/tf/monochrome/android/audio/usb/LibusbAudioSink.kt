package tf.monochrome.android.audio.usb

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioSink] that hands PCM directly to libusb when the user
 * has Exclusive USB DAC mode on AND the iso pump is live; otherwise
 * delegates everything to a [DefaultAudioSink] (provided by the
 * caller as `delegate`). Wrapping ForwardingAudioSink keeps every
 * one of the ~35 AudioSink methods doing the right thing without
 * us having to re-implement them.
 *
 * Honest scope:
 *  - When bypass is hot, the sink runs the same AudioProcessor chain
 *    that DefaultAudioSink would (mixBus DSP, AutoEQ, parametric EQ,
 *    spectrum FFT, ProjectM tap) inline via [AudioProcessorChain],
 *    then writes the post-DSP PCM to libusb. So EQ + visualizer keep
 *    working in exclusive mode.
 *  - getCurrentPositionUs uses frames-written accounting, ignoring
 *    iso buffer-depth latency (~32 ms at 48 kHz w/ defaults).
 *    A/V sync is "good enough" for music; would need correction for
 *    video lipsync.
 */
@UnstableApi
class LibusbAudioSink(
    delegate: AudioSink,
    private val driver: LibusbUacDriver,
    processors: List<AudioProcessor> = emptyList(),
) : ForwardingAudioSink(delegate) {

    private val chain = AudioProcessorChain(processors)
    private var bypassActive = false
    private var configuredFormat: Format? = null
    private var framesWritten: Long = 0L
    private var startTimeUs: Long = C.TIME_UNSET
    private var pcmBytesPerFrame: Int = 0
    // Throttle for the lazy-engage path so that if driver.start
    // returns false for the current format, we don't hammer it on
    // every handleBuffer (~50× per second) — that flooded logcat
    // with SET_CUR errors during the UAC1 bug. Reset on
    // configure/flush so the next track gets a fresh attempt.
    private var lastEngageFailHash: Int = 0

    // Wedged-iso-pump watchdog. driver.start() can succeed (returns
    // true, _isStreaming flips on) but the iso completion callbacks
    // never actually fire on some Android xHCI controllers — e.g.
    // OnePlus OP611FL1 / CPH2749 in field reports. The user-visible
    // symptom is "USB audio crash after a couple of seconds": the
    // ring fills (~6 sec at 44.1k/16/2ch), driver.write() starts
    // returning 0, ExoPlayer back-pressures, PipelineWatcher floods
    // "pipelineFull (4)" for ~3.5 sec, then everything falls silent
    // because the renderer thread stops feeding a sink that won't
    // drain. There is no Java exception and no native tombstone —
    // just dead audio. To recover, watch driver.playedFrames(): if it
    // hasn't budged for kIsoStallNs after our first successful write
    // we declare the iso pump wedged, latch bypass off, and let
    // ForwardingAudioSink delegate to DefaultAudioSink so audio
    // actually plays. Latch is cleared on flush()/configure() so the
    // next track / next play attempt gets a fresh shot at bypass.
    private var firstWriteNs: Long = 0L
    private var lastPlayedFrames: Long = 0L
    private var lastPlayedAdvanceNs: Long = 0L
    private var watchdogTripped: Boolean = false

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        // Always configure the delegate so [flush] / [reset] / fallback
        // playback all work. The libusb pump only kicks in if start()
        // succeeds; if it doesn't, audio still plays via the delegate.
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        configuredFormat = inputFormat
        lastEngageFailHash = 0   // fresh attempt for the new format

        val rate = inputFormat.sampleRate
        val channels = inputFormat.channelCount
        val bits = pcmBitsFromEncoding(inputFormat.pcmEncoding)
        if (rate <= 0 || channels <= 0 || bits <= 0) {
            bypassActive = false
            return
        }

        // Configure the inline DSP chain with the same input format
        // the renderer is feeding us. Output format may change (e.g.
        // an upsampler would lift the rate); we negotiate the libusb
        // alt setting against the *post-chain* format so the DAC
        // sees what the chain actually produced.
        val chainOut = chain.configure(
            AudioProcessor.AudioFormat(rate, channels, inputFormat.pcmEncoding)
        )
        val outRate = if (chainOut != AudioProcessor.AudioFormat.NOT_SET)
            chainOut.sampleRate else rate
        val outChans = if (chainOut != AudioProcessor.AudioFormat.NOT_SET)
            chainOut.channelCount else channels
        val outBits = if (chainOut != AudioProcessor.AudioFormat.NOT_SET)
            pcmBitsFromEncoding(chainOut.encoding) else bits
        if (outRate <= 0 || outChans <= 0 || outBits <= 0) {
            bypassActive = false
            return
        }
        pcmBytesPerFrame = (outBits / 8) * outChans

        // Track-to-track configure with the same format: skip the
        // stop/start cycle so we don't release the streaming
        // interface (releasing causes the Android kernel to briefly
        // re-grab it, after which the next claim returns BUSY and
        // playback dies until the user re-plugs the DAC).
        bypassActive = when {
            !driver.isOpen.value -> false
            driver.isStreamingFormat(outRate, outBits, outChans) -> {
                Log.i(TAG, "configured: reused active stream "
                    + "($outRate/${outBits}b/${outChans}ch)")
                true
            }
            else -> {
                // Format changed (or first start). driver.start now
                // handles the "already streaming, different format"
                // case internally — stops iso pump and keeps the
                // interface claim, so the kernel can't grab the
                // device in the gap and bounce us with EBUSY.
                driver.start(outRate, outBits, outChans).also { ok ->
                    if (ok) Log.i(TAG, "configured: bypass active " +
                        "(in $rate/${bits}b/${channels}ch → out $outRate/${outBits}b/${outChans}ch)")
                }
            }
        }
        if (bypassActive) {
            framesWritten = 0L
            startTimeUs = C.TIME_UNSET
            resetWatchdog()
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        // Lazy-engage bypass: if the user flipped the toggle AFTER
        // configure() ran for this track (typical case — they enable
        // exclusive mode mid-listen), we still have the format from
        // configure but bypassActive=false. Try to start the iso pump
        // here so the next buffer flows through the DAC. The check is
        // cheap (two atomics + a small int compare) so it's fine on
        // the hot handleBuffer path.
        if (!bypassActive && pcmBytesPerFrame > 0 && driver.isOpen.value) {
            val fmt = configuredFormat
            if (fmt != null) {
                val rate = fmt.sampleRate
                val ch = fmt.channelCount
                val bits = pcmBitsFromEncoding(fmt.pcmEncoding)
                val fmtHash = (rate * 31 + ch) * 31 + bits
                if (rate > 0 && ch > 0 && bits > 0 &&
                    fmtHash != lastEngageFailHash) {
                    bypassActive = if (driver.isStreamingFormat(rate, bits, ch)) {
                        true
                    } else {
                        driver.start(rate, bits, ch)
                    }
                    if (bypassActive) {
                        lastEngageFailHash = 0
                        Log.i(TAG, "lazy-engaged bypass mid-stream " +
                            "($rate/${bits}b/${ch}ch)")
                    } else {
                        // Cache the failed format so we don't retry
                        // until configure or flush clears it. Without
                        // this, every handleBuffer (~50/s) re-tries
                        // and floods logcat with the same error.
                        lastEngageFailHash = fmtHash
                        Log.w(TAG, "bypass engage failed for "
                            + "$rate/${bits}b/${ch}ch — staying on delegate")
                    }
                }
            }
        }

        if (!bypassActive) {
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        if (!buffer.hasRemaining()) return true
        if (startTimeUs == C.TIME_UNSET) startTimeUs = presentationTimeUs

        // Run input through the DSP chain (no-op when no processors
        // are active). Chain returns a buffer owned by the last
        // processor, or EMPTY_BUFFER if it buffered the input
        // waiting for more — in which case our input was consumed
        // (chain advances `buffer`'s position) but no output yet,
        // and we report the buffer fully handled.
        val processed = if (chain.anyActive()) chain.process(buffer)
                        else buffer

        if (!processed.hasRemaining()) {
            return !buffer.hasRemaining()
        }

        // Driver expects a direct, native-byte-order ByteBuffer because
        // the JNI side reads via GetDirectBufferAddress.
        val direct = if (processed.isDirect) processed else copyIntoScratch(processed)
        val framesAvailable = direct.remaining() / pcmBytesPerFrame
        if (framesAvailable <= 0) return !buffer.hasRemaining()

        val written = driver.write(direct, framesAvailable)
        if (written <= 0) {
            // Ring full. If we owned the input buffer (no DSP),
            // back-pressure to the renderer; otherwise the chain
            // already consumed the input and we'd be lying to claim
            // we couldn't take it. Drop this tick's output —
            // underrun handling in the iso pump will pad with
            // silence.
            return processed === buffer && !buffer.hasRemaining()
        }
        if (processed === buffer) {
            buffer.position(buffer.position() + written * pcmBytesPerFrame)
        } else {
            // Advance the processed buffer so the chain sees its
            // output as consumed on the next tick.
            processed.position(processed.position() + written * pcmBytesPerFrame)
        }
        framesWritten += written
        checkIsoPumpWatchdog()
        return !buffer.hasRemaining()
    }

    // Trips bypass off if the iso pump accepted writes but never
    // actually dispatched any frames. See the field-block comment on
    // firstWriteNs for the failure mode this exists to catch.
    private fun checkIsoPumpWatchdog() {
        if (watchdogTripped) return
        val now = System.nanoTime()
        if (firstWriteNs == 0L) {
            firstWriteNs = now
            lastPlayedFrames = driver.playedFrames()
            lastPlayedAdvanceNs = now
            return
        }
        val played = driver.playedFrames()
        if (played > lastPlayedFrames) {
            lastPlayedFrames = played
            lastPlayedAdvanceNs = now
            return
        }
        // Both gates so we don't false-trip on the very first tick
        // (pump hasn't had time to start) or on a brief stall mid-
        // stream that recovers on its own.
        val sinceFirstWriteNs = now - firstWriteNs
        val sinceAdvanceNs = now - lastPlayedAdvanceNs
        if (sinceFirstWriteNs > kIsoWarmupNs && sinceAdvanceNs > kIsoStallNs) {
            Log.w(TAG, "iso pump wedged — playedFrames=$played stuck for " +
                "${sinceAdvanceNs / 1_000_000} ms after $framesWritten frames " +
                "written; falling back to delegate sink. Re-engages on next " +
                "configure/flush.")
            watchdogTripped = true
            bypassActive = false
            // Don't call driver.stop() here — nativeStop() spins up
            // to a second waiting for cancelled transfers to drain,
            // which would glitch the audio thread mid-buffer.
            // ForwardingAudioSink's delegate is already configured
            // with the same format (we always call super.configure
            // in configure()), so the next handleBuffer flows
            // straight to it.
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!bypassActive) return super.getCurrentPositionUs(sourceEnded)
        val rate = configuredFormat?.sampleRate ?: return C.TIME_UNSET
        if (rate <= 0) return C.TIME_UNSET
        // Use frames the iso pump has actually dispatched, not frames
        // we've pushed into the ring — the renderer fills the ring
        // 10× faster than realtime, and reporting written-frames
        // makes ExoPlayer think a 30-second track ended after 3
        // seconds. (Symptom the user saw: 'plays for 5 sec then
        // skips to next track with weird distortion'.)
        val playedUs = driver.playedFrames() * 1_000_000L / rate
        return (if (startTimeUs == C.TIME_UNSET) 0L else startTimeUs) + playedUs
    }

    override fun hasPendingData(): Boolean {
        if (!bypassActive) return super.hasPendingData()
        // Honest answer: we have pending data iff frames are still
        // queued in the ring waiting for the iso pump to dispatch
        // them. Returning `false` unconditionally (the previous
        // impl) made Media3's renderer stop waiting for the sink to
        // drain at end-of-track, which combined with the inflated
        // position made ExoPlayer skip to the next track way before
        // the previous one finished playing.
        return driver.pendingFrames() > 0
    }

    override fun isEnded(): Boolean {
        if (!bypassActive) return super.isEnded()
        // We're ended only when the iso pump has flushed everything
        // we pushed AND the renderer has indicated it's not feeding
        // any more (driver.isStreaming becomes false on stop()).
        return !hasPendingData() && !driver.isStreaming.value
    }

    override fun flush() {
        super.flush()
        chain.flush()
        if (bypassActive) {
            // Drop queued PCM but keep the iso pump running on
            // silence — see LibusbUacDriver.flushRing for why we
            // can't release the interface here without breaking
            // track-to-track playback.
            driver.flushRing()
            framesWritten = 0L
            startTimeUs = C.TIME_UNSET
        }
        lastEngageFailHash = 0
        resetWatchdog()
    }

    override fun reset() {
        super.reset()
        chain.reset()
        if (driver.isStreaming.value) driver.stop()
        bypassActive = false
        framesWritten = 0L
        startTimeUs = C.TIME_UNSET
        resetWatchdog()
    }

    private fun resetWatchdog() {
        firstWriteNs = 0L
        lastPlayedFrames = 0L
        lastPlayedAdvanceNs = 0L
        watchdogTripped = false
    }

    override fun release() {
        super.release()
        if (driver.isStreaming.value) driver.stop()
    }

    private fun copyIntoScratch(buffer: ByteBuffer): ByteBuffer {
        val scratch = ByteBuffer.allocateDirect(buffer.remaining())
            .order(ByteOrder.nativeOrder())
        val mark = buffer.position()
        scratch.put(buffer)
        buffer.position(mark)  // restore — handleBuffer will advance below
        scratch.flip()
        return scratch
    }

    private fun pcmBitsFromEncoding(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> 0   // float / packed / encoded streams not supported on bypass
    }

    companion object {
        private const val TAG = "LibusbAudioSink"
        // Give the iso pump this long to start dispatching frames
        // before we even consider it stuck. The first ~150 ms after
        // configure can legitimately produce zero playedFrames while
        // the URBs prime, the kernel schedules iso slots, and the
        // ring fills up to the silence head-start.
        private const val kIsoWarmupNs: Long = 250_000_000L
        // After warmup, this much time without playedFrames advancing
        // means the pump isn't draining and won't recover. Tuned to
        // be well under the ~3.5 s of back-pressure the wedged-pump
        // bug produces in field logs, so the user gets fallback
        // audio before they notice the dropout.
        private const val kIsoStallNs: Long = 250_000_000L
    }
}
