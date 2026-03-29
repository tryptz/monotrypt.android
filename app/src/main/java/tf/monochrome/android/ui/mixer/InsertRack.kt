package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tf.monochrome.android.audio.dsp.model.BusConfig
import tf.monochrome.android.audio.dsp.model.PluginInstance
import tf.monochrome.android.ui.components.liquidGlass
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * FL Studio-style insert effect rack — right-side panel.
 *
 * Shows numbered effect slots for the selected bus, with inline
 * plugin parameter editing.  Uses liquidGlass surfaces and MaterialTheme
 * colours so it blends with the rest of the app's aesthetic.
 *
 * Matches the "Mixer – Insert N" panel from FL Studio's mixer layout.
 */
@Composable
fun InsertRack(
    bus: BusConfig?,
    busIndex: Int,
    editingPlugin: Pair<Int, Int>?,
    onSlotTap: (slotIndex: Int) -> Unit,
    onAddPlugin: () -> Unit,
    onPluginBypass: (busIndex: Int, slotIndex: Int) -> Unit,
    onPluginRemove: (busIndex: Int, slotIndex: Int) -> Unit,
    onParameterChange: (busIndex: Int, slotIndex: Int, paramIndex: Int, value: Float) -> Unit,
    onDismissEditor: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val maxSlots = 6   // max plugins per bus in our DSP engine

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp)
            .liquidGlass(
                shape     = MonoDimens.shapeSm,
                tintAlpha = 0.30f
            )
    ) {
        // ── Header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonoDimens.spacingSm, vertical = MonoDimens.spacingSm),
            verticalAlignment  = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = "Mixer – ${bus?.name ?: "Insert"}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            IconButton(
                onClick  = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(
            color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            modifier = Modifier.padding(horizontal = MonoDimens.spacingSm)
        )

        // ── Slot list ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(MonoDimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val plugins = bus?.plugins ?: emptyList()

            for (slotIndex in 0 until maxSlots) {
                val plugin    = plugins.getOrNull(slotIndex)
                val isEditing = editingPlugin == Pair(busIndex, slotIndex)

                InsertSlot(
                    slotIndex = slotIndex,
                    plugin    = plugin,
                    isEditing = isEditing,
                    onTap     = {
                        if (plugin != null) onSlotTap(slotIndex)
                        else onAddPlugin()
                    },
                    onBypass  = {
                        if (plugin != null) onPluginBypass(busIndex, slotIndex)
                    }
                )

                // Inline plugin editor (expands below the slot)
                if (isEditing && plugin != null) {
                    InlinePluginEditor(
                        plugin           = plugin,
                        busIndex         = busIndex,
                        slotIndex        = slotIndex,
                        onParameterChange = onParameterChange,
                        onDismiss        = onDismissEditor
                    )
                }
            }
        }
    }
}

// ── Single insert slot row ──────────────────────────────────────────────

@Composable
private fun InsertSlot(
    slotIndex: Int,
    plugin: PluginInstance?,
    isEditing: Boolean,
    onTap: () -> Unit,
    onBypass: () -> Unit
) {
    val bgAlpha = if (isEditing) 0.15f else 0.08f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape       = MonoDimens.shapeSm,
                tintAlpha   = bgAlpha,
                borderAlpha = if (isEditing) MonoDimens.glassBorderAlpha * 2f
                else MonoDimens.glassBorderAlpha
            )
            .clickable(onClick = onTap)
            .padding(horizontal = MonoDimens.spacingSm, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MonoDimens.spacingXs)
    ) {
        // Active indicator dot
        if (plugin != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (plugin.bypassed)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else Color(0xFF4CAF50)
                    )
            )
        }

        // Slot label / plugin name
        Text(
            text       = plugin?.displayName ?: "Slot ${slotIndex + 1}",
            style      = MaterialTheme.typography.labelSmall,
            fontSize   = 10.sp,
            fontWeight = if (plugin != null) FontWeight.Medium else FontWeight.Normal,
            color      = if (plugin != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f)
        )

        // Bypass toggle (for loaded plugins)
        if (plugin != null) {
            IconButton(
                onClick  = onBypass,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Bypass",
                    tint     = if (plugin.bypassed)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            // Add hint for empty slots
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Plugin",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ── Inline parameter editor (shown below the slot) ──────────────────────

@Composable
private fun InlinePluginEditor(
    plugin: PluginInstance,
    busIndex: Int,
    slotIndex: Int,
    onParameterChange: (Int, Int, Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val paramDefs = getParamDefs(plugin.type)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape       = MonoDimens.shapeSm,
                tintAlpha   = 0.20f,
                borderAlpha = MonoDimens.glassBorderAlpha * 2f
            )
            .padding(horizontal = MonoDimens.spacingSm, vertical = MonoDimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Plugin name + close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = plugin.displayName,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Parameter sliders
        paramDefs.forEachIndexed { paramIndex, def ->
            val currentValue = plugin.parameters[paramIndex] ?: def.default

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = def.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = formatParamValue(currentValue, def),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value         = currentValue,
                    onValueChange = { onParameterChange(busIndex, slotIndex, paramIndex, it) },
                    valueRange    = def.min..def.max,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor        = MaterialTheme.colorScheme.primary,
                        activeTrackColor  = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun formatParamValue(value: Float, def: ParamDef): String {
    val formatted = if (def.max - def.min > 100) {
        "%.0f".format(value)
    } else if (def.max - def.min > 10) {
        "%.1f".format(value)
    } else {
        "%.2f".format(value)
    }
    return if (def.unit.isNotEmpty()) "$formatted ${def.unit}" else formatted
}
