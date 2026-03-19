package tf.monochrome.android.ui.eq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.ui.components.bounceClick
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: EqViewModel = hiltViewModel()
) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val currentBands by viewModel.currentBands.collectAsState()
    val currentPreamp by viewModel.currentPreamp.collectAsState()
    val availableTargets by viewModel.availableTargets.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val allPresets by viewModel.allPresets.collectAsState()
    val error by viewModel.error.collectAsState()
    val isCalculating by viewModel.isCalculating.collectAsState()
    val originalMeasurement by viewModel.originalMeasurement.collectAsState()
    val selectedHeadphone by viewModel.selectedHeadphone.collectAsState()
    val bandCount by viewModel.bandCount.collectAsState()
    val maxFrequency by viewModel.maxFrequency.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val headphoneTypeFilter by viewModel.headphoneTypeFilter.collectAsState()
    val availableHeadphones by viewModel.availableHeadphones.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }
    var showHeadphoneSelect by remember { mutableStateOf(false) }
    var showMeasurementUpload by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var showBandsExpanded by remember { mutableStateOf(true) }
    var saveName by remember { mutableStateOf("") }
    var saveDescription by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ─── Title Section ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                "PRECISION AUTOEQ",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Headphone correction filters generator.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ─── Interactive Frequency Graph ───
            item {
                FrequencyResponseGraph(
                    originalCurve = originalMeasurement,
                    targetCurve = selectedTarget.data,
                    eqBands = currentBands,
                    preamp = currentPreamp,
                    sampleRate = sampleRate,
                    onBandDragged = { bandId, freq, gain ->
                        viewModel.updateBandByDrag(bandId, freq, gain)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ─── Category Tabs (ALL / OVER-EAR / IN-EAR) ───
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabChip(
                        label = "ALL",
                        isSelected = headphoneTypeFilter == null,
                        onClick = { viewModel.setHeadphoneTypeFilter(null) },
                        modifier = Modifier.weight(1f)
                    )
                    TabChip(
                        label = "OVER-EAR",
                        isSelected = headphoneTypeFilter == "over-ear",
                        onClick = { viewModel.setHeadphoneTypeFilter("over-ear") },
                        modifier = Modifier.weight(1f)
                    )
                    TabChip(
                        label = "IN-EAR",
                        isSelected = headphoneTypeFilter == "in-ear",
                        onClick = { viewModel.setHeadphoneTypeFilter("in-ear") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Headphone Model Selector ───
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SectionLabel("HEADPHONE MODEL")
                    SelectorRow(
                        value = selectedHeadphone?.name ?: "Select headphone...",
                        onClick = { showHeadphoneSelect = true },
                        trailingIcon = {
                            IconButton(
                                onClick = { showMeasurementUpload = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Custom measurement",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            // ─── Target Curve Selector ───
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SectionLabel("TARGET")
                    Box {
                        SelectorRow(
                            value = selectedTarget.label,
                            onClick = { showTargetMenu = true },
                            trailingIcon = {
                                IconButton(
                                    onClick = { /* compare targets */ },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Target settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false }
                        ) {
                            availableTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.label) },
                                    onClick = {
                                        viewModel.selectTarget(target.id)
                                        showTargetMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ─── Parameters Row (Filter Bands / Max Hz / Sample Rate) ───
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ParameterDropdown(
                        label = "FILTER BANDS",
                        value = bandCount.toString(),
                        options = listOf("5", "10", "15", "20", "31"),
                        onValueChanged = { viewModel.setBandCount(it.toInt()) },
                        modifier = Modifier.weight(1f)
                    )
                    ParameterDropdown(
                        label = "MAX HZ",
                        value = formatFreqLabel(maxFrequency),
                        options = listOf("8k", "12k", "16k", "20k"),
                        onValueChanged = {
                            viewModel.setMaxFrequency(parseFreqLabel(it))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ParameterDropdown(
                        label = "SAMPLE RATE",
                        value = formatSampleRate(sampleRate),
                        options = listOf("44.1k", "48k", "96k", "192k"),
                        onValueChanged = {
                            viewModel.setSampleRate(parseSampleRate(it))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Action Row (Download + AutoEQ Button) ───
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* export EQ preset */ },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export EQ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GradientAutoEqButton(
                        isCalculating = isCalculating,
                        onClick = { viewModel.runAutoEq() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Database Section ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Database",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "AutoEq Repo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${availableHeadphones.size} models",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showHeadphoneSelect = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Search model (e.g. HD 600)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ─── Error Display ───
            if (!error.isNullOrEmpty()) {
                item {
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                }
            }

            // ─── Collapsible EQ Bands Section ───
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBandsExpanded = !showBandsExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PARAMETRIC EQ FILTERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        imageVector = if (showBandsExpanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showBandsExpanded) {
                // Preamp
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Preamp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${currentPreamp.roundToInt()} dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = if (currentPreamp.isNaN()) 0f else currentPreamp.coerceIn(-12f, 12f),
                            onValueChange = { viewModel.setPreamp(it) },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Band sliders
                items(currentBands) { band ->
                    EqBandSlider(
                        band = band,
                        onBandChanged = { viewModel.updateBand(band.id, it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Action buttons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .bounceClick(onClick = { viewModel.resetToFlat() }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .bounceClick(onClick = { showSaveDialog = true }),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Text("Save", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Dialogs ───

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save EQ Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Preset Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = saveDescription,
                        onValueChange = { saveDescription = it },
                        label = { Text("Description") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveName.isNotEmpty()) {
                            viewModel.saveAsPreset(saveName, saveDescription)
                            showSaveDialog = false
                            saveName = ""
                            saveDescription = ""
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showHeadphoneSelect) {
        AlertDialog(
            onDismissRequest = { showHeadphoneSelect = false },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            content = {
                HeadphoneSelectScreen(
                    viewModel = viewModel,
                    onHeadphoneSelected = { showHeadphoneSelect = false },
                    onDismiss = { showHeadphoneSelect = false }
                )
            }
        )
    }

    if (showMeasurementUpload) {
        AlertDialog(
            onDismissRequest = { showMeasurementUpload = false },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            ),
            content = {
                MeasurementUploadScreen(
                    viewModel = viewModel,
                    onDismiss = { showMeasurementUpload = false },
                    onCalibrationComplete = { showMeasurementUpload = false }
                )
            }
        )
    }
}

@Composable
fun EqBandSlider(
    band: EqBand,
    onBandChanged: (EqBand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // --- Frequency Slider ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Freq (${band.type})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${band.freq.toInt()} Hz", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        val minLogFreq = kotlin.math.log10(20f)
        val maxLogFreq = kotlin.math.log10(20000f)
        val currentLogFreq = kotlin.math.log10(band.freq.coerceIn(20f, 20000f))
        val freqRatio = (currentLogFreq - minLogFreq) / (maxLogFreq - minLogFreq)
        Slider(
            value = if (freqRatio.isNaN()) 0.5f else freqRatio.coerceIn(0f, 1f),
            onValueChange = {
                val newLog = minLogFreq + it * (maxLogFreq - minLogFreq)
                val newFreq = java.lang.Math.pow(10.0, newLog.toDouble()).toFloat()
                onBandChanged(band.copy(freq = newFreq))
            },
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // --- Gain Slider ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${band.gain.roundToInt()} dB",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    band.gain > 0 -> MaterialTheme.colorScheme.primary
                    band.gain < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Slider(
            value = if (band.gain.isNaN()) 0f else band.gain.coerceIn(-12f, 12f),
            onValueChange = { onBandChanged(band.copy(gain = it)) },
            valueRange = -12f..12f,
            steps = 23,
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
        
        // --- Q-Factor Slider ---
        if (band.q > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Q-Factor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.2f".format(band.q), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = if (band.q.isNaN()) 1f else band.q.coerceIn(0.1f, 10f),
                onValueChange = { onBandChanged(band.copy(q = it)) },
                valueRange = 0.1f..10f,
                modifier = Modifier.fillMaxWidth().height(32.dp)
            )
        }
    }
}

// ─── Utility functions ───

private fun formatFreqLabel(freq: Float): String = "${(freq / 1000).toInt()}k"

private fun parseFreqLabel(label: String): Float =
    label.removeSuffix("k").toFloat() * 1000f

private fun formatSampleRate(rate: Float): String = when (rate.toInt()) {
    44100 -> "44.1k"
    48000 -> "48k"
    96000 -> "96k"
    192000 -> "192k"
    else -> "${(rate / 1000).toInt()}k"
}

private fun parseSampleRate(label: String): Float = when (label) {
    "44.1k" -> 44100f
    "48k" -> 48000f
    "96k" -> 96000f
    "192k" -> 192000f
    else -> 48000f
}
