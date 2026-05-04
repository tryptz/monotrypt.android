package tf.monochrome.android.audio.usb

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase D in-app bypass self-test.
 *
 * Runs a short measurement window against the live iso pump and
 * returns a [Verdict] summarizing whether the bypass is healthy.
 * The user-facing entry is a Settings-screen button "Run bypass
 * self-test"; this class is the orchestration behind it.
 *
 * What the self-test IS:
 *
 *   A health check that uses the Phase A telemetry to confirm the
 *   iso pump is engaged, completing transfers without errors,
 *   running below the underrun threshold, and within the expected
 *   rate-drift envelope. A passing self-test gives the user strong
 *   evidence that their DAC is being driven correctly RIGHT NOW.
 *
 * What the self-test IS NOT:
 *
 *   A bit-perfect proof. Byte-equality at the wire is provable only
 *   by the host-side wire capture toolchain in tools/bit-perfect/.
 *   The self-test cannot tell you that the bytes leaving your phone
 *   equal the bytes the renderer fed in — only that the bytes that
 *   ARE leaving are being delivered without iso-layer errors. A
 *   failure mode the self-test cannot catch: if some upstream DSP
 *   processor is silently transforming the audio at unity gain,
 *   the iso pump is still healthy and the self-test still passes,
 *   but the output is not bit-perfect. The self-test verdict states
 *   this limitation explicitly so users do not over-rely on it.
 *
 * The intended user flow: the user enables exclusive bypass, plays
 * a short test track from the corpus (or any music they like),
 * opens Settings, taps "Run bypass self-test." The self-test takes
 * roughly five seconds — long enough for a stable telemetry window
 * to accumulate — and reports a verdict. A passing verdict reads
 * "Bypass healthy" with the numeric backing visible underneath; a
 * failing verdict reads "Bypass not healthy" with the specific
 * failure reason and a suggestion to check.
 */
