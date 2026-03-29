package tf.monochrome.android.ui.mixer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import tf.monochrome.android.ui.components.bounceClick
import tf.monochrome.android.ui.components.liquidGlass
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Collapsible bottom panel showing the plugin chain for the selected bus.
 */
@Composable
fun PluginRack(
    bus: BusConfig?,
    onPluginEdit: (busIndex: Int, slotIndex: Int) -> Unit,
    onPluginBypass: (busIndex: Int, slotIndex: Int) -> Unit,
    onPluginRemove: (busIndex: Int, slotIndex: Int) -> Unit,
    onAddPlugin: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = bus != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        bus?.let { b ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = MonoDimens.shapeLg)
                    .padding(MonoDimens.spacingMd)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${b.name} — Plugins",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${b.plugins.size} / 16",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(MonoDimens.spacingSm))

                // Plugin cards (horizontal scroll)
                LazyRow(
                    contentPadding = PaddingValues(end = MonoDimens.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(MonoDimens.spacingSm)
                ) {
                    itemsIndexed(b.plugins) { slotIndex, plugin ->
                        PluginCard(
                            plugin = plugin,
                            onEdit = { onPluginEdit(b.index, slotIndex) },
                            onBypass = { onPluginBypass(b.index, slotIndex) },
                            onRemove = { onPluginRemove(b.index, slotIndex) }
                        )
                    }

                    // Add button card
                    item {
                        AddPluginCard(onClick = onAddPlugin)
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginInstance,
    onEdit: () -> Unit,
    onBypass: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .liquidGlass(shape = MonoDimens.shapeSm)
            .bounceClick(onClick = onEdit)
            .padding(MonoDimens.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MonoDimens.spacingXs)
    ) {
        // Bypass indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (plugin.bypassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else Color(0xFF4CAF50)
                )
        )

        // Name
        Text(
            text = plugin.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Bypass toggle
        IconButton(
            onClick = onBypass,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = "Bypass",
                tint = if (plugin.bypassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddPluginCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(80.dp)
            .liquidGlass(shape = MonoDimens.shapeSm)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Plugin",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
