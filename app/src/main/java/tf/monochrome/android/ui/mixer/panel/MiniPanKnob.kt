package tf.monochrome.android.ui.mixer.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact 32dp pan knob — reuses drawing logic from PanKnob.kt
 * but at a smaller scale for the slim bottom control panel.
 */
@Composable
fun MiniPanKnob(
    value: Float,               // -1 .. +1
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val activeColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onPrimary
    val haptic = LocalHapticFeedback.current
    val firedDetent = remember { mutableSetOf<Boolean>() }

    Canvas(
        modifier = modifier
            .size(32.dp)
            .pointerInput(Unit) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                detectDragGestures { change, _ ->
                    change.consume()
                    val pos = change.position
                    val angle = atan2(pos.x - cx, -(pos.y - cy)).toFloat()
                    val maxAngle = (135f * PI / 180f).toFloat()
                    val clamped = angle.coerceIn(-maxAngle, maxAngle)
                    val newVal = (clamped / maxAngle).coerceIn(-1f, 1f)
                    onValueChange(newVal)

                    if (newVal in -0.05f..0.05f) {
                        if (firedDetent.isEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            firedDetent.add(true)
                        }
                    } else {
                        firedDetent.clear()
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f - 4.dp.toPx()
        val strokeWidth = 2.5.dp.toPx()

        // Background arc (270 degrees)
        drawArc(
            color = trackColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active arc from centre
        val fraction = (value + 1f) / 2f
        val centreFraction = 0.5f
        if (fraction != centreFraction) {
            val sweepDeg = (fraction - centreFraction) * 270f
            val startDeg = 135f + centreFraction * 270f
            drawArc(
                color = activeColor,
                startAngle = startDeg,
                sweepAngle = sweepDeg,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Indicator dot
        val indicatorAngle = Math.toRadians((135.0 + fraction * 270.0)).toFloat()
        val dotRadius = 3.dp.toPx()
        val dotX = cx + radius * cos(indicatorAngle)
        val dotY = cy + radius * sin(indicatorAngle)
        drawCircle(color = activeColor, radius = dotRadius + 0.5.dp.toPx(), center = Offset(dotX, dotY))
        drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, dotY))

        // Centre tick
        val tickAngle = Math.toRadians(270.0).toFloat()
        val tickInner = radius - 3.dp.toPx()
        val tickOuter = radius + 3.dp.toPx()
        drawLine(
            color = trackColor,
            start = Offset(cx + tickInner * cos(tickAngle), cy + tickInner * sin(tickAngle)),
            end = Offset(cx + tickOuter * cos(tickAngle), cy + tickOuter * sin(tickAngle)),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
