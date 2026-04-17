// SPDX-License-Identifier: GPL-3.0-or-later
// Compose UI for Seap Inflator + Seap Compressor tabs.

package tf.monochrome.android.ui.oxford

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tf.monochrome.android.audio.dsp.oxford.CompressorEffect
import tf.monochrome.android.audio.dsp.oxford.CompressorPreset
import tf.monochrome.android.audio.dsp.oxford.CompressorState
import tf.monochrome.android.audio.dsp.oxford.InflatorEffect
import tf.monochrome.android.audio.dsp.oxford.InflatorPreset
import tf.monochrome.android.audio.dsp.oxford.InflatorState
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------
// Palette
// ----------------------------------------------------------------------------

private object Seap {
    val PanelTop     = Color(0xFF8FA1C7)
    val PanelBottom  = Color(0xFF6A81AD)
    val PanelShadow  = Color(0xFF40547A)
    val ChromeLight  = Color(0xFFE4E8EE)
    val ChromeMid    = Color(0xFFB5BCC7)
    val ChromeDark   = Color(0xFF7A828F)
    val Readout      = Color(0xFF1E2233)
    val ReadoutText  = Color(0xFFE8B84A)
    val LedGreen     = Color(0xFF3EE26A)
    val LedYellow    = Color(0xFFF5D84A)
    val LedRed       = Color(0xFFE84A3A)
    val LedOff       = Color(0xFF2A3A55)
    val TrackBg      = Color(0xFF394C74)
    val TrackInner   = Color(0xFF5A6E94)
    val BtnClip      = Color(0xFFD94234)
    val BtnBandSplit = Color(0xFFA8AFB8)
    val BtnEffectOn  = Color(0xFF5FD06E)
    val TextDark     = Color(0xFF1A1F2E)
    val TitleWhite   = Color(0xFFF5F7FA)
}

private val digitStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    color = Seap.ReadoutText,
)

/** LED peak-hold decay rate in dB/sec (rate-independent). */
private const val DECAY_DB_PER_SEC = 36f

// ----------------------------------------------------------------------------
// Vertical fader — silver cap on a dark track, drag to set value.
// ----------------------------------------------------------------------------

