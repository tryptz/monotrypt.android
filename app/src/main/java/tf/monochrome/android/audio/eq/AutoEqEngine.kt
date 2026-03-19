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
 *
 * Ported from SeapEngine's TypeScript AutoEqEngine.ts
 *
 * Algorithm:
 * 1. Takes a headphone measurement and a target curve
 * 2. Calculates the difference between measured and target
 * 3. Iteratively finds the biggest deviation and adds an EQ band to correct it
 * 4. Uses biquad filter math to simulate the effect of each band
 * 5. Returns a list of parametric EQ bands that flatten the response toward the target
 */
object AutoEqEngine {

    // Constants for algorithm tuning
    private const val MAX_BOOST = 12.0f
    private const val MAX_CUT = 12.0f
    private const val MIN_Q = 0.6f
    private const val DEFAULT_SAMPLE_RATE = 48000f

    /**
     * Calculate biquad filter response at a given frequency.
     * This simulates how a parametric EQ band affects the audio spectrum.
     */
    fun calculateBiquadResponse(
        freqHz: Float,
        band: EqBand,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): Float {
        if (!band.enabled) return 0f

        val q = maxOf(0.1f, band.q)
        val w = 2 * PI * band.freq / sampleRate
        val p = 2 * PI * freqHz / sampleRate
        val s = sin(w.toDouble()) / (2 * q)
        val A = 10.0.pow(band.gain / 40.0)
        val c = cos(w.toDouble())

        val coeffs = when (band.type) {
            FilterType.PEAKING -> {
                val b0 = (1 + s * A).toFloat()
                val b1 = (-2 * c).toFloat()
                val b2 = (1 - s * A).toFloat()
                val a0 = (1 + s / A).toFloat()
                val a1 = (-2 * c).toFloat()
                val a2 = (1 - s / A).toFloat()
                listOf(b0, b1, b2, a0, a1, a2)
            }
            FilterType.LOWSHELF -> {
                val sq = (2 * sqrt(A) * s).toFloat()
                val b0 = (A * ((A + 1) - (A - 1) * c + sq)).toFloat()
                val b1 = (2 * A * ((A - 1) - (A + 1) * c)).toFloat()
                val b2 = (A * ((A + 1) - (A - 1) * c - sq)).toFloat()
                val a0 = ((A + 1) + (A - 1) * c + sq).toFloat()
                val a1 = (-2 * ((A - 1) + (A + 1) * c)).toFloat()
                val a2 = ((A + 1) + (A - 1) * c - sq).toFloat()
                listOf(b0, b1, b2, a0, a1, a2)
            }
            FilterType.HIGHSHELF -> {
                val sq = (2 * sqrt(A) * s).toFloat()
                val b0 = (A * ((A + 1) + (A - 1) * c + sq)).toFloat()
                val b1 = (-2 * A * ((A - 1) + (A + 1) * c)).toFloat()
                val b2 = (A * ((A + 1) + (A - 1) * c - sq)).toFloat()
                val a0 = ((A + 1) - (A - 1) * c + sq).toFloat()
                val a1 = (2 * ((A - 1) - (A + 1) * c)).toFloat()
                val a2 = ((A + 1) - (A - 1) * c - sq).toFloat()
                listOf(b0, b1, b2, a0, a1, a2)
            }
        }

        val b0 = coeffs[0]
        val b1 = coeffs[1]
        val b2 = coeffs[2]
        val a0 = coeffs[3]
        val a1 = coeffs[4]
        val a2 = coeffs[5]
        
        // Prevent divide by zero if a0 is somehow 0
        if (abs(a0) < 1e-10f) return 0f

        val a0inv = 1f / a0
        val b0n = b0 * a0inv
        val b1n = b1 * a0inv
        val b2n = b2 * a0inv
        val a1n = a1 * a0inv
        val a2n = a2 * a0inv

        val cp = cos(p.toDouble()).toFloat()
        val c2p = cos((2 * p).toDouble()).toFloat()

        val n = b0n * b0n + b1n * b1n + b2n * b2n +
                2 * (b0n * b1n + b1n * b2n) * cp +
                2 * b0n * b2n * c2p
        val d = 1 + a1n * a1n + a2n * a2n +
                2 * (a1n + a1n * a2n) * cp +
                2 * a2n * c2p
        
        val magnitudeSq = maxOf(1e-10, (n / d).toDouble())
        return (10 * log10(magnitudeSq)).toFloat()
    }

    /**
     * Linear interpolation to find gain at arbitrary frequency
     */
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

