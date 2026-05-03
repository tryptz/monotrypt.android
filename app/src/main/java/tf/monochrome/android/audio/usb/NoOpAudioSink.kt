package tf.monochrome.android.audio.usb

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * Throwaway [AudioSink] used as the delegate inside [LibusbAudioSink]
 * when the user has Exclusive USB DAC mode on. Never instantiates an
 * `AudioTrack`. Never opens a route. Never produces sound.
 *
 * Why this exists: the previous design wrapped a real [DefaultAudioSink]
 * (`androidx.media3.exoplayer.audio.DefaultAudioSink`) as the delegate
 * so we could fall back to AudioFlinger if libusb couldn't engage. But
 * `DefaultAudioSink` builds an `AudioTrack` as a side effect of
 * `configure()` regardless of whether anyone ever feeds it via
 * `handleBuffer()`. With a USB DAC plugged in, that AudioTrack defaults
 * to the USB output, tries `start()`, fails with `status=-32` because
 * libusb has the streaming interface claimed exclusively, and loops in
 * a 10-retry cascade that sprays USB control transfers across the bus
 * and contends with our iso pump's data transfers — the audible
 * "KKDDRDRRDRRDLDLLDLLD" stutter from field log
 * `monotryptdebug20260503143146`. A speaker-pin workaround in
 * `2c6fe41` didn't fully fix it because the AudioTrack's mere lifecycle
 * on this OEM is enough to disturb USB scheduling.
 *
 * The fix: when exclusive is on, swap the delegate from a real sink to
 * this NoOp. [LibusbAudioSink]'s existing bypass logic stays intact;
 * if `super.handleBuffer` falls through (when bypass can't engage for
 * whatever reason) it forwards to us and we silently accept the audio
 * — bypass-or-nothing semantics, exactly what the user signed up for
 * by toggling Exclusive on.
 *
 * Method-by-method notes inline. Most are no-op; a few are load-bearing
 * per the [AudioSink] contract:
 *   - [setListener] / [flush] / [handleDiscontinuity] together emit
 *     `Listener.onPositionDiscontinuity` so the renderer's position
 *     tracking stays correct after seeks. `DefaultAudioSink` fires this
 *     from inside its AudioTrack lifecycle; we have to do it manually.
 *   - [getFormatSupport] reports `SINK_FORMAT_HANDLED` for int PCM and
 *     `SINK_FORMAT_UNSUPPORTED` for float / non-PCM. This propagates
 *     into `MediaCodecAudioRenderer` and disables float-output
 *     negotiation for the codec, so the decoded PCM that reaches us is
 *     int — which is what `LibusbAudioSink.applyGainPcm16` expects on
 *     the bypass path.
 *   - [setPlaybackParameters] / [getPlaybackParameters] round-trip the
 *     user's last-set value (NOT `DEFAULT`) — returning `DEFAULT`
 *     unconditionally makes ExoPlayer loop trying to apply the user's
 *     intended speed.
 *
 * Implements only the abstract methods of [AudioSink]; the interface's
 * `default` methods (`setPlayerId`, `setClock`, `getAudioCapabilities`,
 * `getFormatOffloadSupport`, `setPreferredDevice`,
 * `setOutputStreamOffsetUs`, `getAudioTrackBufferSizeUs`, `release`)
 * are intentionally left to their no-op defaults.
 */
@UnstableApi
class NoOpAudioSink : AudioSink {

    private var listener: AudioSink.Listener? = null
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var skipSilenceEnabled: Boolean = false
    private var audioAttributes: AudioAttributes? = null
    private var endedAfterPlayToEnd: Boolean = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    @AudioSink.SinkFormatSupport
    override fun getFormatSupport(format: Format): Int {
        // Compressed source formats (FLAC, Opus, etc.) reach us here
        // with pcmEncoding = NO_VALUE before the decoder has reported
        // its output format. Reject — the renderer will re-query with
        // the decoded PCM format and we'll match there.
        if (format.pcmEncoding == Format.NO_VALUE) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        return when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT -> AudioSink.SINK_FORMAT_HANDLED
            // Float / packed-24-BE / 32-BE / non-PCM all unsupported —
            // bypass cannot transcode and LibusbAudioSink's gain path
            // only handles 16-bit; 24/32-bit fall through to direct
            // write, which still works as long as the renderer hands
            // us non-float PCM.
            else -> AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        AudioSink.CURRENT_POSITION_NOT_SET

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        // Successful configure means the sink is "ready". We have
        // nothing to set up; just clear the ended-flag so the next
        // playback can drain.
        endedAfterPlayToEnd = false
    }

    override fun play() { /* no-op */ }

    override fun handleDiscontinuity() {
        // Renderer signals a seek-induced discontinuity. Mirror
        // DefaultAudioSink's behavior: tell the listener the position
        // is no longer continuous so the renderer re-anchors.
        listener?.onPositionDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        // Swallow whatever LibusbAudioSink forwards us. Caller
        // semantics: returning `true` means we consumed the buffer
        // fully. Advance the position so the caller's accounting
        // matches.
        if (buffer.hasRemaining()) {
            buffer.position(buffer.limit())
        }
        return true
    }

    override fun playToEndOfStream() {
        // No queued audio to drain — instantly "ended".
        endedAfterPlayToEnd = true
    }

    override fun isEnded(): Boolean = endedAfterPlayToEnd

    override fun hasPendingData(): Boolean = false

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        // Round-trip the value back through getPlaybackParameters.
        // ExoPlayer reads it back to confirm the sink accepted it;
        // returning DEFAULT unconditionally would loop forever as the
        // player tries to apply the user's intended speed. Speed
        // change doesn't actually time-stretch audio in bypass mode
        // — we don't have SonicAudioProcessor in the chain — but
        // advertising acceptance keeps the player state machine quiet.
        this.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        this.skipSilenceEnabled = skipSilenceEnabled
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes? = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) { /* no-op */ }

    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {
        /* no-op */
    }

    override fun enableTunnelingV21() { /* no-op — never tunneled */ }

    override fun disableTunneling() { /* no-op */ }

    override fun setVolume(volume: Float) {
        // Volume is owned by BypassVolumeController; LibusbAudioSink
        // reads from there directly. Nothing to do here.
    }

    override fun pause() { /* no-op */ }

    override fun flush() {
        // Renderer flush — the listener needs to know the position
        // baseline is reset, otherwise the renderer's tracked position
        // drifts after seeks. DefaultAudioSink fires this from its
        // AudioTrack's flush path; we have to do it manually.
        endedAfterPlayToEnd = false
        listener?.onPositionDiscontinuity()
    }

    override fun reset() {
        endedAfterPlayToEnd = false
    }
}
