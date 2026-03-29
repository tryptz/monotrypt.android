package tf.monochrome.android.ui.mixer.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import tf.monochrome.android.ui.mixer.canvas.model.CanvasOffset
import tf.monochrome.android.ui.mixer.canvas.model.CanvasState
import tf.monochrome.android.ui.mixer.canvas.model.DspNode
import tf.monochrome.android.ui.mixer.canvas.model.NodeDimensions
import tf.monochrome.android.ui.mixer.canvas.model.NodeId
import tf.monochrome.android.ui.mixer.canvas.node.nodeBounds

/**
 * Handles hit testing and coordinate transforms for the DSP canvas.
 * All methods are pure functions operating on [CanvasState].
 */
object CanvasGestureHandler {

    /**
     * Converts a screen-space position to canvas-space coordinates,
     * accounting for the current viewport offset and scale.
     */
    fun screenToCanvas(screenPos: Offset, state: CanvasState): CanvasOffset {
        val x = (screenPos.x - state.viewportOffset.x) / state.viewportScale
        val y = (screenPos.y - state.viewportOffset.y) / state.viewportScale
        return CanvasOffset(x, y)
    }

    /**
     * Converts canvas-space coordinates to screen-space position.
     */
    fun canvasToScreen(canvasPos: CanvasOffset, state: CanvasState): Offset {
        val x = canvasPos.x * state.viewportScale + state.viewportOffset.x
        val y = canvasPos.y * state.viewportScale + state.viewportOffset.y
        return Offset(x, y)
    }

    /**
     * Hit-tests all nodes to find which one (if any) is under the given
     * screen-space position. Returns the NodeId or null.
     */
    fun hitTestNode(screenPos: Offset, state: CanvasState): NodeId? {
        val canvasPos = screenToCanvas(screenPos, state)
        // Test in reverse draw order (top-most first)
        for ((id, node) in state.nodes.entries.reversed()) {
            val bounds = nodeBounds(node)
            if (bounds.contains(canvasPos.toComposeOffset())) {
                return id
            }
        }
        return null
    }

    /**
     * Hit-tests for a port circle (input or output) at the given screen position.
     * Returns a Pair of (NodeId, isOutputPort) or null.
     */
    fun hitTestPort(screenPos: Offset, state: CanvasState): Pair<NodeId, Boolean>? {
        val canvasPos = screenToCanvas(screenPos, state)
        val hitRadius = NodeDimensions.PORT_HIT_RADIUS

        for ((id, node) in state.nodes) {
            val bounds = nodeBounds(node)

            // Output port (right edge, centre)
            if (node !is DspNode.Output) {
                val outPort = Offset(bounds.right, bounds.top + bounds.height / 2f)
                if ((canvasPos.toComposeOffset() - outPort).getDistance() <= hitRadius) {
                    return id to true
                }
            }

            // Input port (left edge, centre) — except BusInput which has no input
            if (node !is DspNode.BusInput) {
                when (node) {
                    is DspNode.BusMaster -> {
                        // 4 input ports
                        val portSpacing = bounds.height / 5f
                        for (i in 0 until 4) {
                            val inPort = Offset(bounds.left, bounds.top + portSpacing * (i + 1))
                            if ((canvasPos.toComposeOffset() - inPort).getDistance() <= hitRadius) {
                                return id to false
                            }
                        }
                    }
                    else -> {
                        val inPort = Offset(bounds.left, bounds.top + bounds.height / 2f)
                        if ((canvasPos.toComposeOffset() - inPort).getDistance() <= hitRadius) {
                            return id to false
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Hit-tests the delete X button on a node in delete mode.
     */
    fun hitTestDeleteButton(screenPos: Offset, state: CanvasState): NodeId? {
        val deleteId = state.deleteTargetId ?: return null
        val node = state.nodes[deleteId] ?: return null
        val canvasPos = screenToCanvas(screenPos, state)
        val bounds = nodeBounds(node)

        // X button is at top-right corner of the node
        val xCenter = Offset(bounds.right - 14f, bounds.top + 14f)
        val xRadius = 16f

        if ((canvasPos.toComposeOffset() - xCenter).getDistance() <= xRadius) {
            return deleteId
        }
        return null
    }

    /**
     * Snaps a canvas position to the nearest grid point.
     */
    fun snapToGrid(pos: CanvasOffset, gridSize: Float = 20f): CanvasOffset {
        return CanvasOffset(
            x = (pos.x / gridSize).let { kotlin.math.round(it) } * gridSize,
            y = (pos.y / gridSize).let { kotlin.math.round(it) } * gridSize
        )
    }

    /**
     * Checks if a bounding rect is visible in the current viewport.
     * Used for culling off-screen nodes.
     */
    fun isNodeVisible(
        node: DspNode,
        state: CanvasState,
        canvasWidth: Float,
        canvasHeight: Float
    ): Boolean {
        val bounds = nodeBounds(node)
        val screenTopLeft = canvasToScreen(
            CanvasOffset(bounds.left, bounds.top), state
        )
        val screenBottomRight = canvasToScreen(
            CanvasOffset(bounds.right, bounds.bottom), state
        )

        val viewport = Rect(0f, 0f, canvasWidth, canvasHeight)
        val nodeScreen = Rect(
            screenTopLeft.x, screenTopLeft.y,
            screenBottomRight.x, screenBottomRight.y
        )

        return viewport.overlaps(nodeScreen)
    }
}
