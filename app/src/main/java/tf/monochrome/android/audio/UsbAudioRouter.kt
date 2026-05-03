package tf.monochrome.android.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the currently-attached USB Audio Class DAC (if any) so the
 * ExoPlayer instance in PlaybackService can be pinned to it via
 * `setPreferredAudioDevice` when the user has bit-perfect routing on.
 *
 * Pure observation via AudioManager.AudioDeviceCallback — we do not
 * open a USB connection through UsbManager directly; that would
 * conflict with Android's audio HAL which is already serving the DAC
 * as a normal output. The "true bypass" path (libusb-backed UAC
 * driver à la USB Audio Player Pro) is a much bigger feature; this
 * router gives the realistic Android-supported version: route the
 * stream to the USB device, with no downsample as long as the source
 * sample rate matches one the DAC supports.
 *
 * Devices with no USB host port (tablets/TVs that surfaced
 * `android.hardware.usb.host` as false) will simply never report a
 * USB output here and the StateFlow stays null — callers no-op.
 */
@Singleton
class UsbAudioRouter @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val callbackHandler = Handler(Looper.getMainLooper())

    private val _usbOutputDevice = MutableStateFlow<AudioDeviceInfo?>(initialUsbDevice())
    val usbOutputDevice: StateFlow<AudioDeviceInfo?> = _usbOutputDevice.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    refresh()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    refresh()
                }
            },
            callbackHandler,
        )
    }

    private fun refresh() {
        val current = _usbOutputDevice.value
        val next = currentUsbOutput()
        // Compare by id, not by reference / Object.equals. AudioManager
        // can hand us a freshly-allocated AudioDeviceInfo for the same
        // logical device on every onAudioDevicesAdded/Removed callback
        // — and AudioFlinger fires those internally whenever it
        // reconfigures an output (e.g. when an app calls
        // setPreferredMixerAttributes for bit-perfect mode). The
        // resulting "different instance, same DAC" stream made
        // PlaybackService's setPreferredAudioDevice collector re-fire
        // on every track transition, which kicked AudioFlinger into
        // closing/recreating the in-flight AudioTrack →
        // `restoreTrack_l ... status -32` retry cascades visible in
        // the user-side logs from 2026-05-03. Keying dedupe on
        // AudioDeviceInfo.id breaks that loop: same DAC stays the
        // same StateFlow value across reroutes, plug/unplug emits a
        // new id and only then the route is re-applied.
        if (current?.id != next?.id) {
            _usbOutputDevice.value = next
        }
    }

    private fun initialUsbDevice(): AudioDeviceInfo? = currentUsbOutput()

    private fun currentUsbOutput(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // Prefer USB_HEADSET (DAC + headphone amp combo, most external
        // dongles) over generic USB_DEVICE; if both exist, headset wins.
        return outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
    }

    /** Human-readable label for the Settings screen. */
    fun describe(device: AudioDeviceInfo): String =
        device.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: when (device.type) {
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
                else -> "USB Output"
            }
}
