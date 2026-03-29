package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tf.monochrome.android.audio.dsp.model.MixPreset
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Horizontal preset management bar: current name, save, load, delete.
 */
@Composable
fun PresetBar(
    currentPresetName: String?,
    presets: List<MixPreset>,
    onSave: (String) -> Unit,
    onLoad: (MixPreset) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<MixPreset?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MonoDimens.spacingLg, vertical = MonoDimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MonoDimens.spacingSm)
    ) {
        // Current preset name
        Text(
            text = currentPresetName ?: "Default",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Save
        IconButton(onClick = { showSaveDialog = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save Preset",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Load
        IconButton(onClick = { showLoadMenu = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = "Load Preset",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            DropdownMenu(
                expanded = showLoadMenu,
                onDismissRequest = { showLoadMenu = false }
            ) {
                if (presets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No presets saved") },
                        onClick = { showLoadMenu = false },
                        enabled = false
                    )
                } else {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                onLoad(preset)
                                showLoadMenu = false
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showDeleteConfirm = preset; showLoadMenu = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Save dialog ──────────────────────────────────────────────────
    if (showSaveDialog) {
        var name by remember { mutableStateOf(currentPresetName ?: "") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name.trim())
                            showSaveDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete confirmation ──────────────────────────────────────────
    showDeleteConfirm?.let { preset ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Preset") },
            text = { Text("Delete \"${preset.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(preset.id)
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}
