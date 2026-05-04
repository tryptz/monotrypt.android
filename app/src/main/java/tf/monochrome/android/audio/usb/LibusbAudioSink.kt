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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Media3 [AudioSink] that hands PCM directly to libusb when the user
 * has Exclusive USB DAC mode on AND the iso pump is live; otherwise
 * delegates to whatever sink the caller passed in.
 *
 * The choice of delegate is made once at renderer-build time by
 * [tf.monochrome.android.player.PlaybackService.buildAudioSink]:
 *
 *  - **Exclusive ON** → `delegate` is a [NoOpAudioSink]. No
 *    `AudioTrack` is ever created. The libusb iso pump owns the USB
 *    streaming interface alone. Bypass-or-nothing semantics: if the
 *    iso pump can't engage (e.g. driver.start fails), the NoOp
 *    swallows audio → silence, and the user sees the failure reason
 *    in the Settings status card. Better than fighting the bus with
 *    an AudioTrack the kernel can't honor (which produced the
 *    "KKDDRDRRDRRDLDLLDLLD" stutter that motivated this design).
 *  - **Exclusive OFF** → `delegate` is a real [DefaultAudioSink]
 *    with the full processor chain. AudioTrack handles output via
 *    AudioFlinger.
 *
 * Switching between the two requires a full ExoPlayer rebuild
 * (`buildAudioSink` runs once per player build, not per prepare).
 * Currently we don't do that automatically — the toggle change takes
 * effect at next app start. A live-rebuild watcher is a viable
 * follow-up if the UX bites; the design is intentionally kept
 * simple here because the previous attempts at hot-swap delegates
 * reintroduced exactly the dual-AudioTrack contention bug we're
 * fixing.
 *
 * Honest scope:
 *  - When bypass is hot, the sink runs the same AudioProcessor chain
 *    that DefaultAudioSink would (mixBus DSP, AutoEQ, parametric EQ,
 *    spectrum FFT) inline via [AudioProcessorChain], then writes the
 *    post-DSP PCM to libusb. ProjectM tap is intentionally excluded
 *    from the bypass chain (visualizer bus can block its consumer
 *    and we can't pay that cost on the renderer thread).
 *  - getCurrentPositionUs uses played-frames accounting from
 *    [LibusbUacDriver.playedFrames], including silence padding —
 *    that's intentional so the renderer doesn't see "sink stalled"
 *    during legitimate underrun pads.
 *  - Playback speed has no audible effect while exclusive is on.
 *    The DefaultAudioSink path uses SonicAudioProcessor internally
 *    for time-stretch; we don't include it in the bypass chain.
 *    [NoOpAudioSink.setPlaybackParameters] still round-trips the
 *    user's value so ExoPlayer's state machine is satisfied — but
 *    the iso pump only knows how to write at the source rate. Speed
 *    slider works again when the user toggles exclusive off.
 */
@UnstableApi
class LibusbAudioSink(
    delegate: AudioSink,
    private val driver: LibusbUacDriver,
    private val volumeController: BypassVolumeController,
    processors: List<AudioProcessor> = emptyList(),
) : ForwardingAudioSink(delegate) {

    /**
     * Phase-A renderer state-trace log. When [traceEnabled] is true,
     * every state-machine boundary Media3 walks through emits a
     * structured logcat line tagged [TRACE_TAG]. The output is
     * grep-friendly key=value pairs so a captured logcat can be
     * reduced to a state timeline by `grep -F LibusbSinkTrace`.
     *
     * The flag is `const`-style here rather than gated on a
     * BuildConfig boolean because Phase-A instrumentation is
     * intentionally on for the duration of this branch — turning it
     * off requires a deliberate edit, which prevents it from being
     * accidentally enabled in production by a config oversight.
     * Phase D will move it behind a debug build variant.
     */
    private val traceEnabled = true

    private inline fun trace(msg: () -> String) {
        if (traceEnabled) Log.v(TRACE_TAG, msg())
    }

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
    // True between the first successful write of a stream and the
    // next flush/configure/reset. Used solely to log a one-shot
    // diagnostic ("first bypass write succeeded") so a build that
    // includes this code path is visibly distinct in logcat from
    // an older build that doesn't — saves a "did you rebuild?"
    // round-trip when triaging field reports.
    private var firstWriteLogged: Boolean = false

    // Reusable scratch for the software-gain path. Only allocated /
    // grown when the user actually attenuates (volume < 1.0f); at
    // unity the bypass stays bit-perfect — direct buffer goes
    // straight to libusb with no copy, no allocation, no rounding.
    private var gainScratch: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    // Bit depth is per-stream; cache so the gain path doesn't have
    // to recompute pcmBitsFromEncoding on every handleBuffer.
    private var outBitsPerSample: Int = 0

    // Latch so the trace log records isEnded becoming true exactly
    // once per stream, rather than on every poll (which the renderer
    // does at high frequency). Reset on flush/reset/configure so the
    // next stream gets its own one-shot edge log.
    private var endedLogged: Boolean = false

    // Phase B: end-of-stream contract flag. ExoPlayer signals
    // "no more buffers will be supplied for this configuration" by
    // calling playToEndOfStream() exactly once, then polls isEnded()
    // until it returns true. The previous bypass implementation
    // tied isEnded to driver.isStreaming.value, which never goes
    // false on natural EOS (only on stop/reset/release), so single-
    // track sessions hung indefinitely with the position frozen at
    // the end of the track. The fix is to latch this flag on the
    // override, and key isEnded off "EOS signaled AND ring drained"
    // — the natural definition that ExoPlayer's other audio sink
    // implementations use. Reset on configure/flush/reset so the
    // next stream re-arms. Marked @Volatile because it's set on the
    // audio thread (playToEndOfStream) and read on the renderer's
    // poll thread (isEnded).
    @Volatile
    private var playingToEndOfStream: Boolean = false

    // Phase B: iso pump retry guard. When the watchdog detects a
    // stall, the previous behaviour was to flip bypassActive=false
    // and fall through to the delegate — which in exclusive mode is
    // NoOpAudioSink and produces permanent silence with no recovery.
    // The retry path stops and re-starts the pump on a background
    // thread (because stop() spins waiting for cancelled URBs and
    // must not block the audio thread). pumpRetryInFlight prevents
    // re-triggering while a retry is already running, and
    // watchdogTripped is now reserved for "retry was attempted and
    // also failed" — the genuinely-give-up state.
    @Volatile
    private var pumpRetryInFlight: Boolean = false

    // Phase D: configuration generation counter. Every call to
    // configure() bumps this, which is the canonical "the renderer's
    // intent has changed" signal. The recovery worker captures the
    // generation at scheduling time and refuses to call driver.start
    // if the generation has advanced — which would mean the format
    // it captured is stale and starting the pump for it would race
    // with whatever new format the renderer is about to push. The
    // race window is real: between watchdog trip on the audio
    // thread and driver.start on the recovery thread, a track
    // change with a different sample rate can arrive and call
    // configure() concurrently. Without this guard the recovery
    // thread would bring the pump up at the OLD rate, the
    // renderer's next handleBuffer would feed NEW-rate frames into
    // a ring sized for the old, and the user would hear pitched-up
    // audio or rapid stutter depending on which size won.
    //
    // Atomic Int rather than Volatile because we need both
    // increment and compare-load semantics — Volatile alone would
    // give us correct visibility but no atomic increment. The
    // performance cost is one CAS per configure(), which happens
    // at most a few times per second; nothing on the hot path
    // touches this field.
    private val configGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    // Single-thread executor used only for iso pump recovery work.
    // Rare events; we don't need a pooled scope or a heavyweight
    // CoroutineScope. The thread is daemon so it doesn't block
    // process exit, named explicitly so a thread dump pinpoints
    // any pathological recovery loop, and shut down on release().
    private val recoveryExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "BypassPumpRecovery").apply { isDaemon = true }
        }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        trace {
            "configure inputRate=${inputFormat.sampleRate} " +
            "ch=${inputFormat.channelCount} " +
            "enc=${inputFormat.pcmEncoding} " +
            "bufSize=$specifiedBufferSize " +
            "outCh=${outputChannels?.joinToString() ?: "null"} " +
            "driverOpen=${driver.isOpen.value} " +
            "driverStreaming=${driver.isStreaming.value}"
        }
        // Always configure the delegate so [flush] / [reset] / fallback
        // playback all work. The libusb pump only kicks in if start()
        // succeeds; if it doesn't, audio still plays via the delegate.
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        configuredFormat = inputFormat
        lastEngageFailHash = 0   // fresh attempt for the new format
        endedLogged = false      // re-arm the one-shot trace edge
        playingToEndOfStream = false  // re-arm EOS contract for new stream
        // Phase D: bump the configuration generation. The recovery
        // worker reads this when it's about to call driver.start; if
        // the value has changed since the watchdog scheduled the
        // recovery, the recovery aborts because its captured format
        // is no longer what the renderer wants. Bump AFTER the
        // configuration writes above are done, so a recovery worker
        // that observes the new generation also observes the new
        // configuredFormat consistently.
        configGeneration.incrementAndGet()

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
        outBitsPerSample = outBits

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
                        trace { "engage_lazy ok=true rate=$rate bits=$bits ch=$ch" }
                    } else {
                        // Cache the failed format so we don't retry
                        // until configure or flush clears it. Without
                        // this, every handleBuffer (~50/s) re-tries
                        // and floods logcat with the same error.
                        lastEngageFailHash = fmtHash
                        Log.w(TAG, "bypass engage failed for "
                            + "$rate/${bits}b/${ch}ch — staying on delegate")
                        trace { "engage_lazy ok=false rate=$rate bits=$bits ch=$ch" }
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

        // Software volume on the bypass path. AudioFlinger's
        // master-volume / hardware-volume-key apparatus doesn't reach
        // the DAC here, so without this attenuation the user has no
        // way to lower the level short of yanking the cable.
        // BypassVolumeController is fed by the UI slider
        // (PlayerViewModel.setVolume), ReplayGain
        // (PlaybackService.applyReplayGain), and the hardware key
        // dispatcher (MainActivity.dispatchKeyEvent). At unity gain
        // we feed `direct` straight to libusb to preserve bit-perfect
        // output. Below unity, Phase B added paths for all three
        // bit depths the renderer produces (16/24/32) — previously
        // only 16-bit had a path and 24/32-bit silently kept unity
        // gain, making the volume slider appear broken for the
        // Bathys's default 24-bit format.
        val gain = volumeController.getVolume()
        val toWrite = if (gain >= 0.9999f) {
            direct
        } else when (outBitsPerSample) {
            16 -> applyGainPcm16(direct, gain)
            24 -> applyGainPcm24(direct, gain)
            32 -> applyGainPcm32(direct, gain)
            // Fall back to unity for unknown depths rather than
            // emitting silence; the user will hear something, and
            // the log will show the gap so we can add coverage.
            else -> {
                Log.w(TAG, "no gain path for ${outBitsPerSample}b PCM — " +
                    "passing through at unity gain ($gain requested)")
                direct
            }
        }
        val written = driver.write(toWrite, framesAvailable)
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
        if (!firstWriteLogged) {
            firstWriteLogged = true
            Log.i(TAG, "bypass first write succeeded — wrote $written frames " +
                "at ${outBitsPerSample}b, gain=$gain")
        }
        checkIsoPumpWatchdog()
        return !buffer.hasRemaining()
    }

    // Pause/play: ExoPlayer's DefaultAudioSink.pause() calls
    // AudioTrack.pause(), which immediately mutes the hardware
    // regardless of what's queued. Our libusb path has no such
    // primitive — the iso pump keeps draining the ring on its own
    // thread until it goes empty, so without intervention the user
    // hears up to a full ring's worth of audio (multiple seconds
    // depending on encoding) AFTER they hit pause. Mute by
    // dropping queued PCM so the iso pump immediately starts
    // padding silence on its next packet.
    //
    // We do NOT call driver.stop() here — stopIsoPump spins for up
    // to a second waiting for cancelled URBs to drain (see
    // libusb_uac_driver.cpp:stopIsoPump), which would block the
    // pause-button latency on the audio thread. Keeping the iso
    // pump alive over pause also means resume() doesn't need to
    // re-claim the streaming interface, which is the slow path
    // that occasionally wedges with EBUSY when snd-usb-audio
    // re-attaches in the gap.
    override fun playToEndOfStream() {
        // Phase B: latch the EOS signal so isEnded() can honestly
        // report when both EOS has been signaled AND the ring has
        // drained. Without this latch, isEnded would never return
        // true on a natural end-of-stream because driver.isStreaming
        // stays true until stop() — which the renderer never calls
        // before isEnded returns true, deadlocking the contract.
        // Trace records the signal so the post-fix log shows the
        // configure → handleBuffer → playToEndOfStream → isEnded
        // sequence completing cleanly.
        if (bypassActive) {
            playingToEndOfStream = true
        }
        trace { "playToEndOfStream bypassActive=$bypassActive ring_pending=${driver.pendingFrames()}" }
        super.playToEndOfStream()
    }

    override fun pause() {
        trace { "pause bypassActive=$bypassActive ring_pending=${driver.pendingFrames()}" }
        super.pause()
        if (bypassActive) {
            // Phase B: soft-mute. The previous path called
            // driver.flushRing(), which discarded the queued PCM
            // and lost ~80 ms of audio on every pause/resume cycle
            // at 44.1k/16-bit/2ch (the kRingTargetMs window). Soft-
            // mute preserves the queued PCM and just suppresses
            // audio at the consumer side; resume picks up exactly
            // where pause left off, with no audible drop.
            driver.setMuted(true)
        }
    }

    override fun play() {
        trace { "play bypassActive=$bypassActive" }
        super.play()
        if (bypassActive) {
            driver.setMuted(false)
            // Watchdog reset is no longer required here — the iso
            // pump kept running through pause and playedFrames kept
            // advancing (the soft-mute path counts emitted silence
            // toward playedFrames), so lastPlayedAdvanceNs is fresh
            // and the watchdog won't false-trip on resume. We do
            // still clear startTimeUs so position reporting
            // re-anchors to the resumed write point.
            startTimeUs = C.TIME_UNSET
        }
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
        // != not > : driver.flushRing() (called on our own pause()
        // override and on Media3-driven flushes) zeros the C++ side
        // playedFrames_ counter, so a healthy pump can legitimately
        // produce played < lastPlayedFrames right after a flush.
        // Treating that as "stuck" causes a false trip + fallback to
        // the delegate sink mid-stream — which the user perceives
        // as audio "fighting" itself between two paths every time
        // they pause / skip / seek. Any change at all means the
        // pump is alive; only zero-progress is a genuine wedge.
        if (played != lastPlayedFrames) {
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
            // Phase B: previously this branch flipped bypassActive
            // to false and let handleBuffer fall through to the
            // delegate sink — which in exclusive mode is NoOp, so
            // the user heard permanent silence with no recovery
            // until they manually flipped exclusive off. The new
            // behaviour stops the iso pump and restarts it with the
            // same negotiated format on a background thread, so a
            // transient stall (CPU starvation, brief bus hiccup,
            // kernel scheduler pause) recovers automatically with
            // a brief silence gap rather than a permanent one. The
            // background thread is required because driver.stop()
            // can spin up to a second waiting for cancelled URBs;
            // running it on the audio thread would risk an ANR.
            // pumpRetryInFlight de-dupes — without it the watchdog
            // would re-fire every 50 ms while the recovery thread
            // is still running and we'd queue a retry storm.
            if (!pumpRetryInFlight && !watchdogTripped) {
                pumpRetryInFlight = true
                val fmt = configuredFormat
                // Phase D: capture the configuration generation at
                // the moment the watchdog decides to recover. The
                // recovery worker will compare against the live
                // counter just before calling driver.start; if a
                // configure() ran in between, the captured fmt is
                // stale and the recovery aborts (a fresh
                // lazy-engage on the next handleBuffer will bring
                // the pump up at the correct format).
                val genAtSchedule = configGeneration.get()
                Log.w(TAG, "iso pump wedged — playedFrames=$played stuck for " +
                    "${sinceAdvanceNs / 1_000_000} ms after $framesWritten frames " +
                    "written; scheduling pump restart")
                trace {
                    "watchdog_trip played=$played stalled_ms=${sinceAdvanceNs / 1_000_000} " +
                    "since_first_write_ms=${sinceFirstWriteNs / 1_000_000} " +
                    "frames_written=$framesWritten retry=scheduled gen=$genAtSchedule"
                }
                bypassActive = false
                if (fmt != null) {
                    recoveryExecutor.execute { recoverIsoPump(fmt, genAtSchedule) }
                } else {
                    // No format known — can't restart. Fall back to
                    // the previous "give up" behaviour so we don't
                    // pretend recovery is happening.
                    watchdogTripped = true
                    pumpRetryInFlight = false
                }
            }
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
        // Phase B: ended ⟺ EOS signaled AND ring drained. The
        // previous predicate "(!hasPendingData && !driver.isStreaming)"
        // was wrong because driver.isStreaming stays true on natural
        // EOS — nothing calls stop() when the source signals it's
        // done feeding. The renderer polls isEnded after calling
        // playToEndOfStream and won't transition the player to its
        // ENDED state until this returns true; the previous logic
        // hung the player at the end of every single-track session.
        //
        // The drained check is hasPendingData(), which compares
        // writtenFrames (host pushed into ring) against playedFrames
        // (iso pump dispatched to DAC). When those equal each other
        // and EOS was signaled, every byte the renderer fed us has
        // been sent to the device. Note this does NOT wait for the
        // device's own analog FIFO to drain — that's a few
        // milliseconds we accept as imprecision; chasing it would
        // require a vendor-specific clock-recovery query the UAC
        // spec doesn't standardize.
        val ended = playingToEndOfStream && !hasPendingData()
        if (ended && !endedLogged) {
            endedLogged = true
            trace { "isEnded -> true frames_written=$framesWritten played=${driver.playedFrames()}" }
        }
        return ended
    }

    override fun flush() {
        trace {
            "flush bypassActive=$bypassActive ring_pending=${driver.pendingFrames()}"
        }
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
        playingToEndOfStream = false  // gapless track transitions re-arm
        endedLogged = false
        resetWatchdog()
    }

    override fun reset() {
        trace { "reset bypassActive=$bypassActive driverStreaming=${driver.isStreaming.value}" }
        super.reset()
        chain.reset()
        if (driver.isStreaming.value) driver.stop()
        bypassActive = false
        framesWritten = 0L
        startTimeUs = C.TIME_UNSET
        playingToEndOfStream = false
        endedLogged = false
        resetWatchdog()
    }

    private fun resetWatchdog() {
        firstWriteNs = 0L
        lastPlayedFrames = 0L
        lastPlayedAdvanceNs = 0L
        watchdogTripped = false
        firstWriteLogged = false
    }

    override fun release() {
        trace { "release driverStreaming=${driver.isStreaming.value}" }
        super.release()
        if (driver.isStreaming.value) driver.stop()
        // Phase B: shut down the recovery executor so the daemon
        // thread doesn't outlive the sink. shutdownNow rather than
        // shutdown because release() means we're tearing down for
        // good; a recovery in flight should be abandoned, not
        // awaited. The thread is daemon so process exit isn't
        // affected either way, but explicit shutdown is cleaner
        // and surfaces in thread dumps as terminated.
        recoveryExecutor.shutdownNow()
    }

    /**
     * Phase B: iso pump recovery worker. Runs on [recoveryExecutor],
     * never on the audio thread. Stops the wedged pump, re-starts it
     * with the same negotiated format, and on success leaves the
     * sink in a state where the next [handleBuffer] will lazy-engage
     * bypass exactly as it does after a normal configure().
     *
     * Phase D: takes a [generationAtSchedule] parameter that the
     * watchdog captured at the moment of the trip. We re-check the
     * live [configGeneration] just before calling driver.start; if
     * a configure() ran in between the trip and now, the captured
     * format is stale, and bringing the pump up at it would race
     * with whatever new format the renderer is about to push. In
     * that case we abort the recovery — the next handleBuffer's
     * lazy-engage will bring the pump up at the new format.
     *
     * On hard failure (the device is genuinely gone, or the kernel
     * re-grabbed the streaming interface during the gap),
     * [watchdogTripped] is set so the sink stops attempting recovery
     * for this configured format — re-engaging would just hit the
     * same failure. The next [configure] or [flush] call clears the
     * trip and gives recovery another shot.
     */
    private fun recoverIsoPump(fmt: Format, generationAtSchedule: Int) {
        try {
            // Stop the wedged pump. Even if its internal state is
            // confused, stop() unconditionally cancels every URB
            // and joins the event thread, then releases the
            // streaming interface. The brief 100 ms wait gives the
            // kernel time to settle before we re-claim — without
            // it, the next libusb_claim_interface occasionally
            // fails with EBUSY because snd-usb-audio briefly
            // re-attaches in the gap before we've fully released.
            driver.stop()
            Thread.sleep(100L)

            // Phase D: format-change race check. The renderer might
            // have called configure() with a different format while
            // we were asleep. If so, abandon — the new lazy-engage
            // on the next handleBuffer will bring the pump up at
            // the renderer's current format. Without this check we
            // would commit to the stale format and the renderer
            // would feed wrong-sized buffers into the ring.
            val liveGeneration = configGeneration.get()
            if (liveGeneration != generationAtSchedule) {
                Log.i(TAG, "iso pump recovery aborted — format changed " +
                    "during recovery window (gen $generationAtSchedule -> " +
                    "$liveGeneration). Lazy-engage on next handleBuffer " +
                    "will bring up the new format.")
                trace {
                    "watchdog_recover ok=aborted reason=format_changed " +
                    "gen_sched=$generationAtSchedule gen_live=$liveGeneration"
                }
                return
            }

            val rate = fmt.sampleRate
            val ch = fmt.channelCount
            val bits = pcmBitsFromEncoding(fmt.pcmEncoding)
            val ok = if (rate > 0 && ch > 0 && bits > 0)
                driver.start(rate, bits, ch) else false
            if (ok) {
                // The pump is alive again. Reset the watchdog so
                // the new pump's first write isn't immediately
                // judged against the old pump's stalled timestamps.
                // Don't re-engage bypass here — let lazy-engage
                // do it on the next handleBuffer, which keeps the
                // engage path consistent with cold start.
                resetWatchdog()
                Log.i(TAG, "iso pump recovered ($rate/${bits}b/${ch}ch)")
                trace { "watchdog_recover ok=true rate=$rate bits=$bits ch=$ch" }
            } else {
                // Genuine failure — give up on automatic recovery
                // for this configured format. The next configure()
                // or flush() will clear watchdogTripped and the
                // engage path will try again.
                watchdogTripped = true
                Log.w(TAG, "iso pump recovery failed " +
                    "($rate/${bits}b/${ch}ch); audio will route to " +
                    "delegate until configure/flush retries.")
                trace { "watchdog_recover ok=false rate=$rate bits=$bits ch=$ch" }
            }
        } catch (t: Throwable) {
            // Catch-all: an exception on the recovery thread must
            // not propagate to crash the app. Log, mark the trip
            // permanent for this configured format, clear the
            // in-flight flag so a future configure can try again.
            Log.e(TAG, "iso pump recovery threw", t)
            watchdogTripped = true
            trace { "watchdog_recover ok=false error=${t.javaClass.simpleName}" }
        } finally {
            pumpRetryInFlight = false
        }
    }

    // PCM16 software gain. Reads signed 16-bit samples from `src`
    // (native byte order — set on every direct buffer we touch), does
    // one float multiply per sample, clamps at ±0x7FFF to match the
    // PCM16 range exactly, and writes the result to a reusable direct
    // scratch buffer that's safe to hand to libusb. Returned buffer
    // is positioned at 0 / limited to the byte count actually written
    // so driver.write sees the whole payload. Source position is
    // intentionally NOT advanced — handleBuffer's existing accounting
    // advances `processed` (or `buffer`) by `written * pcmBytesPerFrame`
    // after the write returns.
    //
    // No dither: attenuation is monotonic, so the LSB error is
    // deterministic and quieter than a pre-existing dither floor on
    // the upstream MixBusProcessor. Adding TPDF here would just
    // double-dither.
    private fun applyGainPcm16(src: ByteBuffer, gain: Float): ByteBuffer {
        val srcPos = src.position()
        val srcLimit = src.limit()
        val totalBytes = srcLimit - srcPos
        val scratch = ensureGainScratch(totalBytes)
        val numSamples = totalBytes / 2
        for (i in 0 until numSamples) {
            val s = src.getShort(srcPos + i * 2).toInt()
            val scaled = (s * gain).toInt().coerceIn(-32768, 32767)
            scratch.putShort(i * 2, scaled.toShort())
        }
        scratch.position(0)
        scratch.limit(numSamples * 2)
        return scratch
    }

    /**
     * Phase B: attenuate packed 24-bit little-endian PCM in place to
     * a fresh scratch buffer.
     *
     * ExoPlayer's `C.ENCODING_PCM_24BIT` is a packed format: three
     * bytes per sample, little-endian, with the sign bit at the top
     * of the third byte. The wire format is [LSB, mid, MSB] with bit
     * 7 of MSB carrying the sign. Reading the sample requires
     * assembling the three bytes and sign-extending the top byte
     * into the upper byte of the int; writing it back requires
     * splitting an int back into the same packed layout.
     *
     * No dither and no double-precision needed because the input is
     * already at 24-bit resolution and float gain has 24 bits of
     * mantissa precision — the multiplication and round-to-int does
     * not lose audible bits before the 24-bit clip. If you change
     * the gain shape (e.g. to a curve with very small values), revisit
     * this — for example a 0.01x gain at 24-bit underflows the int
     * float multiply by less than 1 LSB and would be safer in double.
     */
    private fun applyGainPcm24(src: ByteBuffer, gain: Float): ByteBuffer {
        val srcPos = src.position()
        val srcLimit = src.limit()
        val totalBytes = srcLimit - srcPos
        val scratch = ensureGainScratch(totalBytes)
        val numSamples = totalBytes / 3
        // Maxima for 24-bit signed: ±(2^23 - 1) and ±(2^23) respectively.
        val maxPos = 0x7FFFFF
        val minNeg = -0x800000
        for (i in 0 until numSamples) {
            val off = srcPos + i * 3
            val b0 = src.get(off).toInt() and 0xFF
            val b1 = src.get(off + 1).toInt() and 0xFF
            val b2 = src.get(off + 2).toInt()  // signed for sign-extend
            // Sign-extend the top byte by leaving b2 as a signed
            // int and shifting it left by 16. Java/Kotlin integer
            // promotion preserves the sign bit through the shift,
            // so a negative b2 produces the correct negative sample.
            val s = (b2 shl 16) or (b1 shl 8) or b0
            val scaled = (s * gain).toInt().coerceIn(minNeg, maxPos)
            // Write back in the same packed layout.
            val dstOff = i * 3
            scratch.put(dstOff, (scaled and 0xFF).toByte())
            scratch.put(dstOff + 1, ((scaled ushr 8) and 0xFF).toByte())
            scratch.put(dstOff + 2, ((scaled ushr 16) and 0xFF).toByte())
        }
        scratch.position(0)
        scratch.limit(numSamples * 3)
        return scratch
    }

    /**
     * Phase B: attenuate 32-bit signed PCM (little-endian) in place
     * to a fresh scratch buffer.
     *
     * ExoPlayer's `C.ENCODING_PCM_32BIT` is the standard 4-byte signed
     * integer, native-order ByteBuffer reads via getInt return the
     * sample directly. We promote to double for the multiplication
     * because float's 24-bit mantissa is less than the 32-bit dynamic
     * range; an attenuation done in float on a near-maximum sample
     * would round the bottom 8 bits to a multiple of 256, audible as
     * coarse quantization at very low signal levels. Double has 53
     * bits of mantissa, comfortably more than 32, so the round-to-int
     * is bit-exact within the 32-bit range.
     */
    private fun applyGainPcm32(src: ByteBuffer, gain: Float): ByteBuffer {
        val srcPos = src.position()
        val srcLimit = src.limit()
        val totalBytes = srcLimit - srcPos
        val scratch = ensureGainScratch(totalBytes)
        val numSamples = totalBytes / 4
        val gainD = gain.toDouble()
        // Use Long for the clip endpoints to avoid Int overflow at
        // the boundaries; Int.MIN_VALUE.coerceIn(Int.MIN_VALUE..Int.MAX_VALUE)
        // is correct but the explicit Long range is harder to misread.
        val maxPos: Long = Int.MAX_VALUE.toLong()
        val minNeg: Long = Int.MIN_VALUE.toLong()
        for (i in 0 until numSamples) {
            val s = src.getInt(srcPos + i * 4)
            val scaledD = s.toDouble() * gainD
            val scaledL = scaledD.toLong().coerceIn(minNeg, maxPos)
            scratch.putInt(i * 4, scaledL.toInt())
        }
        scratch.position(0)
        scratch.limit(numSamples * 4)
        return scratch
    }

    private fun ensureGainScratch(needBytes: Int): ByteBuffer {
        if (gainScratch.capacity() < needBytes) {
            gainScratch = ByteBuffer.allocateDirect(needBytes)
                .order(ByteOrder.nativeOrder())
        } else {
            gainScratch.clear()
        }
        return gainScratch
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
        // Phase-A renderer state-trace tag. Separate from TAG so a
        // logcat capture can isolate just the trace line stream
        // with `adb logcat -s LibusbSinkTrace:V` while leaving the
        // existing INFO/WARN logging on its original tag.
        private const val TRACE_TAG = "LibusbSinkTrace"
        // Give the iso pump this long to start dispatching frames
        // before we even consider it stuck. The first ~150 ms after
        // configure can legitimately produce zero playedFrames while
        // the URBs prime, the kernel schedules iso slots, and the
        // ring fills up to the silence head-start. 400 ms is well
        // above the longest legit warmup we've measured but well
        // under the ~3.5 s of back-pressure the wedged-pump bug
        // produces in field logs, so we still recover fast enough
        // that the user gets fallback audio before they notice the
        // dropout.
        private const val kIsoWarmupNs: Long = 400_000_000L
        // After warmup, this much time without playedFrames advancing
        // means the pump isn't draining and won't recover.
        private const val kIsoStallNs: Long = 400_000_000L
    }
}
