package tf.monochrome.android.ui.mixer.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import tf.monochrome.android.ui.mixer.canvas.model.CanvasState
import tf.monochrome.android.ui.mixer.canvas.model.NodeColorScheme
import tf.monochrome.android.ui.mixer.canvas.model.NodeId

/**
 * Stateless DrawScope extension functions for rendering canvas-level elements
 * (background grid, debug info, etc.).
 */
object CanvasRenderer {

    /**
     * Draws the dot grid background.
     * Grid density adapts to the current zoom level.
     */
    fun DrawScope.drawGrid(
        state: CanvasState,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val scale = state.viewportScale
        val offsetX = state.viewportOffset.x
        val offsetY = state.viewportOffset.y

        // Base grid spacing that adapts to zoom
        val baseSpacing = 40f
        val spacing = baseSpacing * scale

        // Don't draw if spacing is too small (zoomed out too far)
        if (spacing < 10f) return

        // Alpha fades with zoom level
        val alpha = ((spacing - 10f) / 30f).coerceIn(0.1f, 0.6f)
        val dotColor = NodeColorScheme.GridDot.copy(alpha = alpha)

        val points = mutableListOf<Offset>()

        // Calculate visible range in screen coordinates
        val startX = (offsetX % spacing).let { if (it > 0) it - spacing else it }
        val startY = (offsetY % spacing).let { if (it > 0) it - spacing else it }

        var x = startX
        while (x <= canvasWidth + spacing) {
            var y = startY
            while (y <= canvasHeight + spacing) {
                points.add(Offset(x, y))
                y += spacing
            }
            x += spacing
        }

        if (points.isNotEmpty()) {
            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = dotColor,
                strokeWidth = 1.5f * (scale.coerceIn(0.5f, 1.5f))
            )
        }
    }

    /**
     * Draws a "no plugins" hint when a bus has no plugins.
     */
    fun DrawScope.drawEmptyBusHint(
        busInputOutputPort: Offset,
        masterInputPort: Offset,
        scale: Float,
        busIndex: Int
    ) {
        // Dashed line suggestion from bus input to master
        val color = NodeColorScheme.BusInput.copy(alpha = 0.1f)
        val dashLen = 8f * scale
        val gapLen = 6f * scale
        val totalLen = (masterInputPort - busInputOutputPort).getDistance()
        val direction = (masterInputPort - busInputOutputPort).let {
            Offset(it.x / totalLen, it.y / totalLen)
        }

        var dist = 0f
        while (dist < totalLen) {
            val start = busInputOutputPort + direction * dist
            val end = busInputOutputPort + direction * (dist + dashLen).coerceAtMost(totalLen)
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = 1f * scale
            )
            dist += dashLen + gapLen
        }
    }
}