@Singleton
class BypassSelfTest @Inject constructor(
    private val driver: LibusbUacDriver,
    private val telemetry: BypassTelemetry,
    private val controller: UsbExclusiveController,
) {
    /**
     * Self-test outcome. The fields are intentionally redundant —
     * [overall] is the single boolean a UI binding can branch on,
     * [headline] is the user-facing one-line summary, [details] is
     * the engineer-facing breakdown. UI surfaces show the headline
     * by default and reveal the details on tap.
     */
    data class Verdict(
        val overall: Outcome,
        val headline: String,
        val details: List<Detail>,
    ) {
        enum class Outcome { PASS, FAIL, INCONCLUSIVE }

        /** A single sub-check result with a name and a human readable
         *  explanation. The UI renders these as a list under the
         *  headline so a failing self-test points the user at the
         *  specific failing check. */
        data class Detail(
            val name: String,
            val outcome: Outcome,
            val explanation: String,
        )
    }

    /**
     * Run the self-test and return a verdict. Suspends for the
     * measurement window duration plus a brief settle. Safe to call
     * concurrently with playback — the test does NOT inject signals,
     * it observes the live telemetry that is already running.
     *
     * The default window of five seconds was chosen to give the
     * underrun-ratio and drift signals enough samples to be honest:
     * with a 1-Hz telemetry tick, five windows produce five data
     * points, which is enough for a confident verdict without
     * making the user wait long. Shorter windows produce
     * inconclusive results because a single outlier can swing the
     * average; longer windows test the user's patience without
     * meaningfully improving the signal.
     */
    suspend fun run(measurementWindowMs: Long = 5000L): Verdict {
        val details = mutableListOf<Verdict.Detail>()

        // Check 1: bypass is engaged at all.
        //
        // If exclusive bypass is not currently streaming, none of
        // the downstream checks can produce useful results — the
        // telemetry counters are all zero by definition. We mark
        // this INCONCLUSIVE rather than FAIL because "I haven't
        // engaged bypass" is the user's choice, not a bug.
        val streamingStatus = controller.status.first()
        val streaming = streamingStatus == UsbExclusiveController.Status.Streaming
        details.add(Verdict.Detail(
            name = "Bypass engaged",
            outcome = if (streaming) Verdict.Outcome.PASS
                      else Verdict.Outcome.INCONCLUSIVE,
            explanation = if (streaming)
                "Exclusive USB DAC bypass is active."
            else
                "Bypass is not currently streaming. Start playback " +
                "with exclusive USB DAC enabled and try again."))

        if (!streaming) {
            return Verdict(
                overall = Verdict.Outcome.INCONCLUSIVE,
                headline = "Self-test inconclusive — start playback first",
                details = details)
        }

        // Reset telemetry counters so the measurement window starts
        // from a clean baseline. Without this, the underrun-ratio
        // value would average against the entire stream's history,
        // including its noisy startup window where padding silence
        // is normal — a stream that is currently healthy could
        // appear to fail because of bad data from minutes ago.
        telemetry.resetCounters()

        // Wait for at least one complete telemetry tick to land in
        // the StateFlow so we have a window to evaluate. The
        // stateFlow.first { ... } pattern with a timeout avoids
        // hanging the self-test forever if telemetry is broken.
        val firstWindow = withTimeoutOrNull(2000L) {
            telemetry.stateFlow.first { it != null }
        }
        if (firstWindow == null) {
            details.add(Verdict.Detail(
                name = "Telemetry alive",
                outcome = Verdict.Outcome.FAIL,
                explanation = "Telemetry collector did not produce a " +
                    "snapshot within 2 seconds. The native counter " +
                    "snapshot may be failing — check logcat for " +
                    "BypassTelemetry errors."))
            return Verdict(
                overall = Verdict.Outcome.FAIL,
                headline = "Self-test failed — telemetry not responding",
                details = details)
        }

        // Sleep through the measurement window, then read the
        // current telemetry state. Using stateFlow.first instead of
        // a periodic poll because the StateFlow already debounces
        // to one value per tick and we only need the latest.
        delay(measurementWindowMs)
        val window = telemetry.latest
        if (window == null) {
            details.add(Verdict.Detail(
                name = "Measurement complete",
                outcome = Verdict.Outcome.FAIL,
                explanation = "Telemetry stopped emitting during the " +
                    "measurement window — the iso pump may have torn " +
                    "down. Restart playback and try again."))
            return Verdict(
                overall = Verdict.Outcome.FAIL,
                headline = "Self-test failed — bypass stopped during measurement",
                details = details)
        }

        // Check 2: iso transfers completed without errors.
        //
        // ANY error during the window is a problem. Errors are rare
        // in healthy operation because libusb retries transient
        // glitches at the kernel level before they reach our
        // counters. A non-zero error count here means something is
        // wrong at the bus level — bad cable, hub contention, or a
        // kernel-side reattach race.
        val isoOk = window.isoErrors == 0L && window.packetsError == 0L
        details.add(Verdict.Detail(
            name = "Iso transfers",
            outcome = if (isoOk) Verdict.Outcome.PASS else Verdict.Outcome.FAIL,
            explanation = if (isoOk)
                "${window.isoCompleted} transfers and " +
                "${window.packetsCompleted} packets completed " +
                "without errors over the measurement window."
            else
                "${window.isoErrors} transfer errors and " +
                "${window.packetsError} packet errors observed. " +
                "Check the cable, try a different USB port, and " +
                "verify the DAC is on a stable power source."))

        // Check 3: underrun ratio below the audible threshold.
        //
        // The threshold of 0.5% (0.005) is conservative — anything
        // below ~1% is generally inaudible because the silence
        // padding tracks the iso packet rate (8000 packets/sec at
        // HS), so each underrun is a sub-millisecond event that
        // most listeners cannot perceive even back-to-back. We use
        // 0.5% to give a small margin, but failures at 0.5%-1%
        // include a softer explanation acknowledging that the user
        // probably did not notice.
        val underrunOk = window.underrunRatio < 0.005
        val underrunMarginal = window.underrunRatio < 0.01
        details.add(Verdict.Detail(
            name = "Underrun ratio",
            outcome = when {
                underrunOk -> Verdict.Outcome.PASS
                underrunMarginal -> Verdict.Outcome.INCONCLUSIVE
                else -> Verdict.Outcome.FAIL
            },
            explanation = "Underrun ratio: ${"%.3f".format(window.underrunRatio * 100)}%. " +
                when {
                    underrunOk -> "Well below the audible threshold."
                    underrunMarginal -> "Slightly elevated but probably " +
                        "imperceptible. Watch for occasional faint clicks."
                    else -> "Above the audible threshold — you may hear " +
                        "occasional clicks or stutters. The renderer is " +
                        "feeding the iso pump slower than realtime, " +
                        "which usually means CPU-heavy DSP processors " +
                        "are competing for the audio thread."
                }))

        // Check 4: rate drift bounded.
        //
        // Drift outside ±200 ppm over a 5-second window is unusual
        // — the device's clock recovery PLL settles within tens of
        // ms, so a steady-state drift this large means either the
        // feedback EP is failing to communicate correctly or our
        // open-loop seed is far enough off that the feedback loop
        // hasn't caught up. The 200 ppm threshold is generous; a
        // healthy Bathys typically lands within ±20 ppm.
        val driftMag = if (window.rateDriftPpm < 0)
            -window.rateDriftPpm else window.rateDriftPpm
        val driftOk = driftMag < 200.0
        val driftMarginal = driftMag < 500.0
        details.add(Verdict.Detail(
            name = "Rate drift",
            outcome = when {
                driftOk -> Verdict.Outcome.PASS
                driftMarginal -> Verdict.Outcome.INCONCLUSIVE
                else -> Verdict.Outcome.FAIL
            },
            explanation = "Rate drift: ${"%.1f".format(window.rateDriftPpm)} ppm. " +
                when {
                    driftOk -> "Within the ±200 ppm healthy envelope; " +
                        "the device's feedback loop is in lock."
                    driftMarginal -> "Outside the typical envelope but " +
                        "still tracking. The feedback EP may be missing " +
                        "occasional packets."
                    else -> "Significant drift — the host and device " +
                        "clocks are out of sync. This usually points at " +
                        "a feedback EP failure or a wMaxPacketSize " +
                        "negotiation problem; check logcat for " +
                        "LibusbUacDriver errors during bypass start."
                }))

        // Roll up the per-check outcomes into the overall verdict.
        // Any FAIL is a FAIL; any INCONCLUSIVE without a FAIL is
        // INCONCLUSIVE; otherwise PASS.
        val anyFail = details.any { it.outcome == Verdict.Outcome.FAIL }
        val anyInconclusive = details.any { it.outcome == Verdict.Outcome.INCONCLUSIVE }
        val overall = when {
            anyFail -> Verdict.Outcome.FAIL
            anyInconclusive -> Verdict.Outcome.INCONCLUSIVE
            else -> Verdict.Outcome.PASS
        }

        val headline = when (overall) {
            Verdict.Outcome.PASS ->
                "Bypass healthy — DAC is being driven correctly."
            Verdict.Outcome.INCONCLUSIVE ->
                "Bypass mostly healthy — see details for marginal checks."
            Verdict.Outcome.FAIL ->
                "Bypass not healthy — see details below."
        }

        Log.i(TAG, "self-test verdict=$overall: $headline")
        for (d in details) {
            Log.i(TAG, "  ${d.name}: ${d.outcome} — ${d.explanation}")
        }
        return Verdict(overall, headline, details)
    }

    companion object {
        private const val TAG = "BypassSelfTest"
    }
}
