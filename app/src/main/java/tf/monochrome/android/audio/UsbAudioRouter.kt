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
        // Compare by productName + address (stable across AudioFlinger
        // output reconfigures), NOT by AudioDeviceInfo.id (ephemeral
        // — OnePlus / OxygenOS reassigns id every time
        // setPreferredMixerAttributes triggers an output reconfigure,
        // 925 → 1711 → ... within ~15 seconds in field logs). The id
        // is also reassigned whenever AudioFlinger rebuilds the route
        // for any reason. Keying dedupe on productName+address means
        // the same physical Bathys stays the same StateFlow value
        // across all internal reroutes; only an actual plug/unplug
        // emits, which is the only case where the downstream
        // setPreferredAudioDevice collector should re-fire.
        //
        // Without this, the routing collector re-fired on every
        // reroute and AudioFlinger never settled a single output →
        // "ioConfigChanged() closing unknown output" cascade and
        // audio stayed permanently stuck on the platform-bit-perfect
        // path.
        if (!sameHardware(current, next)) {
            _usbOutputDevice.value = next
        }
    }

    private fun sameHardware(a: AudioDeviceInfo?, b: AudioDeviceInfo?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.productName?.toString() == b.productName?.toString() &&
            a.address == b.address
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
