package tf.monochrome.android.ui.mixer.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tf.monochrome.android.audio.dsp.model.BusConfig
import tf.monochrome.android.audio.dsp.model.MixPreset
import tf.monochrome.android.ui.mixer.PresetBar
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Macro overview — clean, simplified view of the main mix buses.
 * This is page 0 of the inner HorizontalPager (swipe right for DSP canvas).
 *
 * Shows: header with ON/OFF toggle, preset bar, compact bus cards in a row.
 */
@Composable
fun MacroView(
    enabled: Boolean,
    buses: List<BusConfig>,
    selectedBusIndex: Int,
    presets: List<MixPreset>,
    currentPresetName: String?,
    onEnabledChange: (Boolean) -> Unit,
    onBusSelect: (Int) -> Unit,
    onBusToggleMute: (Int) -> Unit,
    onBusToggleSolo: (Int) -> Unit,
    onPresetSave: (String) -> Unit,
    onPresetLoad: (MixPreset) -> Unit,
    onPresetDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = statusBarPadding)
    ) {
        // ── Header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonoDimens.spacingLg, vertical = MonoDimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mix",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MonoDimens.spacingSm)
            ) {
                Text(
                    text = if (enabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // ── Preset bar ─────────────────────────────────────────────────
        PresetBar(
            currentPresetName = currentPresetName,
            presets = presets,
            onSave = onPresetSave,
            onLoad = onPresetLoad,
            onDelete = onPresetDelete
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = MonoDimens.spacingLg),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        // ── Hint ───────────────────────────────────────────────────────
        Text(
            text = "Swipe right for DSP Canvas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(
                horizontal = MonoDimens.spacingLg,
                vertical = MonoDimens.spacingXs
            )
        )

        // ── Bus cards ──────────────────────────────────────────────────
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = MonoDimens.spacingMd,
                end = MonoDimens.spacingMd,
                top = MonoDimens.spacingSm,
                bottom = MonoDimens.spacingSm
            ),
            horizontalArrangement = Arrangement.spacedBy(MonoDimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(buses) { index, bus ->
                MacroBusCard(
                    bus = bus,
                    isSelected = index == selectedBusIndex,
                    onSelect = { onBusSelect(index) },
                    onToggleMute = { onBusToggleMute(index) },
                    onToggleSolo = { onBusToggleSolo(index) }
                )
            }
        }
    }
}
