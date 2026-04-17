package tf.monochrome.android.ui.mixer.canvas.node

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import tf.monochrome.android.ui.mixer.canvas.model.DspNode
import tf.monochrome.android.ui.mixer.canvas.model.NodeColorScheme
import tf.monochrome.android.ui.mixer.canvas.model.NodeDimensions

/**
 * DrawScope extension to render a plugin node block on the DSP Canvas.
 *
 * Layout within the node (160 x 100 dp):
 * ```
 * ┌──[Category header, 6dp]───────┐
 * │  Plugin Name                   │
 * │  ┌──────────────────────────┐  │
 * │  │   micro-visualization    │  │
 * │  └──────────────────────────┘  │
 * │  ● Active/Bypassed   param    │
 * ◯──┤                          ├──◯
 * └────────────────────────────────┘
 * ```
 */
fun DrawScope.drawPluginNode(
    node: DspNode.Plugin,
    isSelected: Boolean,
    isDeleteTarget: Boolean,
    textMeasurer: TextMeasurer,
    scale: Float,
    vizData: FloatArray? = null
) {
    val w = NodeDimensions.PLUGIN_WIDTH * scale
    val h = NodeDimensions.PLUGIN_HEIGHT * scale
    val x = node.position.x * scale
    val y = node.position.y * scale
    val cr = CornerRadius(8f * scale)
    val accent = NodeColorScheme.categoryColor(node.snapinType.category)

    // ── Selection glow ──────────────────────────────────────────────────
    if (isSelected) {
        drawRoundRect(
            color = accent.copy(alpha = 0.2f),
            topLeft = Offset(x - 6f * scale, y - 6f * scale),
            size = Size(w + 12f * scale, h + 12f * scale),
            cornerRadius = CornerRadius(12f * scale)
        )
    }

    // ── Delete mode overlay ─────────────────────────────────────────────
    if (isDeleteTarget) {
        drawRoundRect(
            color = NodeColorScheme.DeleteBackground,
            topLeft = Offset(x - 4f * scale, y - 4f * scale),
            size = Size(w + 8f * scale, h + 8f * scale),
            cornerRadius = CornerRadius(12f * scale)
        )
    }

    // ── Body ────────────────────────────────────────────────────────────
    drawRoundRect(
        color = NodeColorScheme.NodeBackground,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr
    )

    // ── Border ──────────────────────────────────────────────────────────
    val borderColor = when {
        isDeleteTarget -> NodeColorScheme.DeleteRed
        isSelected -> accent.copy(alpha = 0.8f)
        else -> NodeColorScheme.NodeBorder
    }
    val borderWidth = when {
        isDeleteTarget -> 2f * scale
        isSelected -> 2f * scale
        else -> 1f * scale
    }
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr,
        style = Stroke(width = borderWidth)
    )

    // ── Header bar (category colored) ───────────────────────────────────
    val headerH = 6f * scale
    drawRoundRect(
        color = if (node.bypassed) accent.copy(alpha = 0.3f) else accent,
        topLeft = Offset(x, y),
        size = Size(w, headerH),
        cornerRadius = CornerRadius(cr.x, cr.y)
    )

    // ── Plugin name ─────────────────────────────────────────────────────
    val nameStyle = TextStyle(
        color = if (node.bypassed) NodeColorScheme.NodeTextDim else NodeColorScheme.NodeText,
        fontSize = (11f * scale).sp,
        fontWeight = FontWeight.SemiBold
    )
    val displayName = node.snapinType.displayName
    val nameResult = textMeasurer.measure(
        text = displayName,
        style = nameStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1
    )
    drawText(
        textLayoutResult = nameResult,
        topLeft = Offset(
            x + 10f * scale,
            y + headerH + 4f * scale
        )
    )

    // ── Micro-visualization area ────────────────────────────────────────
    val vizX = x + 10f * scale
    val vizY = y + headerH + 4f * scale + nameResult.size.height + 4f * scale
    val vizW = w - 20f * scale
    val vizH = 36f * scale

    // Viz background
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(vizX, vizY),
        size = Size(vizW, vizH),
        cornerRadius = CornerRadius(4f * scale)
    )

    // Draw micro-visualization
    drawMicroViz(node, vizX, vizY, vizW, vizH, accent, scale, vizData)

    // ── Bypass indicator dot ────────────────────────────────────────────
    val statusY = vizY + vizH + 6f * scale
    val dotRadius = 3f * scale
    drawCircle(
        color = if (node.bypassed) NodeColorScheme.NodeTextDim else Color(0xFF4CAF50),
        radius = dotRadius,
        center = Offset(x + 10f * scale + dotRadius, statusY + dotRadius)
    )

    // Status label
    val statusStyle = TextStyle(
        color = NodeColorScheme.NodeTextDim,
        fontSize = (8f * scale).sp
    )
    val statusText = if (node.bypassed) "Bypassed" else "Active"
    val statusResult = textMeasurer.measure(statusText, statusStyle)
    drawText(
        textLayoutResult = statusResult,
        topLeft = Offset(
            x + 10f * scale + dotRadius * 2 + 4f * scale,
            statusY
        )
    )

    // ── Bypass overlay ──────────────────────────────────────────────────
    if (node.bypassed) {
        drawRoundRect(
            color = NodeColorScheme.BypassedOverlay,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = cr
        )
    }

    // ── Ports ───────────────────────────────────────────────────────────
    // Input port (left edge, vertically centred)
    drawPort(
        center = Offset(x, y + h / 2f),
        color = accent,
        scale = scale
    )

    // Output port (right edge, vertically centred)
    drawPort(
        center = Offset(x + w, y + h / 2f),
        color = accent,
        scale = scale
    )

    // ── Delete X overlay ────────────────────────────────────────────────
    if (isDeleteTarget) {
        val xSize = 18f * scale
        val xCx = x + w - 14f * scale
        val xCy = y + 14f * scale
        // Red circle background
        drawCircle(
            color = NodeColorScheme.DeleteRed,
            radius = xSize / 2f + 2f * scale,
            center = Offset(xCx, xCy)
        )
        // X lines
        val half = xSize / 2f * 0.6f
        drawLine(
            color = Color.White,
            start = Offset(xCx - half, xCy - half),
            end = Offset(xCx + half, xCy + half),
            strokeWidth = 2f * scale
        )
        drawLine(
            color = Color.White,
            start = Offset(xCx + half, xCy - half),
            end = Offset(xCx - half, xCy + half),
            strokeWidth = 2f * scale
        )
    }
}

