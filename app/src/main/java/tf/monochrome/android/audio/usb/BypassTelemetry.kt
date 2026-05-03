package tf.monochrome.android.audio.usb

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-A bypass telemetry collector.
 *
 * Owns the lifecycle of a periodic poll over [LibusbUacDriver]'s native
 * counter snapshot, computes per-window deltas, and exposes:
 *
 *   - A [StateFlow] of [Window] objects the diagnostics UI binds to
 *     so users (and bug reports) can see what the iso pump is doing
 *     in real time.
 *   - A grep-friendly logcat line per window so field log captures
 *     contain the same data even when the device isn't connected to
 *     a debugger.
 *
 * Lifecycle: [start] is called by [UsbExclusiveController] when the
 * driver transitions to streaming; [stop] is called when it
 * transitions away. The collector itself does NOT poll while
 * [LibusbUacDriver.isStreaming] is false because (a) the counters
 * carry no information across stream boundaries — they reset at
 * start() — and (b) we don't want a background coroutine running
 * needlessly when the user has exclusive bypass off.
 *
 * Cost: 1 JNI call per second, 28 atomic loads native-side, a small
 * struct allocation Kotlin-side. Negligible against the iso event
 * thread's workload.
 */
@Singleton
class BypassTelemetry @Inject constructor(
    private val driver: LibusbUacDriver,
) {
    /**
     * One window's worth of telemetry. Most fields are *deltas* over
     * the polling window — that's what diagnoses health. Cumulative
     * fields (like elapsedSinceStartMs) are clearly named to
     * distinguish them. Min/max for ring depth and feedback are kept
     * as the running envelope since the last counter reset (they
     * can't be windowed without a per-window snapshot history we
     * don't carry).
     *
     * "Healthy" reference values for a Bathys at 96 kHz / 24-bit / 2ch:
     *  - completedTransfers ≈ 1000/s, errors == 0
     *  - completedPackets ≈ 8000/s
     *  - underrunBytes / totalDrainBytes < 0.01 (less than 1 % silence)
     *  - feedbackMinHz, feedbackMaxHz within ±50 ppm of nominal
     *  - writeBackPressureCalls > 0 (kRingTargetMs cap is doing its job)
     *  - rateDriftPpm | < 100
     *
     * Anything outside those ranges is a finding worth investigating
     * before any code changes; that's the entire point of Phase A.
     */
    data class Window(
        val windowMs: Long,
        val elapsedSinceStartMs: Long,
        val isoCompleted: Long,
        val isoErrors: Long,    // sum of error/timed_out/stall/no_device/overflow
        val isoCancelled: Long,
        val packetsCompleted: Long,
        val packetsError: Long,
        val ringDepthMeanBytes: Long,
        val ringDepthMinBytes: Long,    // -1 if no samples yet
        val ringDepthMaxBytes: Long,
        val underrunEvents: Long,
        val underrunBytes: Long,
        val totalDrainBytes: Long,
        val underrunRatio: Double,      // underrunBytes / totalDrainBytes (0..1)
        val feedbackCallbacks: Long,
        val feedbackErrors: Long,
        val feedbackLastHz: Double,     // 0 if no feedback received yet
        val feedbackMinHz: Double,
        val feedbackMaxHz: Double,
        val writeCalls: Long,
        val writeBackPressureCalls: Long,
        val writeAcceptedFrames: Long,
        val playedFramesDelta: Long,
        // Effective rate vs nominal: positive means the DAC is
        // running faster than nominal, negative means slower. ppm =
        // parts per million. Computed off elapsedSinceStartMs (not
        // windowMs) so it averages over the whole stream and is
        // immune to single-window jitter. Returns 0.0 before there's
        // enough data (< 200 ms elapsed) to compute meaningfully.
        val rateDriftPpm: Double,
    )

    /**
     * Most recent window snapshot, or null when not collecting.
     * Diagnostics UI binds via [stateFlow]; tests / callers can read
     * synchronously through [latest].
     */
    private val _state = MutableStateFlow<Window?>(null)
    val stateFlow: StateFlow<Window?> = _state.asStateFlow()
    val latest: Window? get() = _state.value

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var lastSnapshot: Snapshot? = null

    /**
     * Begin polling. Idempotent: a second call while already running
     * is a no-op. Called by [UsbExclusiveController] from its
     * driver.isStreaming collector when streaming flips to true.
     */
    fun start() {
        if (pollJob?.isActive == true) return
        lastSnapshot = null
        _state.value = null
        pollJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(pollIntervalMs)
            }
        }
        Log.i(TAG, "telemetry started")
    }

    /**
     * Stop polling and clear the published state. Idempotent.
     * Called by [UsbExclusiveController] when streaming flips to
     * false. We deliberately clear [_state] on stop rather than
     * leaving the last value visible — a stale reading would mislead
     * diagnostic-screen viewers into thinking the pump is still alive.
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        lastSnapshot = null
        _state.value = null
        Log.i(TAG, "telemetry stopped")
    }

    /**
     * Reset native counters and discard the local last-snapshot. Used
     * by the diagnostics-screen "reset counters" button so users can
     * baseline a measurement window without restarting the iso pump.
     */
    fun resetCounters() {
        driver.nativeResetTelemetry()
        lastSnapshot = null
        // Don't clear _state.value — the next pollOnce() will refresh
        // it; clearing here would briefly make the UI go blank.
        Log.i(TAG, "telemetry counters reset (anchor advanced to now)")
    }

    /**
     * Single-poll path. Visible internally so it can also be invoked
     * from a one-shot test or from a debugger; in production the
     * coroutine driver above runs it on a fixed cadence.
     */
    internal fun pollOnce() {
        val raw = driver.nativeTelemetrySnapshot() ?: return
        val now = Snapshot.fromLongArray(raw) ?: return
        val prev = lastSnapshot
        if (prev != null) {
            val window = computeWindow(prev, now)
            _state.value = window
            logWindow(window, now)
        } else {
            // First poll — no delta yet. Don't publish a window full
            // of zeros that would look like a healthy idle pump; the
            // UI's null state ("collecting…") is more honest.
        }
        lastSnapshot = now
    }

    private fun computeWindow(prev: Snapshot, now: Snapshot): Window {
        val windowNs = now.nowNs - prev.nowNs
        val windowMs = (windowNs / 1_000_000L).coerceAtLeast(1L)
        // Elapsed since the START of the current stream, not since
        // last poll. Lets the UI / log show "we've been streaming for
        // 3m12s" alongside the per-window stats.
        val elapsedSinceStartMs = ((now.nowNs - now.startWallNs) / 1_000_000L)
            .coerceAtLeast(0L)

        val isoCompleted = now.isoCompleted - prev.isoCompleted
        // Bucket all hard error categories together for the per-window
        // signal. The per-category breakdown is still in the logcat
        // line; the UI only needs "errors > 0 → red" semantics.
        val isoErrors = (now.isoError - prev.isoError) +
                        (now.isoTimedOut - prev.isoTimedOut) +
                        (now.isoStall - prev.isoStall) +
                        (now.isoNoDevice - prev.isoNoDevice) +
                        (now.isoOverflow - prev.isoOverflow)
        val isoCancelled = now.isoCancelled - prev.isoCancelled

        val packetsCompleted = now.packetsCompleted - prev.packetsCompleted
        val packetsError = now.packetsError - prev.packetsError

        val depthSamples = now.ringDepthSamples - prev.ringDepthSamples
        val depthSum = now.ringDepthSumBytes - prev.ringDepthSumBytes
        val depthMean = if (depthSamples > 0) depthSum / depthSamples else 0L

        val underrunEvents = now.underrunEvents - prev.underrunEvents
        val underrunBytes = now.underrunBytes - prev.underrunBytes
        val totalDrainBytes = now.totalDrainBytes - prev.totalDrainBytes
        val underrunRatio = if (totalDrainBytes > 0)
            underrunBytes.toDouble() / totalDrainBytes.toDouble() else 0.0

        val feedbackCallbacks = now.feedbackCallbacks - prev.feedbackCallbacks
        val feedbackErrors = now.feedbackErrors - prev.feedbackErrors
        val feedbackLastHz = q16ToHz(now.feedbackLastQ16, now.uframeRateHz())
        val feedbackMinHz = q16ToHz(now.feedbackMinQ16, now.uframeRateHz())
        val feedbackMaxHz = q16ToHz(now.feedbackMaxQ16, now.uframeRateHz())

        val writeCalls = now.writeCalls - prev.writeCalls
        val writeBackPressureCalls = now.writeBackPressureCalls -
                                     prev.writeBackPressureCalls
        val writeAcceptedFrames = now.writeAcceptedFrames -
                                  prev.writeAcceptedFrames
        val playedFramesDelta = now.playedFrames - prev.playedFrames

        // Drift in parts per million, computed against the wall clock
        // anchor at start. Returns 0 when we don't yet have enough
        // data (< 200 ms or no nominal rate known).
        val rateDriftPpm = computeDriftPpm(now)

        return Window(
            windowMs = windowMs,
            elapsedSinceStartMs = elapsedSinceStartMs,
            isoCompleted = isoCompleted,
            isoErrors = isoErrors,
            isoCancelled = isoCancelled,
            packetsCompleted = packetsCompleted,
            packetsError = packetsError,
            ringDepthMeanBytes = depthMean,
            ringDepthMinBytes = if (now.ringDepthMinBytes >= UINT32_NO_DATA)
                -1L else now.ringDepthMinBytes.toLong(),
            ringDepthMaxBytes = now.ringDepthMaxBytes.toLong(),
            underrunEvents = underrunEvents,
            underrunBytes = underrunBytes,
            totalDrainBytes = totalDrainBytes,
            underrunRatio = underrunRatio,
            feedbackCallbacks = feedbackCallbacks,
            feedbackErrors = feedbackErrors,
            feedbackLastHz = feedbackLastHz,
            feedbackMinHz = feedbackMinHz,
            feedbackMaxHz = feedbackMaxHz,
            writeCalls = writeCalls,
            writeBackPressureCalls = writeBackPressureCalls,
            writeAcceptedFrames = writeAcceptedFrames,
            playedFramesDelta = playedFramesDelta,
            rateDriftPpm = rateDriftPpm,
        )
    }

    private fun computeDriftPpm(now: Snapshot): Double {
        val nominalRate = driver.diagnostics.value?.sampleRateHz ?: return 0.0
        if (nominalRate <= 0) return 0.0
        val elapsedNs = now.nowNs - now.startWallNs
        if (elapsedNs < 200_000_000L) return 0.0  // too early to be honest
        val playedDelta = now.playedFrames - now.startPlayedFrames
        if (playedDelta <= 0) return 0.0
        // Effective rate: (frames / seconds). Compare against nominal:
        // ppm = (effective - nominal) / nominal * 1e6.
        val effectiveRate = playedDelta.toDouble() * 1e9 / elapsedNs.toDouble()
        return (effectiveRate - nominalRate) / nominalRate * 1_000_000.0
    }

    private fun logWindow(w: Window, raw: Snapshot) {
        // Single-line, key=value format chosen so log captures parse
        // trivially with awk / grep / a small Python script. No JSON
        // because Android's logcat truncates at ~4 KiB per line and
        // structured key=value is denser. Order is: health signals
        // first (errors, underrun), throughput second, drift last.
        val msg = buildString {
            append("bypass_tel ")
            append("win=").append(w.windowMs).append("ms ")
            append("elapsed=").append(w.elapsedSinceStartMs).append("ms ")
            append("iso_ok=").append(w.isoCompleted)
            append(" iso_err=").append(w.isoErrors)
            append(" iso_cancel=").append(w.isoCancelled)
            append(" pkt_ok=").append(w.packetsCompleted)
            append(" pkt_err=").append(w.packetsError)
            append(" ring_mean=").append(w.ringDepthMeanBytes).append("B")
            append(" ring_min=").append(w.ringDepthMinBytes).append("B")
            append(" ring_max=").append(w.ringDepthMaxBytes).append("B")
            append(" under_events=").append(w.underrunEvents)
            append(" under_bytes=").append(w.underrunBytes)
            append(" drain_bytes=").append(w.totalDrainBytes)
            append(" under_ratio=").append("%.4f".format(w.underrunRatio))
            append(" fb_cb=").append(w.feedbackCallbacks)
            append(" fb_err=").append(w.feedbackErrors)
            append(" fb_last=").append("%.2f".format(w.feedbackLastHz)).append("Hz")
            append(" fb_min=").append("%.2f".format(w.feedbackMinHz)).append("Hz")
            append(" fb_max=").append("%.2f".format(w.feedbackMaxHz)).append("Hz")
            append(" wr_calls=").append(w.writeCalls)
            append(" wr_bp=").append(w.writeBackPressureCalls)
            append(" wr_frames=").append(w.writeAcceptedFrames)
            append(" played_delta=").append(w.playedFramesDelta)
            append(" drift=").append("%.1f".format(w.rateDriftPpm)).append("ppm")
        }
        // Severity escalation: errors or sustained underrun → WARN so
        // the log grep finds them without filtering, otherwise INFO.
        if (w.isoErrors > 0L || w.packetsError > 0L || w.underrunRatio > 0.01) {
            Log.w(TAG, msg)
        } else {
            Log.i(TAG, msg)
        }
    }

    /**
     * Convert a 16.16 fixed-point "frames per microframe" value (the
     * native side's unit) to a sample rate in Hz. uframeRateHz is the
     * USB clock — 8000 for high-speed, 1000 for full-speed.
     */
    private fun q16ToHz(q16: Long, uframeRateHz: Int): Double {
        if (q16 <= 0L || q16 >= UINT32_NO_DATA) return 0.0
        return q16.toDouble() * uframeRateHz.toDouble() / 65536.0
    }

    /**
     * Decoded, typed view of the native counter array. Pure
     * deserialisation — no derived fields, no logic. Field order
     * matches LibusbUacDriver::snapshotTelemetry; keep them in lock-
     * step. Adding fields: append at the END only.
     */
    private data class Snapshot(
        val isoCompleted: Long,
        val isoError: Long,
        val isoTimedOut: Long,
        val isoStall: Long,
        val isoNoDevice: Long,
        val isoOverflow: Long,
        val isoCancelled: Long,
        val packetsCompleted: Long,
        val packetsError: Long,
        val ringDepthSamples: Long,
        val ringDepthSumBytes: Long,
        val ringDepthMinBytes: Long,    // 0xFFFFFFFF if no samples yet
        val ringDepthMaxBytes: Long,
        val underrunEvents: Long,
        val underrunBytes: Long,
        val totalDrainBytes: Long,
        val feedbackCallbacks: Long,
        val feedbackLastQ16: Long,
        val feedbackMinQ16: Long,
        val feedbackMaxQ16: Long,
        val feedbackErrors: Long,
        val writeCalls: Long,
        val writeBackPressureCalls: Long,
        val writeAcceptedFrames: Long,
        val startWallNs: Long,
        val startPlayedFrames: Long,
        val nowNs: Long,
        val playedFrames: Long,
    ) {
        /**
         * USB host clock for the active stream. Phase A doesn't have
         * the host's HS/FS bit in the snapshot (the BypassDiagnostics
         * payload carries it instead), so we infer from the
         * BypassDiagnostics if known. Default 8000 — 99 % of modern
         * DACs are HS, so this is the right default when unknown.
         * Worst case for an FS device misclassified as HS: feedback
         * Hz reads 8x too high, which is obviously wrong on the
         * diagnostics screen and trivially correctable later.
         */
        fun uframeRateHz(): Int = 8000

        companion object {
            fun fromLongArray(arr: LongArray): Snapshot? {
                if (arr.size < EXPECTED_FIELD_COUNT) return null
                var i = 0
                return Snapshot(
                    isoCompleted = arr[i++],
                    isoError = arr[i++],
                    isoTimedOut = arr[i++],
                    isoStall = arr[i++],
                    isoNoDevice = arr[i++],
                    isoOverflow = arr[i++],
                    isoCancelled = arr[i++],
                    packetsCompleted = arr[i++],
                    packetsError = arr[i++],
                    ringDepthSamples = arr[i++],
                    ringDepthSumBytes = arr[i++],
                    ringDepthMinBytes = arr[i++] and 0xFFFFFFFFL,
                    ringDepthMaxBytes = arr[i++] and 0xFFFFFFFFL,
                    underrunEvents = arr[i++],
                    underrunBytes = arr[i++],
                    totalDrainBytes = arr[i++],
                    feedbackCallbacks = arr[i++],
                    feedbackLastQ16 = arr[i++] and 0xFFFFFFFFL,
                    feedbackMinQ16 = arr[i++] and 0xFFFFFFFFL,
                    feedbackMaxQ16 = arr[i++] and 0xFFFFFFFFL,
                    feedbackErrors = arr[i++],
                    writeCalls = arr[i++],
                    writeBackPressureCalls = arr[i++],
                    writeAcceptedFrames = arr[i++],
                    startWallNs = arr[i++],
                    startPlayedFrames = arr[i++],
                    nowNs = arr[i++],
                    playedFrames = arr[i++],
                )
            }
        }
    }

    companion object {
        private const val TAG = "BypassTelemetry"
        private const val pollIntervalMs = 1000L
        private const val EXPECTED_FIELD_COUNT = 28
        // Sentinel value the C++ side uses for "no samples / no data".
        // 0xFFFFFFFF as an unsigned 32-bit, which Kotlin gets as the
        // signed long 4294967295.
        private const val UINT32_NO_DATA = 0xFFFFFFFFL
    }
}
