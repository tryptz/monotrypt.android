package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tf.monochrome.android.audio.dsp.SnapinType
import tf.monochrome.android.audio.dsp.model.PluginInstance
import tf.monochrome.android.ui.theme.MonoDimens

// Parameter metadata per snapin type
data class ParamDef(
    val name: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val unit: String = ""
)

private fun getParamDefs(type: SnapinType?): List<ParamDef> = when (type) {
    SnapinType.GAIN -> listOf(
        ParamDef("Gain", -100f, 24f, 0f, "dB")
    )
    SnapinType.STEREO -> listOf(
        ParamDef("Mid", -24f, 24f, 0f, "dB"),
        ParamDef("Width", -24f, 24f, 0f, "dB"),
        ParamDef("Pan", -1f, 1f, 0f, "")
    )
    SnapinType.FILTER -> listOf(
        ParamDef("Type", 0f, 6f, 0f, ""),
        ParamDef("Cutoff", 20f, 20000f, 1000f, "Hz"),
        ParamDef("Q", 0.1f, 20f, 0.707f, ""),
        ParamDef("Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Slope", 0f, 3f, 0f, "x")
    )
    SnapinType.EQ_3BAND -> listOf(
        ParamDef("Low-Mid Split", 20f, 5000f, 200f, "Hz"),
        ParamDef("Mid-High Split", 200f, 20000f, 5000f, "Hz"),
        ParamDef("Low Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Mid Gain", -24f, 24f, 0f, "dB"),
        ParamDef("High Gain", -24f, 24f, 0f, "dB")
    )
    SnapinType.COMPRESSOR -> listOf(
        ParamDef("Attack", 0.1f, 300f, 10f, "ms"),
        ParamDef("Release", 1f, 3000f, 100f, "ms"),
        ParamDef("Mode", 0f, 1f, 0f, ""),
        ParamDef("Ratio", 1f, 100f, 4f, ":1"),
        ParamDef("Threshold", -60f, 0f, -18f, "dB"),
        ParamDef("Makeup", 0f, 40f, 0f, "dB"),
        ParamDef("Sidechain", 0f, 1f, 0f, "")
    )
    SnapinType.LIMITER -> listOf(
        ParamDef("Input Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Output Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Threshold", -24f, 0f, 0f, "dB"),
        ParamDef("Release", 1f, 1000f, 100f, "ms")
    )
    SnapinType.GATE -> listOf(
        ParamDef("Attack", 0.01f, 100f, 0.1f, "ms"),
        ParamDef("Hold", 0f, 500f, 50f, "ms"),
        ParamDef("Release", 1f, 2000f, 100f, "ms"),
        ParamDef("Threshold", -80f, 0f, -30f, "dB"),
        ParamDef("Tolerance", 0f, 24f, 6f, "dB"),
        ParamDef("Range", 0f, 80f, 80f, "dB"),
        ParamDef("Look-ahead", 0f, 1f, 0f, ""),
        ParamDef("Flip", 0f, 1f, 0f, ""),
        ParamDef("Sidechain", 0f, 1f, 0f, "")
    )
    SnapinType.DYNAMICS -> listOf(
        ParamDef("Low Threshold", -60f, 0f, -40f, "dB"),
        ParamDef("Low Ratio", 0.5f, 4f, 1f, ":1"),
        ParamDef("High Threshold", -60f, 0f, -12f, "dB"),
        ParamDef("High Ratio", 1f, 100f, 4f, ":1"),
        ParamDef("Attack", 0.1f, 300f, 10f, "ms"),
        ParamDef("Release", 1f, 3000f, 100f, "ms"),
        ParamDef("Knee", 0f, 24f, 6f, "dB"),
        ParamDef("Input Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Output Gain", -24f, 24f, 0f, "dB"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.COMPACTOR -> listOf(
        ParamDef("Attack", 0f, 50f, 5f, "ms"),
        ParamDef("Hold", 0f, 500f, 10f, "ms"),
        ParamDef("Release", 1f, 1000f, 100f, "ms"),
        ParamDef("Threshold", -60f, 0f, -12f, "dB"),
        ParamDef("Mode", 0f, 2f, 1f, ""),
        ParamDef("Range", 0f, 200f, 100f, "%"),
        ParamDef("Stereo", 0f, 100f, 0f, "%"),
        ParamDef("Sidechain", 0f, 1f, 0f, "")
    )
    SnapinType.TRANSIENT_SHAPER -> listOf(
        ParamDef("Attack", -100f, 100f, 0f, "%"),
        ParamDef("Pump", 0f, 100f, 0f, "%"),
        ParamDef("Sustain", -100f, 100f, 0f, "%"),
        ParamDef("Speed", 0f, 100f, 50f, "%"),
        ParamDef("Clip", 0f, 1f, 0f, ""),
        ParamDef("Sidechain", 0f, 1f, 0f, "")
    )
    SnapinType.DISTORTION -> listOf(
        ParamDef("Drive", 0f, 48f, 12f, "dB"),
        ParamDef("Bias", -1f, 1f, 0f, ""),
        ParamDef("Spread", 0f, 100f, 0f, "%"),
        ParamDef("Type", 0f, 5f, 0f, ""),
        ParamDef("Dynamics", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.SHAPER -> listOf(
        ParamDef("Drive", 0f, 48f, 0f, "dB"),
        ParamDef("Mix", 0f, 100f, 100f, "%"),
        ParamDef("Overflow", 0f, 2f, 0f, ""),
        ParamDef("DC Filter", 0f, 1f, 1f, "")
    )
    SnapinType.CHORUS -> listOf(
        ParamDef("Delay", 1f, 40f, 7f, "ms"),
        ParamDef("Rate", 0.01f, 10f, 1f, "Hz"),
        ParamDef("Depth", 0f, 100f, 50f, "%"),
        ParamDef("Spread", 0f, 100f, 50f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%"),
        ParamDef("Taps", 1f, 6f, 2f, "")
    )
    SnapinType.ENSEMBLE -> listOf(
        ParamDef("Voices", 2f, 8f, 4f, ""),
        ParamDef("Detune", 0f, 100f, 50f, "%"),
        ParamDef("Spread", 0f, 100f, 80f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%"),
        ParamDef("Motion", 0f, 2f, 0f, "")
    )
    SnapinType.FLANGER -> listOf(
        ParamDef("Delay", 0.1f, 10f, 1f, "ms"),
        ParamDef("Depth", 0f, 100f, 50f, "%"),
        ParamDef("Rate", 0.01f, 10f, 0.5f, "Hz"),
        ParamDef("Scroll", 0f, 1f, 0f, ""),
        ParamDef("Offset", 0f, 360f, 0f, "deg"),
        ParamDef("Motion", 0f, 10f, 0f, "Hz"),
        ParamDef("Spread", 0f, 100f, 0f, "%"),
        ParamDef("Feedback", -100f, 100f, 30f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%")
    )
    SnapinType.PHASER -> listOf(
        ParamDef("Order", 2f, 12f, 4f, ""),
        ParamDef("Cutoff", 20f, 20000f, 1000f, "Hz"),
        ParamDef("Depth", 0f, 100f, 50f, "%"),
        ParamDef("Rate", 0.01f, 10f, 0.5f, "Hz"),
        ParamDef("Spread", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%")
    )
    SnapinType.DELAY -> listOf(
        ParamDef("Time", 1f, 2000f, 250f, "ms"),
        ParamDef("Sync", 0f, 1f, 0f, ""),
        ParamDef("Feedback", 0f, 100f, 30f, "%"),
        ParamDef("Pan", -100f, 100f, 0f, ""),
        ParamDef("Ping-Pong", 0f, 1f, 0f, ""),
        ParamDef("Duck", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%")
    )
    SnapinType.REVERB -> listOf(
        ParamDef("Decay", 0.1f, 30f, 2f, "s"),
        ParamDef("Dampen", 0f, 100f, 50f, "%"),
        ParamDef("Size", 0f, 100f, 50f, "%"),
        ParamDef("Width", 0f, 100f, 100f, "%"),
        ParamDef("Early", 0f, 100f, 50f, "%"),
        ParamDef("Mix", 0f, 100f, 30f, "%")
    )
    SnapinType.BITCRUSH -> listOf(
        ParamDef("Rate", 200f, 48000f, 48000f, "Hz"),
        ParamDef("Bits", 1f, 24f, 24f, ""),
        ParamDef("ADC Quality", 0f, 100f, 100f, "%"),
        ParamDef("DAC Quality", 0f, 100f, 100f, "%"),
        ParamDef("Dither", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.COMB_FILTER -> listOf(
        ParamDef("Cutoff", 20f, 20000f, 440f, "Hz"),
        ParamDef("Mix", 0f, 100f, 50f, "%"),
        ParamDef("Polarity", 0f, 1f, 0f, ""),
        ParamDef("Stereo", 0f, 1f, 0f, "")
    )
    SnapinType.CHANNEL_MIXER -> listOf(
        ParamDef("L\u2192L", -1f, 1f, 1f, ""),
        ParamDef("R\u2192L", -1f, 1f, 0f, ""),
        ParamDef("L\u2192R", -1f, 1f, 0f, ""),
        ParamDef("R\u2192R", -1f, 1f, 1f, "")
    )
    SnapinType.FORMANT_FILTER -> listOf(
        ParamDef("Vowel X", 0f, 1f, 0.5f, ""),
        ParamDef("Vowel Y", 0f, 1f, 0.5f, ""),
        ParamDef("Q", 0.5f, 20f, 5f, ""),
        ParamDef("Lows", 0f, 100f, 0f, "%"),
        ParamDef("Highs", 0f, 100f, 0f, "%")
    )
    SnapinType.FREQUENCY_SHIFTER -> listOf(
        ParamDef("Shift", -5000f, 5000f, 0f, "Hz")
    )
    SnapinType.HAAS -> listOf(
        ParamDef("Channel", 0f, 1f, 0f, ""),
        ParamDef("Delay", 0f, 30f, 10f, "ms")
    )
    SnapinType.LADDER_FILTER -> listOf(
        ParamDef("Cutoff", 20f, 20000f, 1000f, "Hz"),
        ParamDef("Resonance", 0f, 100f, 0f, "%"),
        ParamDef("Topology", 0f, 1f, 0f, ""),
        ParamDef("Saturate", 0f, 1f, 0f, ""),
        ParamDef("Drive", 0f, 48f, 0f, "dB"),
        ParamDef("Bias", -1f, 1f, 0f, "")
    )
    SnapinType.NONLINEAR_FILTER -> listOf(
        ParamDef("Type", 0f, 3f, 0f, ""),
        ParamDef("Cutoff", 20f, 20000f, 1000f, "Hz"),
        ParamDef("Q", 0.1f, 20f, 0.707f, ""),
        ParamDef("Drive", 0f, 48f, 0f, "dB"),
        ParamDef("Mode", 0f, 4f, 0f, "")
    )
    SnapinType.PHASE_DISTORTION -> listOf(
        ParamDef("Drive", 0f, 100f, 30f, "%"),
        ParamDef("Normalize", 0f, 1f, 0f, ""),
        ParamDef("Tone", 0f, 100f, 100f, "%"),
        ParamDef("Bias", -3.14f, 3.14f, 0f, "rad"),
        ParamDef("Spread", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.PITCH_SHIFTER -> listOf(
        ParamDef("Pitch", -24f, 24f, 0f, "st"),
        ParamDef("Jitter", 0f, 100f, 0f, "%"),
        ParamDef("Grain Size", 10f, 200f, 50f, "ms"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.RESONATOR -> listOf(
        ParamDef("Pitch", 0f, 127f, 69f, ""),
        ParamDef("Decay", 0f, 100f, 50f, "%"),
        ParamDef("Intensity", 0f, 100f, 50f, "%"),
        ParamDef("Timbre", 0f, 1f, 0f, ""),
        ParamDef("Mix", 0f, 100f, 50f, "%")
    )
    SnapinType.REVERSER -> listOf(
        ParamDef("Time", 50f, 2000f, 250f, "ms"),
        ParamDef("Sync", 0f, 1f, 0f, ""),
        ParamDef("Crossfade", 1f, 50f, 10f, "%"),
        ParamDef("Mix", 0f, 100f, 50f, "%")
    )
    SnapinType.RING_MOD -> listOf(
        ParamDef("Frequency", 1f, 5000f, 440f, "Hz"),
        ParamDef("Bias", 0f, 100f, 0f, "%"),
        ParamDef("Rectify", -100f, 100f, 0f, "%"),
        ParamDef("Spread", 0f, 100f, 0f, "%"),
        ParamDef("Mix", 0f, 100f, 100f, "%")
    )
    SnapinType.TAPE_STOP -> listOf(
        ParamDef("Play", 0f, 1f, 1f, ""),
        ParamDef("Stop Time", 50f, 5000f, 500f, "ms"),
        ParamDef("Start Time", 50f, 5000f, 500f, "ms"),
        ParamDef("Curve", 0f, 100f, 50f, "%")
    )
    SnapinType.TRANCE_GATE -> listOf(
        ParamDef("Pattern", 0f, 7f, 0f, ""),
        ParamDef("Length", 1f, 32f, 16f, ""),
        ParamDef("Attack", 0.1f, 100f, 1f, "ms"),
        ParamDef("Decay", 0.1f, 500f, 50f, "ms"),
        ParamDef("Sustain", 0f, 100f, 100f, "%"),
        ParamDef("Release", 0.1f, 500f, 10f, "ms"),
        ParamDef("Mix", 0f, 100f, 100f, "%"),
        ParamDef("Resolution", 0f, 3f, 2f, "")
    )
    else -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginEditorSheet(
    plugin: PluginInstance,
    busIndex: Int,
    slotIndex: Int,
    onParameterChange: (busIndex: Int, slotIndex: Int, paramIndex: Int, value: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val paramDefs = getParamDefs(plugin.type)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = MonoDimens.cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = plugin.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            paramDefs.forEachIndexed { paramIndex, def ->
                val currentValue = plugin.parameters[paramIndex] ?: def.default

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = def.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatValue(currentValue, def),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = currentValue,
                    onValueChange = { onParameterChange(busIndex, slotIndex, paramIndex, it) },
                    valueRange = def.min..def.max,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatValue(value: Float, def: ParamDef): String {
    val formatted = if (def.max - def.min > 100) {
        "%.0f".format(value)
    } else if (def.max - def.min > 10) {
        "%.1f".format(value)
    } else {
        "%.2f".format(value)
    }
    return if (def.unit.isNotEmpty()) "$formatted ${def.unit}" else formatted
}
