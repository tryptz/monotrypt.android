package tf.monochrome.android.audio.eq

import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.FilterType
import tf.monochrome.android.domain.model.FrequencyPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max

/**
 * AutoEqEngine - Calculates optimal peaking EQ bands to match a target frequency response.
 * Peaks-only mode: only boosts dips where measurement falls below target.
 */
object AutoEqEngine {

    private const val MAX_BOOST = 12.0f
    private const val MIN_Q = 0.6f
    private const val DEFAULT_SAMPLE_RATE = 48000f

    /**
     * Calculate biquad filter response at a given frequency.
     * Uses high-precision Double math and direct complex form for stability.
     */
    fun calculateBiquadResponse(
        freqHz: Float,
        band: EqBand,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): Float {
        if (!band.enabled || abs(band.gain) < 0.01f) return 0f

        val q = maxOf(0.1, band.q.toDouble())
        val f0 = band.freq.toDouble()
        val fs = sampleRate.toDouble()
        val gainDb = band.gain.toDouble()

        val w0 = 2.0 * PI * f0 / fs
        val alpha = sin(w0) / (2.0 * q)
        val A = 10.0.pow(gainDb / 40.0)
        val cosW0 = cos(w0)

        // Peaking filter only
        val b0 = 1.0 + alpha * A
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * A
        val a0 = 1.0 + alpha / A
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / A

        if (abs(a0) < 1.0E-15) return 0f

        // Normalize coefficients and calculate response
        val a0inv = 1.0 / a0
        val b0n = b0 * a0inv
        val b1n = b1 * a0inv
        val b2n = b2 * a0inv
        val a1n = a1 * a0inv
        val a2n = a2 * a0inv

        // Calculate frequency response
        val phi = 2.0 * PI * freqHz.toDouble() / fs
        val cosPhi = cos(phi)
        val cos2Phi = cos(2.0 * phi)

        val num = b0n*b0n + b1n*b1n + b2n*b2n + 2.0*(b0n*b1n + b1n*b2n)*cosPhi + 2.0*b0n*b2n*cos2Phi
        val den = 1.0 + a1n*a1n + a2n*a2n + 2.0*(a1n + a1n*a2n)*cosPhi + 2.0*a2n*cos2Phi

        if (den < 1.0E-15) return 0f

        val result = (10.0 * log10(maxOf(1.0E-15, num / den))).toFloat()
        return if (result.isNaN() || result.isInfinite()) 0f else result
    }

    private fun interpolate(freqHz: Float, data: List<FrequencyPoint>): Float {
        if (data.isEmpty()) return 0f
        if (freqHz <= data[0].freq) return data[0].gain
        if (freqHz >= data[data.size - 1].freq) return data[data.size - 1].gain

        for (i in 0 until data.size - 1) {
            if (freqHz >= data[i].freq && freqHz <= data[i + 1].freq) {
                val ratio = (freqHz - data[i].freq) / (data[i + 1].freq - data[i].freq)
                return data[i].gain + ratio * (data[i + 1].gain - data[i].gain)
            }
        }
        return 0f
    }

    private fun getNormalizationOffset(data: List<FrequencyPoint>): Float {
        var sum = 0f
        var count = 0
        for (point in data) {
            if (point.freq >= 250 && point.freq <= 2500) {
                sum += point.gain
                count++
            }
        }
        return if (count > 0) sum / count else interpolate(1000f, data)
    }

    /**
     * Run the AutoEQ algorithm — peaks only (boost-only peaking filters).
     * Only corrects dips where measurement falls below target.
     */
    fun runAutoEqAlgorithm(
        measurement: List<FrequencyPoint>,
        target: List<FrequencyPoint>,
        bandCount: Int,
        maxFrequency: Float = 16000f,
        minFrequency: Float = 20f,
        maxQ: Float = 4.0f,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): List<EqBand> {
        val offset = getNormalizationOffset(target) - getNormalizationOffset(measurement)

        val error = measurement.map { p ->
            FrequencyPoint(p.freq, (p.gain + offset) - interpolate(p.freq, target))
        }.toMutableList()

        val bands = mutableListOf<EqBand>()

        for (bandIdx in 0 until bandCount) {
            var maxDev = 0.0
            var maxWeightedDev = 0.0
            var peakFreq = 1000.0
            var peakIdx = 0

            for (j in error.indices) {
                val freq = error[j].freq.toDouble()
                if (freq < minFrequency || freq > maxFrequency) continue

                // 3-point smooth
                var v = error[j].gain.toDouble()
                if (j > 0 && j < error.size - 1) {
                    v = (error[j - 1].gain + v + error[j + 1].gain) / 3.0
                }

                // Peaks only: only look for dips (negative error = measurement below target)
                if (v >= 0.0) continue

                // Priority weighting
                val priority = when {
                    freq < 300.0 -> 1.5
                    freq < 4000.0 -> 1.0
                    freq < 8000.0 -> 0.5
                    else -> 0.25
                }

                val weightedAbs = abs(v * priority)
                if (weightedAbs > abs(maxWeightedDev)) {
                    maxWeightedDev = weightedAbs
                    maxDev = v
                    peakFreq = freq
                    peakIdx = j
                }
            }

            // No dips found, we're done
            if (maxWeightedDev == 0.0) break

            // Invert for correction (always positive = boost)
            var gain = -maxDev

            // Treble safety: taper max boost in highs
            var safeBoost = MAX_BOOST.toDouble()
            if (peakFreq > 3000.0) safeBoost = 6.0
            if (peakFreq > 6000.0) safeBoost = 3.0

            if (gain > safeBoost) gain = safeBoost

            if (gain < 0.2) break

            // Smart Q calculation (half-energy points)
            val targetEnergy = maxDev / 2.0
            var lowerFreq = peakFreq
            var upperFreq = peakFreq

            for (k in peakIdx downTo 0) {
                if (abs(error[k].gain) < abs(targetEnergy)) {
                    lowerFreq = error[k].freq.toDouble()
                    break
                }
            }
            for (k in peakIdx until error.size) {
                if (abs(error[k].gain) < abs(targetEnergy)) {
                    upperFreq = error[k].freq.toDouble()
                    break
                }
            }

            var bandwidth = log2(upperFreq / max(1.0, lowerFreq))
            if (bandwidth < 0.1) bandwidth = 0.1

            var q = sqrt(2.0.pow(bandwidth)) / (2.0.pow(bandwidth) - 1.0)
            if (q < MIN_Q) q = MIN_Q.toDouble()
            if (q > maxQ) q = maxQ.toDouble()
            if (peakFreq > 5000.0 && q > 3.0) q = 3.0
            if (q > 2.0) q = 2.0

            val newBand = EqBand(
                id = bandIdx,
                type = FilterType.PEAKING,
                freq = peakFreq.toFloat(),
                gain = gain.toFloat(),
                q = q.toFloat(),
                enabled = true
            )
            bands.add(newBand)

            // Update error curve
            for (j in error.indices) {
                val response = calculateBiquadResponse(error[j].freq, newBand, sampleRate)
                error[j] = FrequencyPoint(error[j].freq, error[j].gain + response)
            }
        }

        // Sort by frequency
        val sortedBands = bands.sortedBy { it.freq }
        return sortedBands.mapIndexed { idx, band ->
            band.copy(id = idx)
        }
    }
}
