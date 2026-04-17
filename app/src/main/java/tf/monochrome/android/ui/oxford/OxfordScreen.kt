// SPDX-License-Identifier: GPL-3.0-or-later
// Compose UI for Oxford-style Inflator + Compressor tabs.

package tf.monochrome.android.ui.oxford

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import tf.monochrome.android.audio.dsp.oxford.InflatorEffect
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------
// Palette
// ----------------------------------------------------------------------------

private object Ox {
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
    color = Ox.ReadoutText,
)

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
                color = Ox.TrackBg,
                topLeft = Offset(trackX - trackWidth.toPx() / 2f, trackTop),
                size = Size(trackWidth.toPx(), trackHeight),
                cornerRadius = CornerRadius(3f, 3f),
            )
            drawRoundRect(
                color = Ox.TrackInner,
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
                    0f to Ox.ChromeLight,
                    0.5f to Ox.ChromeMid,
                    1f to Ox.ChromeDark,
                ),
                topLeft = Offset(trackX - handleW / 2f, handleY - handleH / 2f),
                size = Size(handleW, handleH),
                cornerRadius = CornerRadius(4f, 4f),
            )
            val gripCount = 5
            for (i in 0 until gripCount) {
                val gy = handleY - (handleH * 0.3f) + i * (handleH * 0.6f / (gripCount - 1))
                drawLine(
                    color = Ox.ChromeDark,
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

    val dbL = if (peakL > 1e-6f) 20f * log10(peakL) else minDb
    val dbR = if (peakR > 1e-6f) 20f * log10(peakR) else minDb

    LaunchedEffect(dbL, dbR) {
        heldL = max(dbL, heldL - 1.2f).coerceIn(minDb, maxDb + 3f)
        heldR = max(dbR, heldR - 1.2f).coerceIn(minDb, maxDb + 3f)
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
                    else   -> Ox.LedOff
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
        t >= 0.90f -> Ox.LedRed
        t >= 0.75f -> Ox.LedYellow
        else       -> Ox.LedGreen
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
    val bg = if (active) activeColor else Ox.ChromeMid
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
                    color = if (active) Ox.TextDark else Color(0xFF3A4050),
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
                color = Ox.TitleWhite,
                fontFamily = FontFamily.SansSerif,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .background(Ox.Readout, RoundedCornerShape(3.dp))
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    0f to Ox.PanelTop,
                    1f to Ox.PanelBottom,
                )
            )
            .border(1.dp, Ox.PanelShadow, RoundedCornerShape(10.dp))
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
                    text = "Sonnox",
                    style = TextStyle(color = Ox.TitleWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = title,
                    style = TextStyle(
                        color = Ox.TitleWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Oxford Plugins",
                    style = TextStyle(color = Ox.TitleWhite.copy(alpha = 0.8f), fontSize = 10.sp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
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

    OxfordPanel(title = "OXFORD INFLATOR", modifier = modifier.fillMaxSize()) {
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
                        activeColor = Ox.BtnClip,
                        onClick = { effect.setClipZeroDb(!state.clipZeroDb) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OxfordButton(
                        label = "BAND\nSPLIT",
                        active = state.bandSplit,
                        activeColor = Ox.BtnBandSplit,
                        onClick = { effect.setBandSplit(!state.bandSplit) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OxfordButton(
                        label = "EFFECT\nIN",
                        active = state.effectIn,
                        activeColor = Ox.BtnEffectOn,
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

    OxfordPanel(title = "COMPRESSOR", modifier = modifier.fillMaxSize()) {
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
                    style = TextStyle(color = Ox.TitleWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(Ox.Readout, RoundedCornerShape(3.dp))
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
                    activeColor = Ox.BtnEffectOn,
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

    // ~30 Hz meter polling
    LaunchedEffect(inflator, compressor) {
        while (isActive) {
            inflator.pollMeters()
            compressor.pollMeters()
            delay(33L)
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
