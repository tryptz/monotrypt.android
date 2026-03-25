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
        sheetState = sheetState
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
