package tf.monochrome.android.ui.mixer.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import tf.monochrome.android.ui.components.bounceClick
import tf.monochrome.android.ui.components.liquidGlass
import tf.monochrome.android.ui.mixer.canvas.model.DspNode
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Slim bottom control panel anchored to the bottom of the mixer.
 *
 * When a node is selected: shows Volume, Pan, Mute/Solo (56dp).
 * When nothing is selected: shows DSP enable toggle + preset name (36dp).
 */
@Composable
fun MacroControlPanel(
    selectedNode: DspNode?,
    selectedBus: BusConfig?,
    dspEnabled: Boolean,
    currentPresetName: String?,
    onEnabledChange: (Boolean) -> Unit,
    onGainChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .liquidGlass(
                shape = MonoDimens.shapeLg,
                tintAlpha = 0.35f
            )
            .padding(horizontal = MonoDimens.spacingMd)
    ) {
        AnimatedVisibility(
            visible = selectedNode != null && selectedBus != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            // ── Expanded: full controls ─────────────────────────────────
            if (selectedBus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Volume fader + label
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MiniFader(
                            gainDb = selectedBus.gainDb,
                            onGainChange = onGainChange
                        )
                        Text(
                            text = if (selectedBus.gainDb <= -60f) "-inf dB"
                            else "%.1f dB".format(selectedBus.gainDb),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Pan knob + label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MiniPanKnob(
                            value = selectedBus.pan,
                            onValueChange = onPanChange
                        )
                        val panLabel = when {
                            selectedBus.pan < -0.05f -> "L%.0f".format(-selectedBus.pan * 100)
                            selectedBus.pan > 0.05f -> "R%.0f".format(selectedBus.pan * 100)
                            else -> "C"
                        }
                        Text(
                            text = panLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Mute button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedBus.muted) Color(0xFFE53935)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .bounceClick(onClick = onToggleMute),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedBus.muted) Color.White
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Solo button (not for master)
                    if (!selectedBus.isMaster) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedBus.soloed) Color(0xFFFFC107)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .bounceClick(onClick = onToggleSolo),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedBus.soloed) Color.Black
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // ── Collapsed: always visible base bar ──────────────────────────
        AnimatedVisibility(
            visible = selectedNode == null,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Preset name
                Text(
                    text = currentPresetName ?: "Default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // DSP enable toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (dspEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dspEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = dspEnabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