/**
 * Draws micro-visualization inside the plugin node based on its category.
 */
private fun DrawScope.drawMicroViz(
    node: DspNode.Plugin,
    vizX: Float,
    vizY: Float,
    vizW: Float,
    vizH: Float,
    accent: Color,
    scale: Float,
    vizData: FloatArray?
) {
    val category = node.snapinType.category
    val vizColor = accent.copy(alpha = 0.7f)

    when (category) {
        tf.monochrome.android.audio.dsp.SnapinCategory.UTILITY -> {
            // Level meter bar
            val barH = vizH * 0.4f
            val barY = vizY + (vizH - barH) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.05f),
                topLeft = Offset(vizX + 4f * scale, barY),
                size = Size(vizW - 8f * scale, barH),
                cornerRadius = CornerRadius(barH / 2f)
            )
            // Filled level based on gain param
            val gainParam = node.parameters[0] ?: 0f
            val level = ((gainParam + 100f) / 124f).coerceIn(0f, 1f)
            if (level > 0f) {
                drawRoundRect(
                    color = vizColor,
                    topLeft = Offset(vizX + 4f * scale, barY),
                    size = Size((vizW - 8f * scale) * level, barH),
                    cornerRadius = CornerRadius(barH / 2f)
                )
            }
        }

        tf.monochrome.android.audio.dsp.SnapinCategory.EQ_FILTER -> {
            // Mini frequency response curve
            val points = 32
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0 until points) {
                val t = i.toFloat() / (points - 1)
                val freq = 20f * Math.pow(1000.0, t.toDouble()).toFloat()
                // Simple bell curve response based on cutoff param
                val cutoff = node.parameters[1] ?: 1000f
                val q = node.parameters[2] ?: 0.707f
                val gain = node.parameters[3] ?: 0f
                val ratio = freq / cutoff
                val response = gain / (1f + q * (ratio - 1f / ratio).let { it * it })
                val normY = vizY + vizH / 2f - (response / 24f) * (vizH / 2f)
                val px = vizX + t * vizW
                val py = normY.coerceIn(vizY, vizY + vizH)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = vizColor, style = Stroke(width = 1.5f * scale))
            // Zero line
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(vizX, vizY + vizH / 2f),
                end = Offset(vizX + vizW, vizY + vizH / 2f),
                strokeWidth = 0.5f * scale
            )
        }

        tf.monochrome.android.audio.dsp.SnapinCategory.DYNAMICS -> {
            // Threshold line + gain reduction arc/meter
            val threshold = node.parameters[0] ?: -20f // dB
            val threshNorm = ((threshold + 60f) / 60f).coerceIn(0f, 1f)
            val threshX = vizX + threshNorm * vizW

            // Background grid
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(vizX, vizY + vizH / 2f),
                end = Offset(vizX + vizW, vizY + vizH / 2f),
                strokeWidth = 0.5f * scale
            )

            // 1:1 line (diagonal)
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(vizX, vizY + vizH),
                end = Offset(vizX + vizW, vizY),
                strokeWidth = 0.5f * scale
            )

            // Threshold marker
            drawLine(
                color = vizColor,
                start = Offset(threshX, vizY),
                end = Offset(threshX, vizY + vizH),
                strokeWidth = 1f * scale
            )

            // Compressed curve (ratio-based bend at threshold)
            val ratio = node.parameters[1] ?: 4f
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..32) {
                val t = i / 32f
                val inputDb = -60f + t * 60f
                val outputDb = if (inputDb < threshold) inputDb
                else threshold + (inputDb - threshold) / ratio.coerceAtLeast(1f)
                val outNorm = ((outputDb + 60f) / 60f).coerceIn(0f, 1f)
                val px = vizX + t * vizW
                val py = vizY + vizH * (1f - outNorm)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = vizColor, style = Stroke(width = 1.5f * scale))
        }

        tf.monochrome.android.audio.dsp.SnapinCategory.DISTORTION -> {
            // Transfer curve (waveshaper)
            val drive = node.parameters[0] ?: 0.5f
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..32) {
                val t = i / 32f
                val input = t * 2f - 1f // -1 to +1
                // Soft clipping: tanh approximation
                val k = (1f + drive * 10f)
                val output = (input * k / (1f + kotlin.math.abs(input * k))).coerceIn(-1f, 1f)
                val px = vizX + t * vizW
                val py = vizY + vizH * (0.5f - output * 0.45f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = vizColor, style = Stroke(width = 1.5f * scale))
            // Centre crosshair
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(vizX + vizW / 2f, vizY),
                end = Offset(vizX + vizW / 2f, vizY + vizH),
                strokeWidth = 0.5f * scale
            )
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(vizX, vizY + vizH / 2f),
                end = Offset(vizX + vizW, vizY + vizH / 2f),
                strokeWidth = 0.5f * scale
            )
        }

        tf.monochrome.android.audio.dsp.SnapinCategory.MODULATION -> {
            // LFO waveform
            val rate = node.parameters[0] ?: 1f
            val depth = node.parameters[1] ?: 0.5f
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..48) {
                val t = i / 48f
                val phase = t * rate.coerceIn(0.5f, 4f) * 2f * Math.PI.toFloat()
                val sample = kotlin.math.sin(phase) * depth.coerceIn(0f, 1f)
                val px = vizX + t * vizW
                val py = vizY + vizH * (0.5f - sample * 0.4f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = vizColor, style = Stroke(width = 1.5f * scale))
            // Centre line
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(vizX, vizY + vizH / 2f),
                end = Offset(vizX + vizW, vizY + vizH / 2f),
                strokeWidth = 0.5f * scale
            )
        }

        tf.monochrome.android.audio.dsp.SnapinCategory.SPACE -> {
            // Decay envelope
            val time = node.parameters[0] ?: 0.5f
            val feedback = node.parameters[1] ?: 0.3f
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..32) {
                val t = i / 32f
                // Exponential decay
                val decayRate = (1f - feedback.coerceIn(0f, 0.95f)) * 5f
                val env = kotlin.math.exp(-t * decayRate * time.coerceAtLeast(0.1f) * 3f)
                val px = vizX + t * vizW
                val py = vizY + vizH * (1f - env * 0.85f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path = path, color = vizColor, style = Stroke(width = 1.5f * scale))
            // Reflections (small vertical ticks)
            val numTicks = (4 * time.coerceIn(0.3f, 2f)).toInt()
            for (i in 0 until numTicks) {
                val tx = vizX + (i + 1f) / (numTicks + 1f) * vizW
                val decay = kotlin.math.exp(-(i + 1f) * 0.5f)
                val tickH = vizH * 0.5f * decay
                drawLine(
                    color = vizColor.copy(alpha = 0.4f * decay),
                    start = Offset(tx, vizY + vizH),
                    end = Offset(tx, vizY + vizH - tickH),
                    strokeWidth = 1.5f * scale
                )
            }
        }
    }
}
