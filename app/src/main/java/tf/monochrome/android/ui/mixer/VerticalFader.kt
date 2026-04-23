package tf.monochrome.android.ui.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Vertical gain fader with logarithmic-feel scale.
 *
 * Bottom 60 % of travel covers  -60 … 0 dB (unity).
 * Top    40 % of travel covers    0 … +24 dB.
 *
 * Designed to overlay VU meters inside a Box — the background is
 * transparent so the glass surface and meters behind remain visible.
 * Colours come from MaterialTheme so the fader respects the current
 * app theme and liquid-glass look.
 *
 * @param gainDb      current gain in dB (-60 … +24).
 * @param onGainChange callback when the user moves the fader.
 * @param accentColor colour for the active-fill portion below the thumb.
 */
@Composable
fun VerticalFader(
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor  = MaterialTheme.colorScheme.surfaceContainerHigh
    val thumbFill   = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val tickColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val haptic      = LocalHapticFeedback.current
    val prevInDetent = remember { mutableSetOf<Boolean>() }

    Canvas(
        modifier = modifier
            .width(44.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val db = yToDb(change.position.y, size.height.toFloat())
                    onGainChange(db)
                    hapticAtUnity(db, prevInDetent, haptic)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onGainChange(yToDb(offset.y, size.height.toFloat()))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val trackW = 2.dp.toPx()
        val thumbW = 36.dp.toPx()
        val thumbH = 10.dp.toPx()
        val thumbR = 3.dp.toPx()

        // ── Thin fader track ───────────────────────────────────────────
        drawRoundRect(
            color      = trackColor,
            topLeft    = Offset(cx - trackW / 2f, 0f),
            size       = Size(trackW, h),
            cornerRadius = CornerRadius(trackW / 2f)
        )

        // ── Unity mark (0 dB) at 40 % from top ────────────────────────
        val unityY = h * 0.4f
        drawLine(
            color       = accentColor.copy(alpha = 0.5f),
            start       = Offset(cx - 12.dp.toPx(), unityY),
            end         = Offset(cx + 12.dp.toPx(), unityY),
            strokeWidth = 1.dp.toPx()
        )

        // ── dB tick marks (small lines on left edge) ──────────────────
        val ticks = listOf(24f, 18f, 12f, 6f, 0f, -6f, -12f, -18f, -24f, -36f, -48f, -60f)
        for (db in ticks) {
            val y = dbToY(db, h)
            val len = if (db == 0f) 6.dp.toPx() else 3.dp.toPx()
            drawLine(
                color       = tickColor,
                start       = Offset(0f, y),
                end         = Offset(len, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // ── Filled portion below thumb ─────────────────────────────────
        val thumbY = dbToY(gainDb, h)
        drawRoundRect(
            color      = accentColor.copy(alpha = 0.3f),
            topLeft    = Offset(cx - trackW / 2f, thumbY),
            size       = Size(trackW, h - thumbY),
            cornerRadius = CornerRadius(trackW / 2f)
        )

        // ── Thumb bar ──────────────────────────────────────────────────
        val thumbLeft = cx - thumbW / 2f
        val thumbTop  = thumbY - thumbH / 2f

        // Shadow
        drawRoundRect(
            color      = Color.Black.copy(alpha = 0.18f),
            topLeft    = Offset(thumbLeft, thumbTop + 2.dp.toPx()),
            size       = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbR)
        )
        // Fill
        drawRoundRect(
            color      = thumbFill,
            topLeft    = Offset(thumbLeft, thumbTop),
            size       = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbR)
        )
        // Border
        drawRoundRect(
            color      = thumbBorder,
            topLeft    = Offset(thumbLeft, thumbTop),
            size       = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbR),
            style      = Stroke(width = 1.dp.toPx())
        )
        // Grip lines on thumb
        for (i in -1..1) {
            val ly = thumbTop + thumbH / 2f + i * 2.5.dp.toPx()
            drawLine(
                color       = thumbBorder,
                start       = Offset(cx - 8.dp.toPx(), ly),
                end         = Offset(cx + 8.dp.toPx(), ly),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}

// ── Mapping helpers ──────────────────────────────────────────────────────

/** Y pixel → dB.  Top = +24 dB, 40 % = 0 dB, bottom = -60 dB. */
private fun yToDb(y: Float, height: Float): Float {
    val fraction = (y / height).coerceIn(0f, 1f)
    return if (fraction <= 0.4f) {
        24f * (1f - fraction / 0.4f)
    } else {
        -60f * ((fraction - 0.4f) / 0.6f)
    }
}

/** dB → Y pixel. */
private fun dbToY(db: Float, height: Float): Float {
    val clamped = db.coerceIn(-60f, 24f)
    return if (clamped >= 0f) {
        height * 0.4f * (1f - clamped / 24f)
    } else {
        height * (0.4f + 0.6f * (-clamped / 60f))
    }
}

private fun hapticAtUnity(
    db: Float,
    prev: MutableSet<Boolean>,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    if (db.absoluteValue < 1f) {
        if (prev.isEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            prev.add(true)
        }
    } else {
        prev.clear()
    }
}