    /**
     * Get normalization offset (average gain around 1kHz region)
     */
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
     * Run the AutoEQ algorithm
     *
     * @param measurement Measured frequency response of the headphone
     * @param target Target frequency response (Harman, Diffuse Field, etc.)
     * @param bandCount Number of EQ bands to generate (typically 5-10)
     * @param maxFrequency Maximum frequency to consider (usually 16000 Hz)
     * @param minFrequency Minimum frequency to consider (usually 20 Hz)
     * @param maxQ Maximum Q factor for any band (limits width)
     * @param sampleRate Audio sample rate (typically 48000 Hz)
     * @return List of optimal EQ bands
     */
    fun runAutoEqAlgorithm(
        measurement: List<FrequencyPoint>,
        target: List<FrequencyPoint>,
        bandCount: Int,
        maxFrequency: Float = 16000f,
        minFrequency: Float = 20f,
        maxQ: Float = 5.0f,
        sampleRate: Float = DEFAULT_SAMPLE_RATE
    ): List<EqBand> {
        // Calculate normalization offset to match target baseline
        val offset = getNormalizationOffset(target) - getNormalizationOffset(measurement)

        // Initialize error as the difference between measurement and target
        var error = measurement.map { p ->
            FrequencyPoint(
                freq = p.freq,
                gain = (p.gain + offset) - interpolate(p.freq, target)
            )
        }

        val bands = mutableListOf<EqBand>()

        val logMin = log10(minFrequency)
        val logMax = log10(maxFrequency)
        val step = (logMax - logMin) / bandCount

        // Iteratively find biggest deviation within log-spaced frequency sectors
        for (bandIdx in 0 until bandCount) {
            val sectorMinLog = logMin + bandIdx * step
            val sectorMaxLog = logMin + (bandIdx + 1) * step
            val sectorMinFreq = 10f.pow(sectorMinLog).coerceAtLeast(minFrequency)
            val sectorMaxFreq = 10f.pow(sectorMaxLog).coerceAtMost(maxFrequency)

            var maxDeviation = 0f
            var maxWeightedDeviation = 0f
            var peakFreq = sectorMinFreq * 10f.pow(step / 2f) // Center of sector as fallback
            var peakIdx = -1

            // Scan measurement points in this sector to find biggest error
            for (j in error.indices) {
                val point = error[j]
                if (point.freq < sectorMinFreq || point.freq > sectorMaxFreq) continue

                // Apply 3-point smoothing to reduce noise
                var smoothedGain = point.gain
                if (j > 0 && j < error.size - 1) {
                    smoothedGain = (error[j - 1].gain + point.gain + error[j + 1].gain) / 3f
                }

                // Apply frequency-dependent weighting (more important in midrange)
                val weight = when {
                    point.freq < 300 -> 1.5f
                    point.freq < 4000 -> 1.0f
                    point.freq < 8000 -> 0.5f
                    else -> 0.25f
                }

                val weightedDeviation = abs(smoothedGain * weight)
                if (weightedDeviation > abs(maxWeightedDeviation)) {
                    maxWeightedDeviation = smoothedGain * weight
                    maxDeviation = smoothedGain
                    peakFreq = point.freq
                    peakIdx = j
                }
            }

            if (peakIdx == -1) continue // No points found in sector

            // Stop if remaining error is small, but create a flat band if we need to
            var gain = -maxDeviation
            val maxBoostForFreq = when {
                peakFreq > 3000 -> 6.0f
                peakFreq > 6000 -> 3.0f
                else -> MAX_BOOST
            }
            gain = gain.coerceIn(-MAX_CUT, maxBoostForFreq)
            if (abs(gain) < 0.2f) gain = 0f

            // Calculate Q (bandwidth) based on width of deviation
            var upperFreq: Float = peakFreq
            var lowerFreq: Float = peakFreq
            val targetError = maxDeviation / 2f

            // Find lower frequency where error crosses target
            for (k in peakIdx downTo 0) {
                if (abs(error[k].gain) < abs(targetError)) {
                    lowerFreq = error[k].freq
                    break
                }
            }

            // Find upper frequency where error crosses target
            for (k in peakIdx until error.size) {
                if (abs(error[k].gain) < abs(targetError)) {
                    upperFreq = error[k].freq
                    break
                }
            }

            val bandwidth = log2((upperFreq / max(1f, lowerFreq)).toDouble()).toFloat()
            var q: Float = (sqrt(2.0.pow(bandwidth.toDouble())) / (2.0.pow(bandwidth.toDouble()) - 1)).toFloat()
            if (q.isNaN()) q = 1.0f
            q = q.coerceIn(MIN_Q, maxQ)

            // Further restrict Q for high frequencies and boosts
            if (peakFreq > 5000 && q > 3.0f) q = 3.0f
            if (gain > 0 && q > 2.0f) q = 2.0f

            val newBand = EqBand(
                id = bandIdx,
                type = FilterType.PEAKING,
                freq = peakFreq,
                gain = gain,
                q = q,
                enabled = true
            )
            bands.add(newBand)

            // Update error by applying this band
            error = error.map { p ->
                val response = calculateBiquadResponse(p.freq, newBand, sampleRate)
                FrequencyPoint(
                    freq = p.freq,
                    gain = p.gain + response
                )
            }
        }

        // Sort by frequency and reassign IDs
        return bands
            .sortedBy { it.freq }
            .mapIndexed { idx, band -> band.copy(id = idx) }
    }
}
