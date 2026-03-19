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
import kotlin.math.min

/**
 * AutoEqEngine - Calculates optimal parametric EQ bands to match a target frequency response.
 */
object AutoEqEngine {

    private const val MAX_BOOST = 12.0f
    private const val MAX_CUT = 12.0f
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

        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var a0 = 0.0; var a1 = 0.0; var a2 = 0.0

        when (band.type) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / A
            }
            FilterType.LOWSHELF -> {
                val sqrtA = sqrt(A)
                b0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
                b2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
                a2 = (A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.HIGHSHELF -> {
                val sqrtA = sqrt(A)
                b0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
                b2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (A + 1.0) - (A - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
                a2 = (A + 1.0) - (A - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
        }

        if (abs(a0) < 1.0E-15) return 0f

        // Direct complex response calculation for stability
        val phi = 2.0 * PI * freqHz.toDouble() / fs
        val cos1 = cos(phi); val sin1 = sin(phi)
        val cos2 = cos(2.0 * phi); val sin2 = sin(2.0 * phi)

        val numReal = b0 + b1 * cos1 + b2 * cos2
        val numImag = -(b1 * sin1 + b2 * sin2)
        val denReal = a0 + a1 * cos1 + a2 * cos2
        val denImag = -(a1 * sin1 + a2 * sin2)

        val denMagSq = denReal * denReal + denImag * denImag
        if (denMagSq < 1.0E-15) return 0f
        
        val magSq = (numReal * numReal + numImag * numImag) / denMagSq
        val result = (10.0 * log10(maxOf(1.0E-15, magSq))).toFloat()
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
     * Run the AutoEQ algorithm using Global Greedy peak-finding.
     */
    fun runAutoEqAlgorithm(
        measurement: List<FrequencyPoint>,
        target: List<FrequencyPoint>,
        bandCount: Int,
        maxFrequency: Float = 16000f,
        minFrequency: Float = 20f,
        maxQ: Float = 6.0f,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): List<EqBand> {
        val offset = getNormalizationOffset(target) - getNormalizationOffset(measurement)

        var error = measurement.map { p ->
            FrequencyPoint(p.freq, (p.gain + offset) - interpolate(p.freq, target))
        }

        val bands = mutableListOf<EqBand>()

        // Global Greedy Search
        for (bandIdx in 0 until bandCount) {
            var maxDeviation = 0f
            var peakFreq = 1000f
            var peakIdx = -1

            for (j in error.indices) {
                val point = error[j]
                if (point.freq < minFrequency || point.freq > maxFrequency) continue

                // 3-point smoothing for stability
                val smoothedGain = if (j >= 1 && j < error.size - 1) {
                    (error[j - 1].gain + point.gain + error[j + 1].gain) / 3.0f
                } else point.gain

                // High-precision weighting
                val weight = if (point.freq < 50) 1.2f else 1.0f

                if (abs(smoothedGain * weight) > abs(maxDeviation)) {
                    maxDeviation = smoothedGain
                    peakFreq = point.freq
                    peakIdx = j
                }
            }

            if (peakIdx == -1 || abs(maxDeviation) < 0.05f) break 

            var gain = -maxDeviation
            val boostCap = if (peakFreq > 8000) 8.0f else MAX_BOOST
            gain = gain.coerceIn(-MAX_CUT, boostCap)

            var upperFreq: Float = peakFreq
            var lowerFreq: Float = peakFreq
            val targetError = maxDeviation / 2.0f

            for (k in peakIdx downTo 0) {
                if (abs(error[k].gain) < abs(targetError)) {
                    lowerFreq = error[k].freq
                    break
                }
            }
            for (k in peakIdx until error.size) {
                if (abs(error[k].gain) < abs(targetError)) {
                    upperFreq = error[k].freq
                    break
                }
            }

            val bandwidth = log2((upperFreq / max(1.0f, lowerFreq)).toDouble()).toFloat()
            var q: Float = (sqrt(2.0.pow(bandwidth.toDouble())) / (2.0.pow(bandwidth.toDouble()) - 1.0)).toFloat()
            if (q.isNaN() || q.isInfinite()) q = 1.0f
            q = q.coerceIn(MIN_Q, maxQ)

            val newBand = EqBand(
                id = bandIdx,
                type = FilterType.PEAKING,
                freq = peakFreq,
                gain = gain,
                q = q,
                enabled = true
            )
            bands.add(newBand)

            error = error.map { p ->
                val response = calculateBiquadResponse(p.freq, newBand, sampleRate)
                FrequencyPoint(p.freq, p.gain + response)
            }
        }

        return bands
            .sortedBy { it.freq }
            .mapIndexed { idx, band -> band.copy(id = idx) }
    }
}
