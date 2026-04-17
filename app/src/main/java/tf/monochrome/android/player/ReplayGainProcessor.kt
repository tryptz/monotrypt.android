package tf.monochrome.android.player

import kotlinx.coroutines.flow.first
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.ReplayGainMode
import tf.monochrome.android.domain.model.ReplayGainValues
import tf.monochrome.android.domain.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ReplayGainProcessor @Inject constructor(
    private val preferences: PreferencesManager
) {
    /**
     * Calculate volume for legacy API tracks with ReplayGainValues.
     */
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

    /**
     * Calculate volume for UnifiedTrack - handles replay gain from any source:
     * - Local files: RG tags (REPLAYGAIN_TRACK_GAIN) or R128 tags
     * - Collections: replayGain field (single float, dB)
     * - API: ReplayGainValues from stream response
     */
    suspend fun calculateVolumeUnified(
        userVolume: Float,
        track: UnifiedTrack?,
        apiReplayGain: ReplayGainValues? = null
    ): Float {
        if (track == null) return userVolume

        val mode = preferences.replayGainMode.first()
        if (mode == ReplayGainMode.OFF) return userVolume

        val preamp = preferences.replayGainPreamp.first()

        val gainDb: Float = when (mode) {
            ReplayGainMode.TRACK -> {
                track.replayGainTrack
                    ?: track.r128TrackGain?.let { it / 256f }
                    ?: apiReplayGain?.trackReplayGain?.toFloat()
                    ?: 0f
            }
            ReplayGainMode.ALBUM -> {
                track.replayGainAlbum
                    ?: track.r128AlbumGain?.let { it / 256f }
                    ?: apiReplayGain?.albumReplayGain?.toFloat()
                    ?: track.replayGainTrack
                    ?: track.r128TrackGain?.let { it / 256f }
                    ?: apiReplayGain?.trackReplayGain?.toFloat()
                    ?: 0f
            }
            else -> 0f
        }

        val adjustedGainDb = gainDb + preamp.toFloat()
        var scale = 10f.pow(adjustedGainDb / 20f)

        // Peak protection: prevent clipping (use API peak data when available)
        val peak: Double? = when (mode) {
            ReplayGainMode.ALBUM -> apiReplayGain?.albumPeakAmplitude
                ?: apiReplayGain?.trackPeakAmplitude
            ReplayGainMode.TRACK -> apiReplayGain?.trackPeakAmplitude
            else -> null
        }
        if (peak != null && peak > 0.0 && scale * peak > 1.0) {
            scale = (1.0 / peak).toFloat()
        }

        return (userVolume * scale).coerceIn(0f, 1f)
    }
}
