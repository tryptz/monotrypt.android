package tf.monochrome.android.audio.usb

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tf.monochrome.android.audio.UsbAudioRouter
import tf.monochrome.android.data.preferences.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layers Android 14+ platform bit-perfect on top of the existing
 * `usbBitPerfectEnabled` toggle. When the toggle is on, a USB DAC is
 * attached, and the AIDL Audio HAL exposes `AUDIO_OUTPUT_FLAG_BIT_PERFECT`
 * for a mixer attribute matching the current track's PCM format, we call
 * `AudioManager.setPreferredMixerAttributes(MIXER_BEHAVIOR_BIT_PERFECT)`
 * so AudioFlinger passes PCM through untouched — same end result as the
 * libusb path in [UsbExclusiveController], but native and far less
 * invasive.
 *
 * On any of: SDK < 34, no USB device, HAL doesn't expose bit-perfect for
 * this PCM format, or `setPreferredMixerAttributes` returns false / throws —
 * the controller silently leaves the existing `setPreferredAudioDevice`
 * routing in place. No regression vs the prior behavior; the user just
 * doesn't get bit-perfect on that combination.
 *
 * Concurrency mirrors [UsbExclusiveController]: a single reconcile
 * coroutine consumes a CONFLATED [Channel] of "something changed" ticks.
 * The pref collector, the device-router collector, and the format-change
 * call from [tf.monochrome.android.player.PlaybackService] all feed the
 * channel, so reconcile never overlaps with itself even though its
 * inputs fan in from multiple threads.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlatformBitPerfectController @Inject constructor(
    @ApplicationContext appContext: Context,
    private val preferences: PreferencesManager,
    private val usbAudioRouter: UsbAudioRouter,
) {
    enum class Status {
        /** Toggle off — no platform bit-perfect attempted. */
        Disabled,
        /** SDK_INT < 34 — `AudioMixerAttributes` API not available. */
        Unsupported,
        /** Toggle on but no USB Audio Class device attached. */
        NoUsbDevice,
        /** No track playing yet — waiting for first format-change event. */
        Idle,
        /** Device + format known, but the HAL didn't return any
         *  bit-perfect mixer attributes at all. The OEM hasn't wired
         *  `AUDIO_OUTPUT_FLAG_BIT_PERFECT` on this device's USB port. */
        NotSupported,
        /** HAL returned bit-perfect attrs, but none match the current
         *  track's (sample rate, channel mask, encoding). E.g. DAC says
         *  it can do 48 / 96 / 192 kHz bit-perfect but the track is
         *  44.1 kHz, or track is float and HAL is integer-only. */
        FormatUnsupported,
        /** HAL would accept setPreferredMixerAttributes for this PCM
         *  format on this DAC, but we don't actually call it because
         *  the resulting route reconfigure interacts badly with
         *  ExoPlayer's DefaultAudioSink AudioTrack lifecycle (see
         *  field log monotryptdebug20260503124933.log: AudioFlinger
         *  closes outputs faster than ExoPlayer can restoreTrack_l,
         *  -32 retry cascade exhausts, audio dies permanently). The
         *  routed-but-not-bit-perfect path via setPreferredAudioDevice
         *  remains active so audio still plays through the DAC; only
         *  bit-perfect at the HAL level isn't engaged. Use the libusb
         *  exclusive toggle for true bit-perfect on this device. */
        SuppressedForCompatibility,
        /** `setPreferredMixerAttributes` succeeded — AudioFlinger is now
         *  passing PCM through untouched to the DAC. (Currently
         *  unreachable until SuppressedForCompatibility is lifted.) */
        Active,
        /** `setPreferredMixerAttributes` returned false or threw. The
         *  existing preferred-device routing still applies, just not
         *  bit-perfect. */
        Error,
    }

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _status = MutableStateFlow(
        if (Build.VERSION.SDK_INT >= 34) Status.Disabled else Status.Unsupported
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The mixer attributes currently applied to the device, or null
     *  unless [status] is [Status.Active]. Settings UI renders the
     *  contained AudioFormat as "44.1 kHz · 16-bit · stereo". */
    private val _appliedFormat = MutableStateFlow<AudioMixerAttributes?>(null)
    val appliedFormat: StateFlow<AudioMixerAttributes?> = _appliedFormat.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tick = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var enabled = false
    @Volatile private var currentFormat: Format? = null
    // Tracks the device we last applied attributes to so we can clear
    // them on the same device when it goes away. Cleared at the same
    // time _appliedFormat is.
    @Volatile private var appliedDevice: AudioDeviceInfo? = null

    /** Called once from [tf.monochrome.android.MonochromeApp.onCreate]. */
    fun start() {
        // SDK < 34 short-circuits everything — nothing to observe and no
        // API to call. Status stays at Unsupported and reconcile never
        // runs. Belt-and-braces: every code path in here also checks the
        // SDK gate, but skipping observer setup avoids spinning idle
        // coroutines on older devices.
        if (Build.VERSION.SDK_INT < 34) return

        scope.launch {
            preferences.usbBitPerfectEnabled
                .distinctUntilChanged()
                .collect {
                    enabled = it
                    tick.trySend(Unit)
                }
        }
        scope.launch {
            // No distinctUntilChanged here — usbOutputDevice is a
            // StateFlow which already conflates duplicate emissions
            // (and as of kotlinx.coroutines 1.7 calling
            // distinctUntilChanged on a StateFlow is a hard error).
            usbAudioRouter.usbOutputDevice
                .collect { tick.trySend(Unit) }
        }
        scope.launch {
            for (ignored in tick) reconcile()
        }
    }

    /** Called by PlaybackService from an `AnalyticsListener.onAudioInputFormatChanged`
     *  whenever the renderer's PCM output format changes. Pass null when
     *  playback ends (Player.STATE_IDLE / STATE_ENDED) so the cleared
     *  mixer attributes follow the playback lifecycle and we don't keep
     *  a stale bit-perfect claim against the device after the user
     *  stops playing. */
    fun onAudioFormatChanged(format: Format?) {
        if (Build.VERSION.SDK_INT < 34) return
        currentFormat = format
        tick.trySend(Unit)
    }

    private fun reconcile() {
        if (Build.VERSION.SDK_INT < 34) {
            _status.value = Status.Unsupported
            return
        }
        if (!enabled) {
            clearIfApplied()
            _status.value = Status.Disabled
            return
        }
        val device = usbAudioRouter.usbOutputDevice.value
        if (device == null) {
            clearIfApplied()
            _status.value = Status.NoUsbDevice
            return
        }
        val format = currentFormat
        if (format == null) {
            // No track loaded yet; don't trip status from a previous
            // Active state until we actually need to apply something.
            // clearIfApplied here would race with track transitions
            // where the new track's format hasn't arrived yet but the
            // old one's stop already cleared. Just hold steady.
            if (_status.value !in setOf(Status.Active, Status.Error)) {
                _status.value = Status.Idle
            }
            return
        }
        // Build candidate AudioFormat from the renderer's Format. If
        // the encoding isn't one we can describe to the HAL with a
        // straight integer mapping, give up — silently downconverting
        // float to int defeats "bit-perfect" and would be worse than
        // the existing routed path.
        val encoding = pcmEncodingFor(format) ?: run {
            clearIfApplied()
            _status.value = Status.FormatUnsupported
            return
        }
        val channelMask = channelMaskFor(format.channelCount)
        val candidate = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .build()

        val supported: List<AudioMixerAttributes> = runCatching {
            audioManager.getSupportedMixerAttributes(device)
        }.getOrElse {
            Log.w(TAG, "getSupportedMixerAttributes threw", it)
            emptyList()
        }
        if (supported.isEmpty()) {
            clearIfApplied()
            if (_status.value != Status.NotSupported) {
                Log.i(TAG, "HAL reports no bit-perfect mixer attrs for " +
                    "${device.productName} (id=${device.id}) — falling " +
                    "back to routed playback. OEM hasn't wired " +
                    "AUDIO_OUTPUT_FLAG_BIT_PERFECT for this DAC.")
            }
            _status.value = Status.NotSupported
            return
        }
        // Prefer an exact channel-mask match; fall back to channel-count
        // match because some HALs return wildcard / index-mask variants
        // that don't compare equal under setChannelMask above.
        val match = supported.firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                it.format.encoding == candidate.encoding &&
                it.format.sampleRate == candidate.sampleRate &&
                it.format.channelMask == candidate.channelMask
        } ?: supported.firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                it.format.encoding == candidate.encoding &&
                it.format.sampleRate == candidate.sampleRate &&
                it.format.channelCount == format.channelCount
        }
        if (match == null) {
            clearIfApplied()
            _status.value = Status.FormatUnsupported
            return
        }

        // INTENTIONALLY suppressed. We've identified that the HAL
        // would accept setPreferredMixerAttributes for this PCM
        // format on this DAC — but actually calling it interacts
        // badly with ExoPlayer's DefaultAudioSink AudioTrack
        // lifecycle on every device we've tested (OnePlus 15 /
        // OxygenOS in particular: AudioFlinger keeps closing
        // outputs faster than ExoPlayer's restoreTrack_l can
        // recreate them, the -32 retry cascade exhausts after 10
        // attempts, audio dies permanently — see field log
        // monotryptdebug20260503124933.log:227-300). UAPP / Hiby
        // avoid this by constructing AudioTrack themselves with
        // setAudioFormat(mixerAttributes!!.format) exactly matching
        // what they registered; ExoPlayer can't easily be coerced
        // into that without rewriting DefaultAudioSink.
        //
        // The routed-but-not-bit-perfect path via
        // PlaybackService's setPreferredAudioDevice collector still
        // pins playback to the USB DAC, so audio plays normally
        // through the OEM mixer (with whatever resampling
        // AudioFlinger does). For true bit-perfect on this device
        // the user falls back to the libusb exclusive toggle,
        // which is independently confirmed to work (heartbeat
        // played counter advances at exactly the source sample
        // rate, no AudioTrack involvement at all).
        //
        // Leaving the controller wired up so we still get
        // diagnostic logs (HAL bit-perfect support / format match)
        // and so flipping this back on is a one-line change once
        // either Google fixes the AudioMixerAttributes ↔ AudioTrack
        // routing race or Media3 grows a way to coerce the
        // AudioSink format. Both are tracked upstream
        // (androidx/media #415, public).
        if (BIT_PERFECT_API_CALL_SUPPRESSED) {
            if (_status.value != Status.SuppressedForCompatibility) {
                Log.i(TAG, "would engage bit-perfect on ${device.productName} " +
                    "(${match.format.sampleRate} Hz / " +
                    "${encodingLabel(match.format.encoding)} / " +
                    "${match.format.channelCount}ch) but the actual " +
                    "setPreferredMixerAttributes call is suppressed — " +
                    "ExoPlayer's AudioTrack lifecycle races with the " +
                    "AudioFlinger output reconfigure on every OEM we've " +
                    "tested. Audio still routes via setPreferredAudioDevice; " +
                    "for true bit-perfect, use the Exclusive USB DAC toggle.")
            }
            _appliedFormat.value = match
            _status.value = Status.SuppressedForCompatibility
            return
        }

        // Skip a redundant set if we're already applied with the same
        // attrs on the same physical DAC. Compare by productName, NOT
        // AudioDeviceInfo.id — OnePlus / OxygenOS reassigns the id on
        // every output reconfigure, including the one our own
        // setPreferredMixerAttributes call triggers, so id-based
        // dedupe causes a runaway loop: id 925 → engage → reroute →
        // id 1711 → re-engage → reroute → id N → ... AudioFlinger
        // never settles a single output and audio stays stuck. The
        // productName / productId / address on the same physical
        // hardware stay stable across reroutes.
        val sameHardware = appliedDevice != null &&
            appliedDevice?.productName?.toString() == device.productName?.toString() &&
            appliedDevice?.address == device.address
        if (sameHardware && _appliedFormat.value == match &&
            _status.value == Status.Active) {
            // Refresh the cached AudioDeviceInfo handle to the
            // newly-allocated instance so subsequent clearIfApplied
            // (on toggle off / unplug) targets the live device.
            appliedDevice = device
            return
        }
        // Different physical device → clear the old one before
        // applying the new.
        if (appliedDevice != null && !sameHardware) {
            clearIfApplied()
        }
        val applied = runCatching {
            audioManager.setPreferredMixerAttributes(MEDIA_AUDIO_ATTRS, device, match)
        }.onFailure {
            Log.w(TAG, "setPreferredMixerAttributes threw", it)
        }.getOrDefault(false)
        if (applied) {
            appliedDevice = device
            _appliedFormat.value = match
            _status.value = Status.Active
            Log.i(TAG, "bit-perfect engaged: ${match.format.sampleRate} Hz / " +
                "${encodingLabel(match.format.encoding)} / " +
                "${match.format.channelCount}ch on ${device.productName} " +
                "(id=${device.id}) — note: setPreferredMixerAttributes " +
                "triggers a route reconfigure, an in-flight AudioTrack " +
                "may briefly cycle (status=-32 in logcat) before settling.")
        } else {
            appliedDevice = null
            _appliedFormat.value = null
            _status.value = Status.Error
            Log.w(TAG, "setPreferredMixerAttributes returned false for " +
                "${match.format.sampleRate} / " +
                "${encodingLabel(match.format.encoding)} on " +
                "${device.productName} — staying on routed path.")
        }
    }

    private fun encodingLabel(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        else -> "encoding=$encoding"
    }

    private fun clearIfApplied() {
        val device = appliedDevice ?: return
        runCatching { audioManager.clearPreferredMixerAttributes(MEDIA_AUDIO_ATTRS, device) }
            .onFailure { Log.w(TAG, "clearPreferredMixerAttributes threw", it) }
        appliedDevice = null
        _appliedFormat.value = null
    }

    private fun pcmEncodingFor(format: Format): Int? {
        // AnalyticsListener.onAudioInputFormatChanged reports the
        // RENDERER's input format — for compressed sources (FLAC,
        // Opus, AAC, ...) the source's pcmEncoding is Format.NO_VALUE
        // because the source isn't PCM. Per Media3 convention
        // (Format.kt:pcmEncoding doc), NO_VALUE should be assumed to
        // mean ENCODING_PCM_16BIT — which is what most stock decoders
        // emit by default. So defaulting NO_VALUE → 16-bit lets us
        // claim bit-perfect on the typical 16-bit FLAC case instead
        // of falling to FormatUnsupported every time. The cost: on a
        // source whose decoder actually emits 24-bit / float (rare,
        // requires explicit codec config), we'd ask for 16-bit
        // bit-perfect and either AudioFlinger would reject the call
        // or fall back to resampling — in which case the user hears
        // either silence (audible) or non-bit-perfect (cosmetic) and
        // can flip on the libusb exclusive toggle as a known-good
        // fallback. Acceptable trade for v1; if it bites we hook the
        // sink-side configure() callback for the actual decoded
        // format instead.
        val effectivePcm = when (format.pcmEncoding) {
            Format.NO_VALUE, C.ENCODING_INVALID -> C.ENCODING_PCM_16BIT
            else -> format.pcmEncoding
        }
        return when (effectivePcm) {
            C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            // Media3 calls this PCM_24BIT (no _PACKED suffix); the
            // Android-side ENCODING_PCM_24BIT_PACKED is the matching
            // value the HAL wants when we hand it to the mixer
            // attributes. Don't conflate the two namespaces.
            C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
            // PCM_FLOAT and anything else can't be claimed as
            // bit-perfect through this API.
            else -> null
        }
    }

    private fun channelMaskFor(channelCount: Int): Int = when (channelCount) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        // Surround layouts go through these standard masks. Anything
        // else (rare on USB DACs) falls back to STEREO so the candidate
        // builder still produces a valid mask; the HAL match step will
        // reject it via the no-match path.
        4 -> AudioFormat.CHANNEL_OUT_QUAD
        6 -> AudioFormat.CHANNEL_OUT_5POINT1
        8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        else -> AudioFormat.CHANNEL_OUT_STEREO
    }

    companion object {
        private const val TAG = "PlatformBitPerfect"

        // Suppresses the actual setPreferredMixerAttributes() call.
        // See the SuppressedForCompatibility enum doc for why. Flip
        // to false once Google / Media3 fix the underlying
        // AudioTrack ↔ AudioMixerAttributes route reconfigure race
        // (tracked at androidx/media #415).
        //
        // Even with this true, the controller still does the full
        // device + format detection so the Settings UI reports
        // accurate "would engage" diagnostics, and so flipping it
        // back on is a one-line change with no other code edits
        // needed.
        private const val BIT_PERFECT_API_CALL_SUPPRESSED = true

        // Reused across every set/clear call. AudioAttributes is only
        // read by the framework, never mutated, so a singleton is safe.
        private val MEDIA_AUDIO_ATTRS: AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
    }
}
