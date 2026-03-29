package tf.monochrome.android.ui.mixer.canvas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import tf.monochrome.android.ui.mixer.canvas.CanvasGestureHandler.screenToCanvas
import tf.monochrome.android.ui.mixer.canvas.CanvasRenderer.drawGrid
import tf.monochrome.android.ui.mixer.canvas.connection.ConnectionAnimator.drawConnectionPulse
import tf.monochrome.android.ui.mixer.canvas.connection.SplineRenderer.drawConnectionSpline
import tf.monochrome.android.ui.mixer.canvas.connection.SplineRenderer.drawDragConnectionSpline
import tf.monochrome.android.ui.mixer.canvas.model.CanvasOffset
import tf.monochrome.android.ui.mixer.canvas.model.CanvasState
import tf.monochrome.android.ui.mixer.canvas.model.DragState
import tf.monochrome.android.ui.mixer.canvas.model.DspNode
import tf.monochrome.android.ui.mixer.canvas.model.NodeColorScheme
import tf.monochrome.android.ui.mixer.canvas.model.NodeId
import tf.monochrome.android.ui.mixer.canvas.node.drawBusInputNode
import tf.monochrome.android.ui.mixer.canvas.node.drawMasterBusNode
import tf.monochrome.android.ui.mixer.canvas.node.drawOutputNode
import tf.monochrome.android.ui.mixer.canvas.node.drawPluginNode
import tf.monochrome.android.ui.mixer.canvas.node.inputPortPosition
import tf.monochrome.android.ui.mixer.canvas.node.outputPortPosition

/**
 * The main node-based DSP canvas composable.
 *
 * Renders a zoomable, pannable workspace showing all bus input nodes,
 * plugin chains, master bus, and output, connected by glowing bezier splines.
 * All rendering is done via DrawScope operations within a single Canvas
 * for optimal 60fps performance.
 */
