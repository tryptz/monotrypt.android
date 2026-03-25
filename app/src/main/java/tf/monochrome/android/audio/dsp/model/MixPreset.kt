package tf.monochrome.android.audio.dsp.model

import kotlinx.serialization.Serializable

@Serializable
data class MixPreset(
    val id: Long = 0,
    val name: String,
    val stateJson: String,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
