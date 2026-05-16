package tf.monochrome.android.audio.dsp.model

import kotlinx.serialization.Serializable

/**
 * On-disk envelope for an exported DSP Mixer preset. This is what gets written
 * to / read from a `.json` file when a user shares a preset.
 */
@Serializable
data class MixPresetFile(
    val version: Int = 1,
    val name: String,
    val stateJson: String,
    val exportedAt: Long = System.currentTimeMillis()
)
