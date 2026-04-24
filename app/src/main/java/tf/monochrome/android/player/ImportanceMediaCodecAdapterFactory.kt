package tf.monochrome.android.player

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter

/**
 * Wraps a [MediaCodecAdapter.Factory] and stamps every codec configuration
 * with the highest possible MediaCodec importance (0).
 *
 * Why: Android's `IResourceManagerService` keeps a global ordering of
 * [android.media.MediaCodec] instances and reclaims lower-priority ones when
 * the global codec budget is exhausted. ExoPlayer creates a new codec on
 * every sample-rate / format transition and tears the old one down a frame
 * later — there's a window where two codecs are alive, and on devices under
 * memory pressure the old one gets *evicted* by the resource manager before
 * ExoPlayer asynchronously releases it. Logcat surfaces this as:
 *
 *   E MediaCodec: Released by resource manager
 *
 * which is followed by audio dropouts. Setting `KEY_IMPORTANCE = 0` (the
 * highest priority value) marks our codec as the last-to-be-reclaimed.
 *
 * `KEY_IMPORTANCE` is API 35+. On older devices this is a silent no-op —
 * playback continues, just without the protection.
 */
@OptIn(UnstableApi::class)
class ImportanceMediaCodecAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        if (Build.VERSION.SDK_INT >= 35) {
            // String literal because MediaFormat.KEY_IMPORTANCE is gated to
            // SDK 35 in javadoc but the String value ("importance") is
            // accepted on the same versions; using the literal keeps the
            // class compileable against older `MediaFormat` ABIs.
            configuration.mediaFormat.setInteger("importance", 0)
        }
        return delegate.createAdapter(configuration)
    }
}
