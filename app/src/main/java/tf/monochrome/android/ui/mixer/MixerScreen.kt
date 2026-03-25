package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerScreen(
    navController: NavController,
    viewModel: MixerViewModel
) {
    val enabled by viewModel.enabled.collectAsState()
    val buses by viewModel.buses.collectAsState()
    val selectedBusIndex by viewModel.selectedBusIndex.collectAsState()
    val showPluginPicker by viewModel.showPluginPicker.collectAsState()
    val editingPlugin by viewModel.editingPlugin.collectAsState()

    val selectedBus = buses.getOrNull(selectedBusIndex)
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Text(
                        text = if (enabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.setEnabled(it) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedBus != null) {
                FloatingActionButton(onClick = { viewModel.showAddPlugin() }) {
                    Icon(Icons.Default.Add, "Add Plugin")
                }
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // ── Bus strips (horizontal scroll) ──────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(buses) { index, bus ->
                    BusStrip(
                        bus = bus,
                        isSelected = index == selectedBusIndex,
                        onSelect = { viewModel.selectBus(index) },
                        onGainChange = { viewModel.setBusGain(index, it) },
                        onPanChange = { viewModel.setBusPan(index, it) },
                        onToggleMute = { viewModel.toggleMute(index) },
                        onToggleSolo = { viewModel.toggleSolo(index) }
                    )
                }
            }

            HorizontalDivider()

            // ── Plugin chain for selected bus ───────────────────────────
            if (selectedBus != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${selectedBus.name} — Plugin Chain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedBus.plugins.isEmpty()) {
                        Text(
                            text = "No plugins. Tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(selectedBus.plugins) { slotIndex, plugin ->
                                PluginSlot(
                                    plugin = plugin,
                                    onEdit = { viewModel.editPlugin(selectedBusIndex, slotIndex) },
                                    onToggleBypass = { viewModel.togglePluginBypass(selectedBusIndex, slotIndex) },
                                    onRemove = { viewModel.removePlugin(selectedBusIndex, slotIndex) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs / Sheets ────────────────────────────────────────────────

    if (showPluginPicker) {
        PluginPickerDialog(
            onDismiss = { viewModel.dismissPluginPicker() },
            onSelect = { viewModel.addPlugin(it) }
        )
    }

    editingPlugin?.let { (busIdx, slotIdx) ->
        val bus = buses.getOrNull(busIdx)
        val plugin = bus?.plugins?.getOrNull(slotIdx)
        if (plugin != null) {
            PluginEditorSheet(
                plugin = plugin,
                busIndex = busIdx,
                slotIndex = slotIdx,
                onParameterChange = { b, s, p, v -> viewModel.setParameter(b, s, p, v) },
                onDismiss = { viewModel.dismissPluginEditor() }
            )
        }
    }
}