@Composable
private fun OxfordFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 10.dp,
) {
    var dragOriginValue by remember { mutableStateOf(value) }
    var dragAccumPx by remember { mutableStateOf(0f) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange) {
                    detectDragGestures(
                        onDragStart = {
                            dragOriginValue = value
                            dragAccumPx = 0f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            dragAccumPx += drag.y
                            val h = size.height.toFloat()
                            if (h > 0f) {
                                val span = valueRange.endInclusive - valueRange.start
                                val delta = -(dragAccumPx / h) * span
                                val next = (dragOriginValue + delta)
                                    .coerceIn(valueRange.start, valueRange.endInclusive)
                                onValueChange(next)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val trackX = w * 0.5f
            val trackTop = 6f
            val trackBottom = h - 6f
            val trackHeight = trackBottom - trackTop

            drawRoundRect(
                color = Seap.TrackBg,
                topLeft = Offset(trackX - trackWidth.toPx() / 2f, trackTop),
                size = Size(trackWidth.toPx(), trackHeight),
                cornerRadius = CornerRadius(3f, 3f),
            )
            drawRoundRect(
                color = Seap.TrackInner,
                topLeft = Offset(trackX - trackWidth.toPx() / 2f + 2f, trackTop + 2f),
                size = Size(trackWidth.toPx() - 4f, trackHeight - 4f),
                cornerRadius = CornerRadius(2f, 2f),
            )

            val t = ((value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val handleY = trackBottom - t * trackHeight
            val handleW = w * 0.85f
            val handleH = 28f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Seap.ChromeLight,
                    0.5f to Seap.ChromeMid,
                    1f to Seap.ChromeDark,
                ),
                topLeft = Offset(trackX - handleW / 2f, handleY - handleH / 2f),
                size = Size(handleW, handleH),
                cornerRadius = CornerRadius(4f, 4f),
            )
            val gripCount = 5
            for (i in 0 until gripCount) {
                val gy = handleY - (handleH * 0.3f) + i * (handleH * 0.6f / (gripCount - 1))
                drawLine(
                    color = Seap.ChromeDark,
                    start = Offset(trackX - handleW * 0.35f, gy),
                    end   = Offset(trackX + handleW * 0.35f, gy),
                    strokeWidth = 1.2f,
                )
            }
            drawLine(
                color = Color.Black.copy(alpha = 0.25f),
                start = Offset(trackX - handleW / 2f + 2f, handleY),
                end   = Offset(trackX + handleW / 2f - 2f, handleY),
                strokeWidth = 1f,
            )
        }
    }
}

// ----------------------------------------------------------------------------
// LED meter — vertical, segmented, green/yellow/red. dB-scaled.
// ----------------------------------------------------------------------------

@Composable
private fun LedMeter(
    peakL: Float,
    peakR: Float,
    modifier: Modifier = Modifier,
    minDb: Float = -42f,
    maxDb: Float = 0f,
    segmentCount: Int = 24,
    stereo: Boolean = true,
) {
    var heldL by remember { mutableStateOf(minDb) }
    var heldR by remember { mutableStateOf(minDb) }
    var lastTick by remember { mutableStateOf(0L) }

    val dbL = if (peakL > 1e-6f) 20f * log10(peakL) else minDb
    val dbR = if (peakR > 1e-6f) 20f * log10(peakR) else minDb

    // Decay peak hold at a fixed rate (dB/sec) independent of poll rate so
    // bumping the meter FPS doesn't also speed up how fast the hold falls.
    LaunchedEffect(dbL, dbR) {
        val now = System.nanoTime()
        val dtSec = if (lastTick == 0L) 0f else (now - lastTick) / 1_000_000_000f
        lastTick = now
        val decayDb = DECAY_DB_PER_SEC * dtSec
        heldL = max(dbL, heldL - decayDb).coerceIn(minDb, maxDb + 3f)
        heldR = max(dbR, heldR - decayDb).coerceIn(minDb, maxDb + 3f)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val laneCount = if (stereo) 2 else 1
        val gap = 2f
        val laneW = (w - gap * (laneCount - 1)) / laneCount
        val segGap = 1.2f
        val segH = (h - segGap * (segmentCount - 1)) / segmentCount

        for (lane in 0 until laneCount) {
            val lx = lane * (laneW + gap)
            val db = if (lane == 0) dbL else dbR
            val lit = dbToSegments(db, minDb, maxDb, segmentCount)
            val held = if (lane == 0) heldL else heldR
            val heldSeg = dbToSegments(held, minDb, maxDb, segmentCount) - 1

            for (s in 0 until segmentCount) {
                val segY = h - (s + 1) * segH - s * segGap
                val isLit = s < lit
                val isPeak = s == heldSeg && heldSeg >= 0
                val ledColor = ledColorFor(s, segmentCount)
                val col = when {
                    isLit  -> ledColor
                    isPeak -> ledColor.copy(alpha = 0.7f)
                    else   -> Seap.LedOff
                }
                drawRoundRect(
                    color = col,
                    topLeft = Offset(lx, segY),
                    size = Size(laneW, segH),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            }
        }
    }
}

private fun dbToSegments(db: Float, minDb: Float, maxDb: Float, segmentCount: Int): Int {
    val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
    return (t * segmentCount).roundToInt()
}

private fun ledColorFor(segmentIndex: Int, total: Int): Color {
    val t = segmentIndex.toFloat() / (total - 1).toFloat()
    return when {
        t >= 0.90f -> Seap.LedRed
        t >= 0.75f -> Seap.LedYellow
        else       -> Seap.LedGreen
    }
}

// ----------------------------------------------------------------------------
// Chunky pill buttons
// ----------------------------------------------------------------------------

@Composable
private fun OxfordButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (active) activeColor else Seap.ChromeMid
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        shadowElevation = 2.dp,
        modifier = modifier.height(44.dp).defaultMinSize(minWidth = 68.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = label,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (active) Seap.TextDark else Color(0xFF3A4050),
                    fontFamily = FontFamily.SansSerif,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Fader column
// ----------------------------------------------------------------------------

@Composable
private fun FaderColumn(
    label: String,
    readout: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    peakL: Float,
    peakR: Float,
    modifier: Modifier = Modifier,
    meterStereo: Boolean = true,
    meterMinDb: Float = -42f,
    meterMaxDb: Float = 0f,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Seap.TitleWhite,
                fontFamily = FontFamily.SansSerif,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .background(Seap.Readout, RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(text = readout, style = digitStyle)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OxfordFader(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            LedMeter(
                peakL = peakL,
                peakR = peakR,
                stereo = meterStereo,
                minDb = meterMinDb,
                maxDb = meterMaxDb,
                modifier = Modifier.width(if (meterStereo) 22.dp else 12.dp).fillMaxHeight(),
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Panel chrome
// ----------------------------------------------------------------------------

@Composable
private fun OxfordPanel(
    title: String,
    modifier: Modifier = Modifier,
    onHelpClick: (() -> Unit)? = null,
    presetRail: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    0f to Seap.PanelTop,
                    1f to Seap.PanelBottom,
                )
            )
            .border(1.dp, Seap.PanelShadow, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2D3A5C))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "SEAP",
                    style = TextStyle(
                        color = Seap.TitleWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.2.sp,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = title,
                    style = TextStyle(
                        color = Seap.TitleWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Seap Effects",
                    style = TextStyle(color = Seap.TitleWhite.copy(alpha = 0.8f), fontSize = 10.sp),
                )
                if (onHelpClick != null) {
                    Spacer(Modifier.width(4.dp))
                    HelpChip(onClick = onHelpClick)
                }
            }
            if (presetRail != null) {
                Spacer(Modifier.height(8.dp))
                presetRail()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ----------------------------------------------------------------------------
// ? help chip + preset rail
// ----------------------------------------------------------------------------

@Composable
private fun HelpChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Seap.ChromeLight,
        shadowElevation = 1.dp,
        modifier = Modifier.size(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "?",
                style = TextStyle(
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Seap.TextDark,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
    }
}

@Composable
private fun PresetRail(
    items: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { index, (label, active) ->
            PresetChip(label = label, active = active, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun PresetChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Seap.BtnEffectOn else Seap.ChromeMid
    val fg = if (active) Seap.TextDark else Color(0xFF2A3045)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        shadowElevation = if (active) 2.dp else 1.dp,
        modifier = Modifier.height(28.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = fg,
                    letterSpacing = 0.6.sp,
                ),
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Tutorial bottom sheet
// ----------------------------------------------------------------------------

data class TutorialSection(val heading: String, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TutorialSheet(
    title: String,
    subtitle: String,
    sections: List<TutorialSection>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(18.dp))
            sections.forEachIndexed { i, section ->
                Text(
                    text = section.heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = section.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (i != sections.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Inflator screen
// ----------------------------------------------------------------------------

@Composable
fun InflatorScreen(
    effect: InflatorEffect,
    modifier: Modifier = Modifier,
) {
    val state by effect.state.collectAsState()
    val peak  by effect.peak.collectAsState()
    var showHelp by rememberSaveable { mutableStateOf(false) }

    if (showHelp) {
        TutorialSheet(
            title = "Seap Inflator",
            subtitle = "Psycho-acoustic enhancer — makes tracks feel louder and richer without raising true peak level.",
            sections = inflatorTutorialSections(),
            onDismiss = { showHelp = false },
        )
    }

    OxfordPanel(
        title = "SEAP INFLATOR",
        modifier = modifier.fillMaxSize(),
        onHelpClick = { showHelp = true },
        presetRail = {
            val entries = InflatorPreset.entries
            val active = entries.indexOfFirst { matchesInflator(it.state, state) }
            PresetRail(
                items = entries.map { it.label to (entries.indexOf(it) == active) },
                onSelect = { effect.applyPreset(entries[it]) },
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FaderColumn(
                label = "INPUT",
                readout = formatDb(state.inputDb),
                value = state.inputDb,
                valueRange = -6f..12f,
                onValueChange = effect::setInputDb,
                peakL = peak.left, peakR = peak.right,
                modifier = Modifier.weight(1f),
                meterStereo = true,
                meterMinDb = -42f, meterMaxDb = 0f,
            )

            Column(
                modifier = Modifier.weight(1.1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FaderColumn(
                        label = "EFFECT",
                        readout = "${state.effectPct.roundToInt()} %",
                        value = state.effectPct,
                        valueRange = 0f..100f,
                        onValueChange = effect::setEffectPct,
                        peakL = peak.left, peakR = peak.right,
                        meterStereo = true,
                        meterMinDb = -42f, meterMaxDb = 0f,
                        modifier = Modifier.weight(1f),
                    )
                    FaderColumn(
                        label = "CURVE",
                        readout = formatCurve(state.curve),
                        value = state.curve,
                        valueRange = -50f..50f,
                        onValueChange = effect::setCurve,
                        peakL = 0f, peakR = 0f,
                        meterStereo = false,
                        meterMinDb = -50f, meterMaxDb = 50f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OxfordButton(
                        label = "CLIP\n0 dB",
                        active = state.clipZeroDb,
                        activeColor = Seap.BtnClip,
                        onClick = { effect.setClipZeroDb(!state.clipZeroDb) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OxfordButton(
                        label = "BAND\nSPLIT",
                        active = state.bandSplit,
                        activeColor = Seap.BtnBandSplit,
                        onClick = { effect.setBandSplit(!state.bandSplit) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OxfordButton(
                        label = "EFFECT\nIN",
                        active = state.effectIn,
                        activeColor = Seap.BtnEffectOn,
                        onClick = { effect.setEffectIn(!state.effectIn) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            FaderColumn(
                label = "OUTPUT",
                readout = formatDb(state.outputDb),
                value = state.outputDb,
                valueRange = -12f..0f,
                onValueChange = effect::setOutputDb,
                peakL = peak.left, peakR = peak.right,
                modifier = Modifier.weight(1f),
                meterStereo = true,
                meterMinDb = -40f, meterMaxDb = 0f,
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Compressor screen
// ----------------------------------------------------------------------------

@Composable
fun CompressorScreen(
    effect: CompressorEffect,
    modifier: Modifier = Modifier,
) {
    val state by effect.state.collectAsState()
    val peak  by effect.peak.collectAsState()
    val grDb  by effect.gainReductionDb.collectAsState()
    var showHelp by rememberSaveable { mutableStateOf(false) }

    if (showHelp) {
        TutorialSheet(
            title = "Seap Compressor",
            subtitle = "Dynamics processor — tames loud peaks and evens out levels.",
            sections = compressorTutorialSections(),
            onDismiss = { showHelp = false },
        )
    }

    OxfordPanel(
        title = "SEAP COMPRESSOR",
        modifier = modifier.fillMaxSize(),
        onHelpClick = { showHelp = true },
        presetRail = {
            val entries = CompressorPreset.entries
            val active = entries.indexOfFirst { matchesCompressor(it.state, state) }
            PresetRail(
                items = entries.map { it.label to (entries.indexOf(it) == active) },
                onSelect = { effect.applyPreset(entries[it]) },
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FaderColumn(
                label = "THRESH",
                readout = formatDb(state.thresholdDb),
                value = state.thresholdDb,
                valueRange = -60f..0f,
                onValueChange = effect::setThresholdDb,
                peakL = peak.left, peakR = peak.right,
                modifier = Modifier.weight(1f),
                meterMinDb = -60f, meterMaxDb = 0f,
            )
            FaderColumn(
                label = "RATIO",
                readout = formatRatio(state.ratio),
                value = state.ratio,
                valueRange = 1f..20f,
                onValueChange = effect::setRatio,
                peakL = 0f, peakR = 0f,
                meterStereo = false,
                modifier = Modifier.weight(0.9f),
            )
            FaderColumn(
                label = "ATTACK",
                readout = formatMs(state.attackMs),
                value = state.attackMs.coerceIn(0.1f, 200f),
                valueRange = 0.1f..200f,
                onValueChange = effect::setAttackMs,
                peakL = 0f, peakR = 0f,
                meterStereo = false,
                modifier = Modifier.weight(0.9f),
            )
            FaderColumn(
                label = "RELEASE",
                readout = formatMs(state.releaseMs),
                value = state.releaseMs,
                valueRange = 5f..2000f,
                onValueChange = effect::setReleaseMs,
                peakL = 0f, peakR = 0f,
                meterStereo = false,
                modifier = Modifier.weight(0.9f),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "GR",
                    style = TextStyle(color = Seap.TitleWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(Seap.Readout, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(text = "-${"%.1f".format(grDb)} dB", style = digitStyle)
                }
                Spacer(Modifier.height(8.dp))
                LedMeter(
                    peakL = grDbToFakePeak(grDb),
                    peakR = grDbToFakePeak(grDb),
                    stereo = false,
                    minDb = -20f,
                    maxDb = 0f,
                    modifier = Modifier.weight(1f).width(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                FaderColumn(
                    label = "MAKEUP",
                    readout = formatDbSigned(state.makeupDb),
                    value = state.makeupDb,
                    valueRange = -12f..24f,
                    onValueChange = effect::setMakeupDb,
                    peakL = 0f, peakR = 0f,
                    meterStereo = false,
                    modifier = Modifier.height(100.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OxfordButton(
                    label = "EFFECT\nIN",
                    active = !state.bypass,
                    activeColor = Seap.BtnEffectOn,
                    onClick = { effect.setBypass(!state.bypass) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun grDbToFakePeak(grDb: Float): Float {
    val displayDb = (-grDb).coerceIn(-20f, 0f)
    return 10f.pow(displayDb / 20f)
}

// ----------------------------------------------------------------------------
// Tab host
// ----------------------------------------------------------------------------

@Composable
fun OxfordEffectsTabs(
    inflator: InflatorEffect,
    compressor: CompressorEffect,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Compressor", "Inflator")

    // 60 Hz meter polling — keeps the LED bars fluid under fast transients.
    LaunchedEffect(inflator, compressor) {
        while (isActive) {
            inflator.pollMeters()
            compressor.pollMeters()
            delay(16L)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selected == i,
                    onClick = { selected = i },
                    text = { Text(title) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (selected) {
            0 -> CompressorScreen(
                effect = compressor,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
            else -> InflatorScreen(
                effect = inflator,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Formatters
// ----------------------------------------------------------------------------

private fun formatDb(db: Float): String {
    val v = if (kotlin.math.abs(db) < 0.05f) 0.0f else db
    return "${"%.1f".format(v)} dB"
}
private fun formatDbSigned(db: Float): String =
    if (db >= 0) "+${"%.1f".format(db)} dB" else "${"%.1f".format(db)} dB"
private fun formatCurve(c: Float): String = "%.1f".format(c)
private fun formatRatio(r: Float): String = "${"%.1f".format(r)}:1"
private fun formatMs(ms: Float): String = when {
    ms < 10f  -> "${"%.2f".format(ms)} ms"
    ms < 100f -> "${"%.1f".format(ms)} ms"
    else      -> "${ms.roundToInt()} ms"
}

// ----------------------------------------------------------------------------
// Preset matching (fuzzy, so tiny float noise from the UI doesn't dehighlight)
// ----------------------------------------------------------------------------

private fun matchesInflator(a: InflatorState, b: InflatorState): Boolean =
    abs(a.inputDb    - b.inputDb)   < 0.05f &&
    abs(a.outputDb   - b.outputDb)  < 0.05f &&
    abs(a.effectPct  - b.effectPct) < 0.5f  &&
    abs(a.curve      - b.curve)     < 0.5f  &&
    a.clipZeroDb == b.clipZeroDb &&
    a.bandSplit  == b.bandSplit  &&
    a.effectIn   == b.effectIn

private fun matchesCompressor(a: CompressorState, b: CompressorState): Boolean =
    abs(a.thresholdDb - b.thresholdDb) < 0.1f  &&
    abs(a.ratio       - b.ratio)       < 0.05f &&
    abs(a.attackMs    - b.attackMs)    < 0.1f  &&
    abs(a.releaseMs   - b.releaseMs)   < 0.5f  &&
    abs(a.kneeDb      - b.kneeDb)      < 0.1f  &&
    abs(a.makeupDb    - b.makeupDb)    < 0.1f  &&
    a.bypass == b.bypass

// ----------------------------------------------------------------------------
// Tutorial copy
// ----------------------------------------------------------------------------

private fun inflatorTutorialSections(): List<TutorialSection> = listOf(
    TutorialSection(
        "What it does",
        "The Inflator shapes the waveform with low-order harmonic distortion driven by the CURVE knob. Unlike a limiter, it doesn't just cap peaks — it adds perceived loudness, density, and warmth or brightness depending on the curve's sign. Keep EFFECT IN lit to hear the processing.",
    ),
    TutorialSection(
        "INPUT / OUTPUT",
        "INPUT drives the signal into the shaping stage (harder drive = more effect). OUTPUT trims the result back to unity. On mixes, nudge INPUT up and OUTPUT down to taste — they're independent, not linked.",
    ),
    TutorialSection(
        "EFFECT",
        "How much of the shaped signal is blended in (0–100 %). Below ~30 % you're in parallel territory; above 60 % the effect becomes obvious. If in doubt, sweep EFFECT while A/B-ing the EFFECT IN button.",
    ),
    TutorialSection(
        "CURVE",
        "Negative values (-50…0) push even-order harmonics — warmer, analogue-ish. Positive values (0…+50) emphasise odd-order — sharper, more present. Zero is neutral and the mildest setting.",
    ),
    TutorialSection(
        "CLIP 0 dB",
        "Hard ceiling at 0 dBFS applied on the way out. Leave on for safety when mastering; turn off if you're feeding another plugin that has its own ceiling management.",
    ),
    TutorialSection(
        "BAND SPLIT",
        "Splits the signal into low / mid / high bands and processes each independently before recombining. Produces tighter results on full mixes and drum buses. More CPU — but worth it on masters.",
    ),
    TutorialSection(
        "Start here",
        "Try the WARMTH preset on a vocal or bass, PUNCH on a drum bus, and LOUD on a finished stereo mix. Once you hear what the curve does, you'll reach for it often.",
    ),
)

private fun compressorTutorialSections(): List<TutorialSection> = listOf(
    TutorialSection(
        "What it does",
        "A compressor reduces gain whenever the signal exceeds a threshold — quieter parts pass through untouched, louder parts get pushed down. Use it to even out performances, add punch, or glue a mix together. EFFECT IN lights up when the compressor is active.",
    ),
    TutorialSection(
        "THRESH (Threshold)",
        "The level above which compression engages. Lower threshold = more of the signal is compressed. Watch the GR meter — for musical results aim for 2–6 dB reduction on peaks.",
    ),
    TutorialSection(
        "RATIO",
        "How aggressively signal above threshold is reduced. 2:1 is gentle, 4:1 is classic, 8:1+ is heavy-handed. Ratios above 10:1 start to behave like a limiter.",
    ),
    TutorialSection(
        "ATTACK",
        "How fast the compressor clamps down once the signal crosses threshold. Fast (0.1–5 ms) catches transients — kills punch if over-used. Slow (20–50 ms) lets transients through for more snap.",
    ),
    TutorialSection(
        "RELEASE",
        "How fast the gain recovers after the signal drops back below threshold. Short releases (10–80 ms) pump and breathe with the music — great for drums. Long releases (200 ms+) are smoother, better for vocals and master buses.",
    ),
    TutorialSection(
        "MAKEUP",
        "Output gain applied after compression so the processed signal matches the bypass level. Bump it up by roughly the amount of reduction shown on the GR meter.",
    ),
    TutorialSection(
        "GR meter",
        "Gain Reduction display — tells you how many dB the compressor is currently pulling down. If it's pinned, your threshold is too low or ratio too high. If it never moves, threshold is too high.",
    ),
    TutorialSection(
        "Start here",
        "VOCAL preset for leads, DRUM BUS to tighten drums, GLUE on your stereo mix bus, LIMITER only on the final master-chain output.",
    ),
)
