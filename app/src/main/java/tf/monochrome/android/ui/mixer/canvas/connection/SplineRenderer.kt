package tf.monochrome.android.ui.mixer.canvas.connection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Renders glowing bezier spline connections between nodes on the DSP canvas.
 *
 * Each connection is drawn as a 3-layer effect:
 * 1. Wide glow layer (low alpha, Screen blend)
 * 2. Core line (medium alpha)
 * 3. Thin highlight (white, low alpha)
 */
object SplineRenderer {

    /**
     * Draws a connection spline between two port positions with a 3-layer glow effect.
     *
     * @param from Start position (output port of source node, in scaled canvas coords)
     * @param to End position (input port of target node, in scaled canvas coords)
     * @param color The category neon color for this connection
     * @param scale Current viewport scale
     * @param alpha Overall alpha multiplier (0..1)
     */
    fun DrawScope.drawConnectionSpline(
        from: Offset,
        to: Offset,
        color: Color,
        scale: Float,
        alpha: Float = 1f
    ) {
        val path = buildSplinePath(from, to)

        // Layer 1: Glow (wide, low alpha, Screen blend)
        drawPath(
            path = path,
            color = color.copy(alpha = 0.12f * alpha),
            style = Stroke(
                width = 14f * scale,
                cap = StrokeCap.Round
            ),
            blendMode = BlendMode.Screen
        )

        // Layer 2: Core line
        drawPath(
            path = path,
            color = color.copy(alpha = 0.65f * alpha),
            style = Stroke(
                width = 2.5f * scale,
                cap = StrokeCap.Round
            )
        )

        // Layer 3: Highlight (thin white centre)
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.25f * alpha),
            style = Stroke(
                width = 1f * scale,
                cap = StrokeCap.Round
            )
        )
    }

    /**
     * Draws a temporary connection being dragged from a port.
     */
    fun DrawScope.drawDragConnectionSpline(
        from: Offset,
        to: Offset,
        color: Color,
        scale: Float
    ) {
        val path = buildSplinePath(from, to)

        // Dashed-style glow
        drawPath(
            path = path,
            color = color.copy(alpha = 0.08f),
            style = Stroke(width = 10f * scale, cap = StrokeCap.Round),
            blendMode = BlendMode.Screen
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.4f),
            style = Stroke(width = 2f * scale, cap = StrokeCap.Round)
        )

        // Endpoint indicator
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = 6f * scale,
            center = to
        )
    }

    /**
     * Builds a cubic bezier Path between two points.
     * Control points create smooth S-curves for horizontal signal flow.
     */
    fun buildSplinePath(from: Offset, to: Offset): Path {
        val deltaX = (to.x - from.x).coerceAtLeast(40f)
        val cp1 = Offset(from.x + deltaX * 0.45f, from.y)
        val cp2 = Offset(to.x - deltaX * 0.45f, to.y)

        return Path().apply {
            moveTo(from.x, from.y)
            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, to.x, to.y)
        }
    }
}
