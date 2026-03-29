package tf.monochrome.android.ui.mixer.canvas.connection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Renders audio-reactive pulse dots that travel along connection splines,
 * creating the visual impression of signal flowing through virtual cables.
 */
object ConnectionAnimator {

    /**
     * Draws an animated pulse dot that travels along a connection path.
     *
     * @param from Output port position of source node
     * @param to Input port position of target node
     * @param color Connection neon color
     * @param progress Animation progress 0..1 (from `infiniteTransition`)
     * @param scale Current viewport scale
     * @param amplitude Audio amplitude 0..1 (from ProjectMAudioBus RMS)
     * @param isActive Whether audio is actively passing through this connection
     */
    fun DrawScope.drawConnectionPulse(
        from: Offset,
        to: Offset,
        color: Color,
        progress: Float,
        scale: Float,
        amplitude: Float = 0.5f,
        isActive: Boolean = true
    ) {
        if (!isActive) return

        val path = SplineRenderer.buildSplinePath(from, to)
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(path, false)
        val pathLength = pathMeasure.length

        if (pathLength <= 0f) return

        // Primary dot position
        val dotPosition = pathMeasure.getPosition(progress * pathLength)

        // Pulse intensity modulated by audio amplitude
        val pulseAlpha = (0.3f + amplitude * 0.7f).coerceIn(0f, 1f)
        val pulseRadius = (4f + amplitude * 4f) * scale

        // Outer glow
        drawCircle(
            color = color.copy(alpha = pulseAlpha * 0.3f),
            radius = pulseRadius * 2.5f,
            center = dotPosition,
            blendMode = BlendMode.Screen
        )

        // Core dot
        drawCircle(
            color = color.copy(alpha = pulseAlpha * 0.8f),
            radius = pulseRadius,
            center = dotPosition
        )

        // Inner highlight
        drawCircle(
            color = Color.White.copy(alpha = pulseAlpha * 0.5f),
            radius = pulseRadius * 0.4f,
            center = dotPosition
        )

        // Trail dots (smaller dots behind the main pulse)
        val trailCount = 3
        for (i in 1..trailCount) {
            val trailProgress = (progress - i * 0.04f).let {
                if (it < 0f) it + 1f else it
            }
            val trailPos = pathMeasure.getPosition(trailProgress * pathLength)
            val trailAlpha = pulseAlpha * (1f - i.toFloat() / (trailCount + 1))
            val trailRadius = pulseRadius * (1f - i * 0.2f)

            drawCircle(
                color = color.copy(alpha = trailAlpha * 0.4f),
                radius = trailRadius,
                center = trailPos
            )
        }
    }
}
