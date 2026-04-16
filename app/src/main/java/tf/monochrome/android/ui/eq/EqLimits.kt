package tf.monochrome.android.ui.eq

/**
 * Shared range constants for the two EQ surfaces.
 *
 * AutoEQ bands are capped tighter so the algorithm's own per-band safeBoost
 * (12/6/3 dB depending on frequency) is the binding constraint. The Parametric
 * EQ is a tone-shaping tool and intentionally offers more range; callers are
 * responsible for communicating headroom risk to the user.
 */
object EqLimits {
    const val AUTOEQ_MAX_BAND_DB = 12f
    const val PARAMETRIC_MAX_BAND_DB = 24f
    const val MIN_FREQ_HZ = 20f
    const val MAX_FREQ_HZ = 20000f

    // Total signal-path headroom for AutoEQ (band gain + preamp). Beyond this,
    // the biquad cascade drives the ReplayGain limiter into hard clipping at
    // the band's resonant peak. Preamp is clamped against (TOTAL - peakBand).
    const val AUTOEQ_MAX_TOTAL_DB = 24f
}
