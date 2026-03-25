package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tf.monochrome.android.audio.dsp.model.BusConfig

@Composable
fun BusStrip(
    bus: BusConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onGainChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onSelect() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Bus name
        Text(
            text = bus.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Plugin count
        Text(
            text = "${bus.plugins.size} fx",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Pan knob (simplified as slider)
        Text(text = "Pan", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        Slider(
            value = bus.pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        // Gain fader
        Text(
            text = if (bus.gainDb <= -100f) "-inf" else "${bus.gainDb.toInt()} dB",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
        Slider(
            value = bus.gainDb,
            onValueChange = onGainChange,
            valueRange = -60f..24f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Mute / Solo buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mute button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (bus.muted) Color(0xFFE53935) else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onToggleMute() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (bus.muted) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }

            // Solo button
            if (!bus.isMaster) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (bus.soloed) Color(0xFFFFC107) else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { onToggleSolo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bus.soloed) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
