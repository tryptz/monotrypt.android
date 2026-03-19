package tf.monochrome.android.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import android.content.Context
import androidx.compose.ui.text.input.PasswordVisualTransformation
import tf.monochrome.android.domain.model.NowPlayingViewMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.ui.eq.EqViewModel
import tf.monochrome.android.domain.model.EqPreset

private val settingsTabs = listOf("Appearance", "Interface", "Scrobbling", "Audio", "Equalizer", "AI", "Downloads", "Instances", "System")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            settingsTabs.forEachIndexed { index, tab ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(tab, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        when (selectedTab) {
            0 -> AppearanceTab(viewModel)
            1 -> InterfaceTab(viewModel)
            2 -> ScrobblingTab(viewModel)
            3 -> AudioTab(viewModel)
            4 -> EqualizerTab(navController)
            5 -> AiTab(viewModel)
            6 -> DownloadsTab(viewModel)
            7 -> InstancesTab(viewModel)
            8 -> SystemTab(viewModel)
        }
    }
}

// ─── Tab 4: Equalizer ──────────────────────────────────────────────────
@Composable
private fun EqualizerTab(navController: NavController, eqViewModel: EqViewModel = hiltViewModel()) {
    val eqEnabled by eqViewModel.eqEnabled.collectAsState()
    val selectedTarget by eqViewModel.selectedTarget.collectAsState()
    val selectedHeadphone by eqViewModel.selectedHeadphone.collectAsState()

    SettingsTabContent {
        SettingsGroupHeader("Equalizer")
        SettingSwitchItem(
            title = "Enable Equalizer",
            subtitle = "Apply EQ processing to playback",
            checked = eqEnabled,
            onCheckedChange = { eqViewModel.toggleEq() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingItem(
            title = "Target Curve",
            subtitle = selectedTarget.label,
            onClick = {}
        )

        SettingItem(
            title = "Headphone",
            subtitle = selectedHeadphone?.name ?: "None selected",
            onClick = {}
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.navigate("equalizer") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Precision AutoEQ")
        }
    }
}

// ─── Tab 1: Appearance ─────────────────────────────────────────────────
@Composable
private fun AppearanceTab(viewModel: SettingsViewModel) {
    val themeName by viewModel.theme.collectAsState()
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showFontDropdown by remember { mutableStateOf(false) }

    SettingsTabContent {
        SettingsGroupHeader("Theme")
        SettingItem(title = "Color Theme", subtitle = themeName, onClick = { showThemeDropdown = true })
        DropdownMenu(expanded = showThemeDropdown, onDismissRequest = { showThemeDropdown = false }) {
            listOf("monochrome_dark", "ocean", "midnight", "crimson", "forest").forEach { t ->
                DropdownMenuItem(text = { Text(t) }, onClick = { viewModel.setTheme(t); showThemeDropdown = false })
            }
        }
        SettingSwitchItem(
            title = "Dynamic Colors",
            subtitle = "Extract accent colors from album art",
            checked = dynamicColors,
            onCheckedChange = { viewModel.setDynamicColors(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Typography")
        SettingItem(title = "Font Size", subtitle = fontSize.replaceFirstChar { it.uppercase() }, onClick = { showFontDropdown = true })
        DropdownMenu(expanded = showFontDropdown, onDismissRequest = { showFontDropdown = false }) {
            listOf("small", "medium", "large").forEach { s ->
                DropdownMenuItem(text = { Text(s.replaceFirstChar { it.uppercase() }) }, onClick = { viewModel.setFontSize(s); showFontDropdown = false })
            }
        }
    }
}

// ─── Tab 2: Interface ──────────────────────────────────────────────────
@Composable
private fun InterfaceTab(viewModel: SettingsViewModel) {
    val gapless by viewModel.gaplessPlayback.collectAsState()
    val explicit by viewModel.showExplicitBadges.collectAsState()
    val confirmQueue by viewModel.confirmClearQueue.collectAsState()

    SettingsTabContent {
        SettingsGroupHeader("Playback")
        SettingSwitchItem(
            title = "Gapless Playback",
            subtitle = "Remove silence between tracks",
            checked = gapless,
            onCheckedChange = { viewModel.setGaplessPlayback(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Display")
        SettingSwitchItem(
            title = "Show Explicit Badges",
            subtitle = "Display 'E' badge on explicit tracks",
            checked = explicit,
            onCheckedChange = { viewModel.setShowExplicitBadges(it) }
        )
        val romaji by viewModel.romajiLyrics.collectAsState()
        SettingSwitchItem(
            title = "Romaji Lyrics",
            subtitle = "Transliterate Japanese lyrics to Latin characters",
            checked = romaji,
            onCheckedChange = { viewModel.setRomajiLyrics(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Now Playing")
        val viewMode by viewModel.nowPlayingViewMode.collectAsState()
        var showModeDropdown by remember { mutableStateOf(false) }
        SettingItem(
            title = "View Mode", 
            subtitle = "Action when clicking album art: ${viewMode.displayName}", 
            onClick = { showModeDropdown = true }
        )
        DropdownMenu(expanded = showModeDropdown, onDismissRequest = { showModeDropdown = false }) {
            NowPlayingViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) }, 
                    onClick = { viewModel.setNowPlayingViewMode(mode); showModeDropdown = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Audio Visualizer")
        val sensitivity by viewModel.visualizerSensitivity.collectAsState()
        val brightness by viewModel.visualizerBrightness.collectAsState()
        
        Text("Sensitivity: $sensitivity%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("Controls intensity (High = Epilepsy Warning)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = sensitivity.toFloat(),
            onValueChange = { viewModel.setVisualizerSensitivity(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Brightness: $brightness%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = brightness.toFloat(),
            onValueChange = { viewModel.setVisualizerBrightness(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Queue")
        SettingSwitchItem(
            title = "Confirm Before Clearing Queue",
            subtitle = "Ask before replacing the current queue",
            checked = confirmQueue,
            onCheckedChange = { viewModel.setConfirmClearQueue(it) }
        )
    }
}

// ─── Tab 3: Scrobbling ─────────────────────────────────────────────────
@Composable
private fun ScrobblingTab(viewModel: SettingsViewModel) {
    val lastFmEnabled by viewModel.lastFmEnabled.collectAsState()
    val lastFmUsername by viewModel.lastFmUsername.collectAsState()
    val lbEnabled by viewModel.listenBrainzEnabled.collectAsState()
    val lbToken by viewModel.listenBrainzToken.collectAsState()

    var showLastFmDialog by remember { mutableStateOf(false) }
    var showLbDialog by remember { mutableStateOf(false) }

    if (showLastFmDialog) {
        var sessionInput by remember { mutableStateOf("") }
        var usernameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLastFmDialog = false },
            title = { Text("Last.fm") },
            text = {
                Column {
                    OutlinedTextField(
                        value = usernameInput, onValueChange = { usernameInput = it },
                        label = { Text("Username") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sessionInput, onValueChange = { sessionInput = it },
                        label = { Text("Session Key") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sessionInput.isNotBlank() && usernameInput.isNotBlank()) {
                        viewModel.setLastFmSession(sessionInput, usernameInput)
                    }
                    showLastFmDialog = false
                }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showLastFmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLbDialog) {
        var tokenInput by remember { mutableStateOf(lbToken ?: "") }
        AlertDialog(
            onDismissRequest = { showLbDialog = false },
            title = { Text("ListenBrainz Token") },
            text = {
                OutlinedTextField(
                    value = tokenInput, onValueChange = { tokenInput = it },
                    label = { Text("User Token") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tokenInput.isNotBlank()) viewModel.setListenBrainzToken(tokenInput)
                    else viewModel.clearListenBrainzToken()
                    showLbDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLbDialog = false }) { Text("Cancel") }
            }
        )
    }

    SettingsTabContent {
        SettingsGroupHeader("Last.fm")
        SettingItem(
            title = "Last.fm",
            subtitle = if (lastFmEnabled) "Connected as ${lastFmUsername ?: "user"}" else "Not connected",
            onClick = { showLastFmDialog = true }
        )
        if (lastFmEnabled) {
            TextButton(onClick = { viewModel.clearLastFmSession() }) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("ListenBrainz")
        SettingItem(
            title = "ListenBrainz",
            subtitle = if (lbEnabled) "Connected" else "Not connected",
            onClick = { showLbDialog = true }
        )
        if (lbEnabled) {
            TextButton(onClick = { viewModel.clearListenBrainzToken() }) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─── Tab 4: Audio ──────────────────────────────────────────────────────
@Composable
private fun AudioTab(viewModel: SettingsViewModel) {
    val wifiQuality by viewModel.wifiQuality.collectAsState()
    val cellularQuality by viewModel.cellularQuality.collectAsState()
    val normalization by viewModel.normalizationEnabled.collectAsState()
    val crossfade by viewModel.crossfadeDuration.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val preservePitch by viewModel.preservePitch.collectAsState()
    var showWifiDropdown by remember { mutableStateOf(false) }
    var showCellularDropdown by remember { mutableStateOf(false) }
    var speedText by remember(playbackSpeed) { mutableStateOf(String.format("%.2f", playbackSpeed)) }

    SettingsTabContent {
        SettingsGroupHeader("Streaming Quality")
        SettingItem(title = "Wi-Fi Streaming", subtitle = wifiQuality.displayName, onClick = { showWifiDropdown = true })
        DropdownMenu(expanded = showWifiDropdown, onDismissRequest = { showWifiDropdown = false }) {
            AudioQuality.entries.forEach { q ->
                DropdownMenuItem(text = { Text(q.displayName) }, onClick = { viewModel.setWifiQuality(q); showWifiDropdown = false })
            }
        }

        SettingItem(title = "Cellular Streaming", subtitle = cellularQuality.displayName, onClick = { showCellularDropdown = true })
        DropdownMenu(expanded = showCellularDropdown, onDismissRequest = { showCellularDropdown = false }) {
            AudioQuality.entries.forEach { q ->
                DropdownMenuItem(text = { Text(q.displayName) }, onClick = { viewModel.setCellularQuality(q); showCellularDropdown = false })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Audio Processing")
        SettingSwitchItem(
            title = "Volume Normalization",
            subtitle = "Use ReplayGain to normalize loudness",
            checked = normalization,
            onCheckedChange = { viewModel.setNormalizationEnabled(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Crossfade: ${crossfade}s", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("Blend between tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = crossfade.toFloat(),
            onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
            valueRange = 0f..12f,
            steps = 11,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Playback Speed")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = playbackSpeed,
                onValueChange = { newSpeed ->
                    val rounded = (Math.round(newSpeed * 100) / 100f)
                    speedText = String.format("%.2f", rounded)
                    viewModel.setPlaybackSpeed(rounded)
                },
                valueRange = 0.25f..3.0f,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = speedText,
                onValueChange = { input ->
                    speedText = input
                    input.toFloatOrNull()?.let { parsed ->
                        val clamped = parsed.coerceIn(0.01f, 100f)
                        viewModel.setPlaybackSpeed(clamped)
                    }
                },
                modifier = Modifier.width(80.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Text("x", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = {
                viewModel.setPlaybackSpeed(1.0f)
                speedText = "1.00"
            }) {
                Text("Reset")
            }
        }

        SettingSwitchItem(
            title = "Preserve Pitch",
            subtitle = "Keep original pitch when changing speed",
            checked = preservePitch,
            onCheckedChange = { viewModel.setPreservePitch(it) }
        )
    }
}

// ─── Tab 5: AI ────────────────────────────────────────────────────────
@Composable
private fun AiTab(viewModel: SettingsViewModel) {
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val aiRadioEnabled by viewModel.aiRadioEnabled.collectAsState()
    var keyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey ?: "") }

    SettingsTabContent {
        SettingsGroupHeader("Gemini AI")
        Text(
            "Use Google Gemini to analyze audio and generate intelligent music recommendations based on tempo, genre, year, and samples.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SettingSwitchItem(
            title = "AI Radio",
            subtitle = "Enable AI-powered recommendations on the Home screen",
            checked = aiRadioEnabled,
            onCheckedChange = { viewModel.setAiRadioEnabled(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("API Key")
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.setGeminiApiKey(keyInput.ifBlank { null }) }
            ) {
                Text("Save Key")
            }
            if (geminiApiKey != null) {
                OutlinedButton(onClick = {
                    keyInput = ""
                    viewModel.setGeminiApiKey(null)
                }) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("How It Works")
        Text(
            "When AI Radio is enabled, Monochrome sends a short audio snippet of the seed track to Google Gemini for analysis. " +
                "You can select filter chips (Tempo, Genre, Year, Sample, All) on the Home screen to control what aspects the AI focuses on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Tab 6: Downloads ──────────────────────────────────────────────────
@Composable
private fun DownloadsTab(viewModel: SettingsViewModel) {
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val downloadFolder by viewModel.downloadFolderUri.collectAsState()
    var showQualityDropdown by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission across reboots
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setDownloadFolderUri(uri.toString())
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Downloads") },
            text = { Text("This will delete all downloaded tracks from your device. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val downloadDir = java.io.File(context.getExternalFilesDir(null), "downloads")
                    downloadDir.deleteRecursively()
                    showClearDialog = false
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    SettingsTabContent {
        SettingsGroupHeader("Download Quality")
        SettingItem(title = "Quality", subtitle = downloadQuality.displayName, onClick = { showQualityDropdown = true })
        DropdownMenu(expanded = showQualityDropdown, onDismissRequest = { showQualityDropdown = false }) {
            AudioQuality.entries.forEach { q ->
                DropdownMenuItem(text = { Text(q.displayName) }, onClick = { viewModel.setDownloadQuality(q); showQualityDropdown = false })
            }
        }

        val dlLyrics by viewModel.downloadLyrics.collectAsState()
        SettingSwitchItem(
            title = "Download Lyrics",
            subtitle = "Bundle .lrc files with downloaded tracks",
            checked = dlLyrics,
            onCheckedChange = { viewModel.setDownloadLyrics(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Download Folder")

        val folderDisplay = if (downloadFolder != null) {
            try {
                val uri = Uri.parse(downloadFolder)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                docFile?.name ?: "Custom folder"
            } catch (_: Exception) { "Custom folder" }
        } else {
            "Internal app storage (default)"
        }

        SettingItem(
            title = "Save location",
            subtitle = folderDisplay,
            onClick = { folderPickerLauncher.launch(null) }
        )

        if (downloadFolder != null) {
            TextButton(onClick = { viewModel.setDownloadFolderUri(null) }) {
                Text("Reset to default", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Storage")
        OutlinedButton(
            onClick = { showClearDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear All Downloads")
        }
    }
}

// ─── Tab 6: Instances ──────────────────────────────────────────────────
@Composable
private fun InstancesTab(viewModel: SettingsViewModel) {
    val apiInstances by viewModel.apiInstances.collectAsState()
    val streamingInstances by viewModel.streamingInstances.collectAsState()
    val customEndpoint by viewModel.customEndpoint.collectAsState()
    val refreshing by viewModel.instancesRefreshing.collectAsState()
    var customInput by remember(customEndpoint) { mutableStateOf(customEndpoint ?: "") }

    SettingsTabContent {
        SettingsGroupHeader("Custom Endpoint")
        OutlinedTextField(
            value = customInput,
            onValueChange = { customInput = it },
            label = { Text("Custom API URL (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                viewModel.setCustomEndpoint(customInput.ifBlank { null })
            }) { Text("Apply") }
            if (customEndpoint != null) {
                OutlinedButton(onClick = {
                    customInput = ""
                    viewModel.setCustomEndpoint(null)
                }) { Text("Clear") }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsGroupHeader("API Instances")
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refreshInstances() }) {
                if (refreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (apiInstances.isEmpty()) {
            Text("No API instances loaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            apiInstances.forEach { instance ->
                InstanceCard(url = instance.url, version = instance.version)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsGroupHeader("Streaming Instances")
        if (streamingInstances.isEmpty()) {
            Text("No streaming instances loaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            streamingInstances.forEach { instance ->
                InstanceCard(url = instance.url, version = instance.version)
            }
        }
    }
}

@Composable
private fun InstanceCard(url: String, version: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (version != null) {
                    Text("v$version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.Check, contentDescription = "Online", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Tab 7: System ─────────────────────────────────────────────────────
@Composable
private fun SystemTab(viewModel: SettingsViewModel) {
    val cacheSize by viewModel.cacheSize.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Reset All Data") },
            text = { Text("This will clear all settings, cache, and local data. The app will restart in a clean state.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearAllDialog = false
                }) { Text("Reset Everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    SettingsTabContent {
        SettingsGroupHeader("Storage")
        SettingItem(title = "Cache Size", subtitle = cacheSize, onClick = {})
        OutlinedButton(onClick = { viewModel.clearCache() }) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Cache")
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsGroupHeader("Data")
        OutlinedButton(
            onClick = { showClearAllDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Data")
        }

        Spacer(modifier = Modifier.height(20.dp))
        LinkItem("Website", "https://monochrome.tf", context)

        Spacer(modifier = Modifier.height(20.dp))
        SettingsGroupHeader("Account & Sync")
        val isLoggedIn by viewModel.isLoggedIn.collectAsState()
        val userEmail by viewModel.userEmail.collectAsState()

        if (isLoggedIn) {
            SettingItem(
                title = "Signed in as",
                subtitle = userEmail ?: "Unknown",
                onClick = {}
            )
            OutlinedButton(onClick = { viewModel.logout() }) {
                Text("Sign Out")
            }
        } else {
            Text(
                "Sign in to sync favorites and playlists across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Use the Account page to sign in with Google or email.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsGroupHeader("Playlist Import")
        var importUrl by remember { mutableStateOf("") }
        androidx.compose.material3.OutlinedTextField(
            value = importUrl,
            onValueChange = { importUrl = it },
            label = { Text("Spotify / YT Music URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.importPlaylist(importUrl) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    if (success) importUrl = ""
                }
            },
            enabled = importUrl.isNotBlank()
        ) {
            Text("Import Playlist")
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsGroupHeader("Backup & Restore")
        Text(
            "Export or import your library and history as a JSON file",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                viewModel.exportLibrary { json ->
                    // Copy to clipboard or share
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Monochrome Backup", json)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Backup copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Export JSON")
            }
            OutlinedButton(onClick = {
                // For simplicity, we'll try to get from clipboard
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val json = clip.getItemAt(0).text.toString()
                    viewModel.importLibrary(json)
                    android.widget.Toast.makeText(context, "Library imported", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Clipboard empty", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Import JSON")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SettingsGroupHeader("About")
        Text(
            text = "Monochrome for Android v1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Open-source, ad-free music streaming",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkItem(label: String, url: String, context: android.content.Context) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = 10.dp)
    )
}

// ─── Shared components ─────────────────────────────────────────────────
@Composable
private fun SettingsTabContent(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item { content() }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
fun SettingItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingSwitchItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

