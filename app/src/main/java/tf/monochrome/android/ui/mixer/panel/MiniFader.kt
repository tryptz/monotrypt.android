package tf.monochrome.android.ui.mixer.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Compact horizontal gain fader with logarithmic-feel scale.
 *
 * Left 60% of travel covers -60..0 dB (unity).
 * Right 40% covers 0..+24 dB.
 *
 * Reuses the mapping logic from VerticalFader.kt, transposed to horizontal.
 */
@Composable
fun MiniFader(
    gainDb: Float,                   // -60 .. +24
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val thumbFill = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val haptic = LocalHapticFeedback.current
    val prevInDetent = remember { mutableSetOf<Boolean>() }

    Canvas(
        modifier = modifier
            .width(140.dp)
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val db = xToDb(change.position.x, size.width.toFloat())
                    onGainChange(db)
                    hapticAtUnity(db, prevInDetent, haptic)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val db = xToDb(offset.x, size.width.toFloat())
                    onGainChange(db)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val trackH = 4.dp.toPx()
        val cy = h / 2f
        val thumbW = 12.dp.toPx()
        val thumbH = 22.dp.toPx()

        // Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(w, trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Unity (0 dB) tick — at 60% from left
        val unityX = w * 0.6f
        drawLine(
            color = accentColor.copy(alpha = 0.5f),
            start = Offset(unityX, cy - 8.dp.toPx()),
            end = Offset(unityX, cy + 8.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // Filled portion (from left to thumb)
        val thumbX = dbToX(gainDb, w)
        val fillBrush = Brush.horizontalGradient(
            colors = listOf(accentColor.copy(alpha = 0.3f), accentColor),
            startX = 0f,
            endX = thumbX
        )
        drawRoundRect(
            brush = fillBrush,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(thumbX, trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )

        // Thumb
        val thumbLeft = thumbX - thumbW / 2f
        val thumbTop = cy - thumbH / 2f
        // Shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(thumbLeft + 1.dp.toPx(), thumbTop + 1.dp.toPx()),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        // Fill
        drawRoundRect(
            color = thumbFill,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        // Border
        drawRoundRect(
            color = thumbBorder,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        // Grip lines
        for (i in -1..1) {
            val lx = thumbLeft + thumbW / 2f + i * 2.5.dp.toPx()
            drawLine(
                color = thumbBorder,
                start = Offset(lx, cy - 4.dp.toPx()),
                end = Offset(lx, cy + 4.dp.toPx()),
                strokeWidth = 0.5.dp.toPx()
            )
        }
    }
}

// ── Mapping helpers (horizontal version of VerticalFader) ───────────────

/**
 * Convert an X pixel position to dB.
 * Left edge = -60 dB, 60% = 0 dB (unity), right edge = +24 dB.
 */
private fun xToDb(x: Float, width: Float): Float {
    val fraction = (x / width).coerceIn(0f, 1f)
    return if (fraction <= 0.6f) {
        // Left 60%: -60 .. 0 dB
        val t = fraction / 0.6f
        -60f * (1f - t)
    } else {
        // Right 40%: 0 .. +24 dB
        val t = (fraction - 0.6f) / 0.4f
        24f * t
    }
}

/**
 * Convert a dB value to X pixel position.
 */
private fun dbToX(db: Float, width: Float): Float {
    val clamped = db.coerceIn(-60f, 24f)
    return if (clamped <= 0f) {
        // -60..0 → 0..60%
        val t = (clamped + 60f) / 60f
        width * 0.6f * t
    } else {
        // 0..+24 → 60..100%
        val t = clamped / 24f
        width * (0.6f + 0.4f * t)
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
