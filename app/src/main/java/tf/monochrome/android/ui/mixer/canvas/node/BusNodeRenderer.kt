package tf.monochrome.android.ui.mixer.canvas.node

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tf.monochrome.android.ui.mixer.canvas.model.DspNode
import tf.monochrome.android.ui.mixer.canvas.model.NodeColorScheme
import tf.monochrome.android.ui.mixer.canvas.model.NodeDimensions

/**
 * DrawScope extensions for rendering bus input, master, and output nodes
 * on the DSP Canvas.
 */

/**
 * Draws a bus input node — the leftmost node in a bus chain.
 * Shows bus name and an output port on the right edge.
 */
fun DrawScope.drawBusInputNode(
    node: DspNode.BusInput,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    scale: Float
) {
    val w = NodeDimensions.BUS_INPUT_WIDTH * scale
    val h = NodeDimensions.BUS_INPUT_HEIGHT * scale
    val x = node.position.x * scale
    val y = node.position.y * scale
    val cr = CornerRadius(8f * scale)
    val accent = NodeColorScheme.BusInput

    // Selection glow
    if (isSelected) {
        drawRoundRect(
            color = accent.copy(alpha = 0.15f),
            topLeft = Offset(x - 4f * scale, y - 4f * scale),
            size = Size(w + 8f * scale, h + 8f * scale),
            cornerRadius = CornerRadius(12f * scale)
        )
    }

    // Body
    drawRoundRect(
        color = NodeColorScheme.NodeBackground,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr
    )

    // Border
    drawRoundRect(
        color = if (isSelected) accent.copy(alpha = 0.8f) else NodeColorScheme.NodeBorder,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr,
        style = Stroke(width = if (isSelected) 2f * scale else 1f * scale)
    )

    // Header bar
    val headerH = 6f * scale
    drawRoundRect(
        color = accent,
        topLeft = Offset(x, y),
        size = Size(w, headerH),
        cornerRadius = CornerRadius(cr.x, cr.y)
    )

    // Bus name
    val nameStyle = TextStyle(
        color = NodeColorScheme.NodeText,
        fontSize = (13f * scale).sp,
        fontWeight = FontWeight.Bold
    )
    val nameResult = textMeasurer.measure(node.busName, nameStyle)
    drawText(
        textLayoutResult = nameResult,
        topLeft = Offset(
            x + (w - nameResult.size.width) / 2f,
            y + headerH + 10f * scale
        )
    )

    // "INPUT" label
    val labelStyle = TextStyle(
        color = NodeColorScheme.NodeTextDim,
        fontSize = (9f * scale).sp,
        fontWeight = FontWeight.Medium
    )
    val labelResult = textMeasurer.measure("INPUT", labelStyle)
    drawText(
        textLayoutResult = labelResult,
        topLeft = Offset(
            x + (w - labelResult.size.width) / 2f,
            y + headerH + 10f * scale + nameResult.size.height + 4f * scale
        )
    )

    // Output port (right edge, vertically centred)
    drawPort(
        center = Offset(x + w, y + h / 2f),
        color = accent,
        scale = scale
    )
}

/**
 * Draws the master bus node — where all 4 input buses converge.
 */
