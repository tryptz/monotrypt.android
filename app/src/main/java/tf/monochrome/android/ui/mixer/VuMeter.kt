package tf.monochrome.android.ui.mixer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * FL Studio-style segmented vertical VU meter.
 *
 * Draws a column of thin horizontal bar segments that light up from
 * bottom to top based on the current level.  Colours transition from
 * green (low) through yellow (mid-high) to red (clip).
 *
 * Background is semi-transparent so it composites cleanly on top of
 * a liquidGlass surface.
 *
 * @param levelDb current level in dB (typically -60 … +6).
 * @param muted   when true the bar drops to zero and dims.
 */
@Composable
fun VuMeter(
    levelDb: Float,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    // Normalise dB → 0..1 fraction  (-60 dB → 0,  0 dB → 1,  +6 dB → clamp 1)
    val targetFraction = if (muted) 0f
    else ((levelDb + 60f) / 60f).coerceIn(0f, 1f)

    val animatedLevel by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "vuLevel"
    )

    // ── Peak hold — tracks max and decays slowly ───────────────────────
    var peak by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animatedLevel) {
        if (animatedLevel > peak) peak = animatedLevel
    }
    LaunchedEffect(peak) {
        if (peak > 0f) {
            delay(800)
            peak = (peak - 0.04f).coerceAtLeast(0f)
        }
    }

    Canvas(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
    ) {
        val w = size.width
        val h = size.height
        val segH = 2.5.dp.toPx()
        val gapH = 0.8.dp.toPx()
        val stepH = segH + gapH
        val segCount = (h / stepH).toInt()

        // Semi-transparent background (glass-compatible)
        drawRect(color = Color.White.copy(alpha = 0.04f))

        val activeSegs = (segCount * animatedLevel).toInt()
        val peakSeg = (segCount * peak).toInt().coerceIn(0, segCount - 1)

        for (i in 0 until segCount) {
            val segY = h - (i + 1) * stepH
            val fraction = i.toFloat() / segCount

            // Colour depends on position in the meter
            val color = when {
                fraction > 0.88f -> FLColors.meterRed
                fraction > 0.72f -> FLColors.meterYellow
                fraction > 0.35f -> FLColors.meterGreen
                else             -> FLColors.meterGreenDark
            }

            val isActive = i < activeSegs
            val isPeak   = i == peakSeg && peak > 0.01f

            drawRect(
                color = when {
                    isActive || isPeak -> color
                    else               -> color.copy(alpha = 0.06f)
                },
                topLeft = Offset(0f, segY),
                size    = Size(w, segH)
            )
        }
    }
}
