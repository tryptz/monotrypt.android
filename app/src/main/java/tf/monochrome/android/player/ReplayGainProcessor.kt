package tf.monochrome.android.player

import kotlinx.coroutines.flow.first
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.ReplayGainMode
import tf.monochrome.android.domain.model.ReplayGainValues
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ReplayGainProcessor @Inject constructor(
    private val preferences: PreferencesManager
) {
    suspend fun calculateVolume(
        userVolume: Float,
        replayGain: ReplayGainValues?
    ): Float {
        val mode = preferences.replayGainMode.first()
        if (mode == ReplayGainMode.OFF || replayGain == null) {
            return userVolume
        }

        val preamp = preferences.replayGainPreamp.first()

        val (gainDb, peak) = when (mode) {
            ReplayGainMode.ALBUM -> Pair(
                replayGain.albumReplayGain ?: replayGain.trackReplayGain ?: 0.0,
                replayGain.albumPeakAmplitude ?: replayGain.trackPeakAmplitude ?: 1.0
            )
            ReplayGainMode.TRACK -> Pair(
                replayGain.trackReplayGain ?: 0.0,
                replayGain.trackPeakAmplitude ?: 1.0
            )
            else -> return userVolume
        }

        val adjustedGainDb = gainDb + preamp

        // Convert dB to linear scale: 10^(dB/20)
        var scale = 10.0.pow(adjustedGainDb / 20.0).toFloat()

        // Peak protection: prevent clipping
        if (scale * peak > 1.0) {
            scale = (1.0 / peak).toFloat()
        }

        return (userVolume * scale).coerceIn(0f, 1f)
    }
}