fun DrawScope.drawMasterBusNode(
    node: DspNode.BusMaster,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    scale: Float
) {
    val w = NodeDimensions.MASTER_WIDTH * scale
    val h = NodeDimensions.MASTER_HEIGHT * scale
    val x = node.position.x * scale
    val y = node.position.y * scale
    val cr = CornerRadius(8f * scale)
    val accent = NodeColorScheme.Master

    // Selection glow
    if (isSelected) {
        drawRoundRect(
            color = accent.copy(alpha = 0.15f),
            topLeft = Offset(x - 4f * scale, y - 4f * scale),
            size = Size(w + 8f * scale, h + 8f * scale),
            cornerRadius = CornerRadius(12f * scale)
        )
    }

    // Body
    drawRoundRect(
        color = NodeColorScheme.NodeBackground,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr
    )

    // Border
    drawRoundRect(
        color = if (isSelected) accent.copy(alpha = 0.8f) else NodeColorScheme.NodeBorder,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr,
        style = Stroke(width = if (isSelected) 2f * scale else 1f * scale)
    )

    // Header bar
    val headerH = 6f * scale
    drawRoundRect(
        color = accent,
        topLeft = Offset(x, y),
        size = Size(w, headerH),
        cornerRadius = CornerRadius(cr.x, cr.y)
    )

    // "MASTER" name
    val nameStyle = TextStyle(
        color = NodeColorScheme.NodeText,
        fontSize = (13f * scale).sp,
        fontWeight = FontWeight.Bold
    )
    val nameResult = textMeasurer.measure("Master", nameStyle)
    drawText(
        textLayoutResult = nameResult,
        topLeft = Offset(
            x + (w - nameResult.size.width) / 2f,
            y + headerH + 10f * scale
        )
    )

    // Mini VU bar (simple level indicator)
    val vuY = y + headerH + 10f * scale + nameResult.size.height + 8f * scale
    val vuW = w * 0.6f
    val vuH = 6f * scale
    val vuX = x + (w - vuW) / 2f
    // Background
    drawRoundRect(
        color = Color.White.copy(alpha = 0.06f),
        topLeft = Offset(vuX, vuY),
        size = Size(vuW, vuH),
        cornerRadius = CornerRadius(vuH / 2f)
    )
    // Filled level (mapped from gain)
    val level = ((node.gainDb + 60f) / 84f).coerceIn(0f, 1f) // -60..+24 → 0..1
    if (level > 0f && !node.muted) {
        drawRoundRect(
            color = if (level > 0.85f) Color(0xFFE53935) else accent,
            topLeft = Offset(vuX, vuY),
            size = Size(vuW * level, vuH),
            cornerRadius = CornerRadius(vuH / 2f)
        )
    }

    // Mute indicator
    if (node.muted) {
        val muteStyle = TextStyle(
            color = Color(0xFFE53935),
            fontSize = (10f * scale).sp,
            fontWeight = FontWeight.Bold
        )
        val muteResult = textMeasurer.measure("MUTED", muteStyle)
        drawText(
            textLayoutResult = muteResult,
            topLeft = Offset(
                x + (w - muteResult.size.width) / 2f,
                vuY + vuH + 4f * scale
            )
        )
    }

    // Input ports (left edge — 4 ports for 4 buses, stacked vertically)
    val portSpacing = h / 5f
    for (i in 0 until 4) {
        drawPort(
            center = Offset(x, y + portSpacing * (i + 1)),
            color = NodeColorScheme.BusInput,
            scale = scale
        )
    }

    // Output port (right edge)
    drawPort(
        center = Offset(x + w, y + h / 2f),
        color = accent,
        scale = scale
    )
}

/**
 * Draws the output node — the final destination of the signal graph.
 */