@Composable
fun DspCanvas(
    state: CanvasState,
    dspEnabled: Boolean,
    audioAmplitude: Float,
    onViewportPan: (Offset) -> Unit,
    onViewportZoom: (Float, Offset) -> Unit,
    onNodeSelected: (NodeId?) -> Unit,
    onNodeDragStart: (NodeId) -> Unit,
    onNodeDrag: (NodeId, CanvasOffset) -> Unit,
    onNodeDragEnd: (NodeId) -> Unit,
    onNodeDoubleTap: (NodeId) -> Unit,
    onNodeLongPress: (NodeId) -> Unit,
    onDeleteConfirmed: (NodeId) -> Unit,
    onDeleteCancelled: () -> Unit,
    onCanvasTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current

    // Pulse animation for active connections
    val infiniteTransition = rememberInfiniteTransition(label = "connectionPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    // Track drag start position for distinguishing taps from drags
    val dragStartNodeId = remember { mutableListOf<NodeId?>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(NodeColorScheme.CanvasBackground)
            // Pinch-zoom + two-finger pan
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    if (zoom != 1f) {
                        onViewportZoom(zoom, centroid)
                    }
                    if (pan != Offset.Zero) {
                        onViewportPan(pan)
                    }
                }
            }
            // Tap gestures (select, double-tap edit, long-press delete)
            .pointerInput(state.deleteTargetId, state.selectedNodeId) {
                detectTapGestures(
                    onTap = { offset ->
                        // Check delete button first
                        val deleteHit = CanvasGestureHandler.hitTestDeleteButton(offset, state)
                        if (deleteHit != null) {
                            onDeleteConfirmed(deleteHit)
                            return@detectTapGestures
                        }

                        // If in delete mode, cancel it
                        if (state.deleteTargetId != null) {
                            onDeleteCancelled()
                            return@detectTapGestures
                        }

                        // Hit test nodes
                        val hitNode = CanvasGestureHandler.hitTestNode(offset, state)
                        if (hitNode != null) {
                            onNodeSelected(hitNode)
                        } else {
                            onNodeSelected(null)
                            onCanvasTap()
                        }
                    },
                    onDoubleTap = { offset ->
                        val hitNode = CanvasGestureHandler.hitTestNode(offset, state)
                        if (hitNode != null) {
                            onNodeDoubleTap(hitNode)
                        }
                    },
                    onLongPress = { offset ->
                        val hitNode = CanvasGestureHandler.hitTestNode(offset, state)
                        if (hitNode != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNodeLongPress(hitNode)
                        }
                    }
                )
            }
            // Node drag gestures
            .pointerInput(state.nodes.keys.hashCode()) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val hitNode = CanvasGestureHandler.hitTestNode(offset, state)
                        dragStartNodeId.clear()
                        dragStartNodeId.add(hitNode)
                        if (hitNode != null) {
                            onNodeDragStart(hitNode)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val nodeId = dragStartNodeId.firstOrNull()
                        if (nodeId != null) {
                            val canvasDelta = CanvasOffset(
                                dragAmount.x / state.viewportScale,
                                dragAmount.y / state.viewportScale
                            )
                            onNodeDrag(nodeId, canvasDelta)
                        } else {
                            // Drag on empty canvas = pan
                            onViewportPan(dragAmount)
                        }
                    },
                    onDragEnd = {
                        val nodeId = dragStartNodeId.firstOrNull()
                        if (nodeId != null) {
                            onNodeDragEnd(nodeId)
                        }
                        dragStartNodeId.clear()
                    },
                    onDragCancel = {
                        val nodeId = dragStartNodeId.firstOrNull()
                        if (nodeId != null) {
                            onNodeDragEnd(nodeId)
                        }
                        dragStartNodeId.clear()
                    }
                )
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val scale = state.viewportScale
        val offsetX = state.viewportOffset.x
        val offsetY = state.viewportOffset.y

        // ── Layer 0: Grid background ────────────────────────────────────
        drawGrid(state, canvasWidth, canvasHeight)

        // ── Apply viewport transform for all subsequent drawing ─────────
        val drawState = state // capture for lambda

        // Helper to transform canvas coords to screen coords for drawing
        fun Offset.toScreen() = Offset(
            this.x * scale + offsetX,
            this.y * scale + offsetY
        )

        // ── Layer 1: Connection splines ─────────────────────────────────
        for (connection in drawState.connections) {
            val fromNode = drawState.nodes[connection.fromId] ?: continue
            val toNode = drawState.nodes[connection.toId] ?: continue

            val fromPort = outputPortPosition(fromNode).toScreen()
            val toPort = when (toNode) {
                is DspNode.BusMaster -> inputPortPosition(toNode, connection.busIndex).toScreen()
                else -> inputPortPosition(toNode).toScreen()
            }

            val color = NodeColorScheme.connectionColor(connection.busIndex, fromNode)
            drawConnectionSpline(fromPort, toPort, color, scale)

            // Pulse animation on active connections
            if (dspEnabled) {
                drawConnectionPulse(
                    from = fromPort,
                    to = toPort,
                    color = color,
                    progress = pulseProgress,
                    scale = scale,
                    amplitude = audioAmplitude,
                    isActive = true
                )
            }
        }

        // ── Layer 2: Drag connection line ───────────────────────────────
        val dragConn = drawState.dragState as? DragState.ConnectionDrag
        if (dragConn != null) {
            val fromNode = drawState.nodes[dragConn.fromNodeId]
            if (fromNode != null) {
                val fromPort = outputPortPosition(fromNode).toScreen()
                val endScreen = dragConn.currentEndpoint.toComposeOffset()
                drawDragConnectionSpline(
                    from = fromPort,
                    to = Offset(
                        endScreen.x * scale + offsetX,
                        endScreen.y * scale + offsetY
                    ),
                    color = NodeColorScheme.nodeAccentColor(fromNode),
                    scale = scale
                )
            }
        }

        // ── Layer 3: Nodes ──────────────────────────────────────────────
        for ((id, node) in drawState.nodes) {
            // Viewport culling
            if (!CanvasGestureHandler.isNodeVisible(node, drawState, canvasWidth, canvasHeight)) {
                continue
            }

            // Apply drag offset if this node is being dragged
            val displayNode = when (val drag = drawState.dragState) {
                is DragState.NodeDrag if drag.nodeId == id -> {
                    node.withPosition(
                        CanvasOffset(
                            node.position.x + drag.currentOffset.x,
                            node.position.y + drag.currentOffset.y
                        )
                    )
                }
                else -> node
            }

            val isSelected = id == drawState.selectedNodeId
            val isDeleteTarget = id == drawState.deleteTargetId

            when (displayNode) {
                is DspNode.BusInput -> drawBusInputNode(
                    node = displayNode,
                    isSelected = isSelected,
                    textMeasurer = textMeasurer,
                    scale = scale
                )
                is DspNode.Plugin -> drawPluginNode(
                    node = displayNode,
                    isSelected = isSelected,
                    isDeleteTarget = isDeleteTarget,
                    textMeasurer = textMeasurer,
                    scale = scale,
                    vizData = null  // TODO: wire per-node viz data
                )
                is DspNode.BusMaster -> drawMasterBusNode(
                    node = displayNode,
                    isSelected = isSelected,
                    textMeasurer = textMeasurer,
                    scale = scale
                )
                is DspNode.Output -> drawOutputNode(
                    node = displayNode,
                    isSelected = isSelected,
                    textMeasurer = textMeasurer,
                    scale = scale
                )
            }
        }
    }
}
