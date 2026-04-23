package tf.monochrome.android.ui.mixer.macro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tf.monochrome.android.audio.dsp.model.BusConfig
import tf.monochrome.android.ui.components.bounceClick
import tf.monochrome.android.ui.components.liquidGlass
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Compact bus card for the Macro overview.
 * Shows bus name, plugin count, mini VU bar, and mute/solo indicators.
 */
@Composable
fun MacroBusCard(
    bus: BusConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMaster = bus.isMaster
    val accentColor = if (isMaster) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier
            .width(if (isMaster) 100.dp else 80.dp)
            .liquidGlass(
                shape = MonoDimens.shapeMd,
                borderAlpha = if (isSelected) MonoDimens.glassBorderAlpha * 3f
                else MonoDimens.glassBorderAlpha
            )
            .bounceClick(onClick = onSelect)
            .padding(MonoDimens.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Bus name
        Text(
            text = bus.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (isSelected) accentColor
            else MaterialTheme.colorScheme.onSurface
        )

        // Plugin count
        if (bus.plugins.isNotEmpty()) {
            Text(
                text = "${bus.plugins.size} fx",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Mini VU bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .padding(horizontal = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            // Background
            drawRoundRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f)
            )
            // Level
            val level = ((bus.gainDb + 60f) / 84f).coerceIn(0f, 1f)
            if (level > 0f && !bus.muted) {
                val barColor = when {
                    level > 0.85f -> Color(0xFFE53935)
                    level > 0.7f -> Color(0xFFFFC107)
                    else -> Color(0xFF4CAF50)
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(w * level, h),
                    cornerRadius = CornerRadius(h / 2f)
                )
            }
        }

        // dB readout
        Text(
            text = if (bus.gainDb <= -60f) "-inf" else "%.1f".format(bus.gainDb),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Mute / Solo row
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mute
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (bus.muted) Color(0xFFE53935)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .bounceClick(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (bus.muted) Color.White
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Solo (not on master)
            if (!isMaster) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (bus.soloed) Color(0xFFFFC107)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .bounceClick(onClick = onToggleSolo),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bus.soloed) Color.Black
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