fun DrawScope.drawOutputNode(
    node: DspNode.Output,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    scale: Float
) {
    val w = NodeDimensions.OUTPUT_WIDTH * scale
    val h = NodeDimensions.OUTPUT_HEIGHT * scale
    val x = node.position.x * scale
    val y = node.position.y * scale
    val cr = CornerRadius(8f * scale)
    val accent = NodeColorScheme.OutputNode

    // Selection glow
    if (isSelected) {
        drawRoundRect(
            color = accent.copy(alpha = 0.15f),
            topLeft = Offset(x - 4f * scale, y - 4f * scale),
            size = Size(w + 8f * scale, h + 8f * scale),
            cornerRadius = CornerRadius(12f * scale)
        )
    }

    // Body
    drawRoundRect(
        color = NodeColorScheme.NodeBackground,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr
    )

    // Border
    drawRoundRect(
        color = if (isSelected) accent.copy(alpha = 0.8f) else NodeColorScheme.NodeBorder,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = cr,
        style = Stroke(width = if (isSelected) 2f * scale else 1f * scale)
    )

    // Speaker icon (triangle)
    val iconSize = 16f * scale
    val iconX = x + (w - iconSize) / 2f
    val iconY = y + (h - iconSize) / 2f - 4f * scale
    val trianglePath = Path().apply {
        moveTo(iconX, iconY + iconSize * 0.2f)
        lineTo(iconX + iconSize * 0.4f, iconY + iconSize * 0.2f)
        lineTo(iconX + iconSize, iconY)
        lineTo(iconX + iconSize, iconY + iconSize)
        lineTo(iconX + iconSize * 0.4f, iconY + iconSize * 0.8f)
        lineTo(iconX, iconY + iconSize * 0.8f)
        close()
    }
    drawPath(path = trianglePath, color = accent.copy(alpha = 0.7f))

    // "OUT" label
    val labelStyle = TextStyle(
        color = NodeColorScheme.NodeTextDim,
        fontSize = (9f * scale).sp,
        fontWeight = FontWeight.Medium
    )
    val labelResult = textMeasurer.measure("OUT", labelStyle)
    drawText(
        textLayoutResult = labelResult,
        topLeft = Offset(
            x + (w - labelResult.size.width) / 2f,
            y + h - labelResult.size.height - 4f * scale
        )
    )

    // Input port (left edge)
    drawPort(
        center = Offset(x, y + h / 2f),
        color = accent,
        scale = scale
    )
}

/**
 * Draws a small port circle used for input/output connections.
 */
fun DrawScope.drawPort(
    center: Offset,
    color: Color,
    scale: Float,
    filled: Boolean = true
) {
    val radius = NodeDimensions.PORT_RADIUS * scale
    // Outer glow
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = radius * 1.5f,
        center = center
    )
    // Main circle
    if (filled) {
        drawCircle(color = color.copy(alpha = 0.9f), radius = radius, center = center)
    } else {
        drawCircle(
            color = color.copy(alpha = 0.9f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5f * scale)
        )
    }
    // Inner highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = radius * 0.4f,
        center = center
    )
}

/**
 * Returns the bounding rect (in canvas coordinates, unscaled) for a given node.
 */
fun nodeBounds(node: DspNode): Rect {
    val (w, h) = when (node) {
        is DspNode.BusInput  -> NodeDimensions.BUS_INPUT_WIDTH to NodeDimensions.BUS_INPUT_HEIGHT
        is DspNode.Plugin    -> NodeDimensions.PLUGIN_WIDTH to NodeDimensions.PLUGIN_HEIGHT
        is DspNode.BusMaster -> NodeDimensions.MASTER_WIDTH to NodeDimensions.MASTER_HEIGHT
        is DspNode.Output    -> NodeDimensions.OUTPUT_WIDTH to NodeDimensions.OUTPUT_HEIGHT
    }
    return Rect(
        left = node.position.x,
        top = node.position.y,
        right = node.position.x + w,
        bottom = node.position.y + h
    )
}

/**
 * Returns the output port position (in canvas coordinates, unscaled).
 */
fun outputPortPosition(node: DspNode): Offset {
    val bounds = nodeBounds(node)
    return Offset(bounds.right, bounds.top + bounds.height / 2f)
}

/**
 * Returns the input port position (in canvas coordinates, unscaled).
 * For BusMaster, returns the port position for a specific bus index.
 */
fun inputPortPosition(node: DspNode, busIndex: Int = 0): Offset {
    val bounds = nodeBounds(node)
    return when (node) {
        is DspNode.BusMaster -> {
            val portSpacing = bounds.height / 5f
            Offset(bounds.left, bounds.top + portSpacing * (busIndex + 1))
        }
        else -> Offset(bounds.left, bounds.top + bounds.height / 2f)
    }
}
