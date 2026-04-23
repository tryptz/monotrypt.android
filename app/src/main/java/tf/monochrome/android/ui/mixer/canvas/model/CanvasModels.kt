package tf.monochrome.android.ui.mixer.canvas.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import tf.monochrome.android.audio.dsp.SnapinType
import java.util.UUID

typealias NodeId = String

/**
 * Position in canvas coordinate space (pre-viewport-transform).
 */
@Serializable
data class CanvasOffset(val x: Float, val y: Float) {
    operator fun plus(other: CanvasOffset) = CanvasOffset(x + other.x, y + other.y)
    operator fun minus(other: CanvasOffset) = CanvasOffset(x - other.x, y - other.y)
    fun toComposeOffset() = Offset(x, y)

    companion object {
        val Zero = CanvasOffset(0f, 0f)
    }
}

/**
 * A node on the DSP canvas. Each node type maps to a concept in the DSP engine's
 * 5-bus architecture (4 input buses + 1 master).
 */
sealed class DspNode {
    abstract val id: NodeId
    abstract val position: CanvasOffset

    abstract fun withPosition(position: CanvasOffset): DspNode

    /** Bus input — left edge of a bus chain. */
    data class BusInput(
        override val id: NodeId = UUID.randomUUID().toString(),
        override val position: CanvasOffset,
        val busIndex: Int,
        val busName: String
    ) : DspNode() {
        override fun withPosition(position: CanvasOffset) = copy(position = position)
    }

    /** A plugin instance in a bus's processing chain. */
    data class Plugin(
        override val id: NodeId = UUID.randomUUID().toString(),
        override val position: CanvasOffset,
        val busIndex: Int,
        val slotIndex: Int,
        val snapinType: SnapinType,
        val bypassed: Boolean = false,
        val parameters: Map<Int, Float> = emptyMap()
    ) : DspNode() {
        override fun withPosition(position: CanvasOffset) = copy(position = position)
    }

    /** Master bus — all 4 input buses converge here. */
    data class BusMaster(
        override val id: NodeId = UUID.randomUUID().toString(),
        override val position: CanvasOffset,
        val gainDb: Float = 0f,
        val pan: Float = 0f,
        val muted: Boolean = false
    ) : DspNode() {
        override fun withPosition(position: CanvasOffset) = copy(position = position)
    }

    /** Final output node — right edge of the graph. */
    data class Output(
        override val id: NodeId = UUID.randomUUID().toString(),
        override val position: CanvasOffset
    ) : DspNode() {
        override fun withPosition(position: CanvasOffset) = copy(position = position)
    }
}

/**
 * A connection (virtual cable) between two nodes.
 */
data class NodeConnection(
    val fromId: NodeId,
    val toId: NodeId,
    val busIndex: Int    // Which bus this connection belongs to (-1 for master→output)
)

/**
 * Current drag operation in progress.
 */
sealed class DragState {
    data class NodeDrag(
        val nodeId: NodeId,
        val startPosition: CanvasOffset,
        val currentOffset: CanvasOffset
    ) : DragState()

    data class ConnectionDrag(
        val fromNodeId: NodeId,
        val currentEndpoint: CanvasOffset
    ) : DragState()
}

/**
 * Complete canvas state — held as a single immutable snapshot in the ViewModel.
 */
data class CanvasState(
    val nodes: Map<NodeId, DspNode> = emptyMap(),
    val connections: List<NodeConnection> = emptyList(),
    val viewportOffset: CanvasOffset = CanvasOffset.Zero,
    val viewportScale: Float = 1f,
    val selectedNodeId: NodeId? = null,
    val deleteTargetId: NodeId? = null,
    val dragState: DragState? = null
) {
    val selectedNode: DspNode? get() = selectedNodeId?.let { nodes[it] }

    companion object {
        fun empty() = CanvasState()

        /** Scale constraints */
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3.0f

        /** Auto-layout spacing constants (in canvas dp units) */
        const val NODE_H_SPACING = 200f   // Horizontal gap between nodes
        const val NODE_V_SPACING = 140f   // Vertical gap between bus rows
        const val LEFT_MARGIN = 60f       // Left margin for bus input nodes
        const val PLUGIN_START_X = 280f   // Where plugin chain starts
    }
}

/**
 * Serializable layout data stored alongside DSP presets.
 */
@Serializable
data class CanvasLayout(
    val nodePositions: Map<String, CanvasOffset> = emptyMap(),
    val viewportOffset: CanvasOffset = CanvasOffset.Zero,
    val viewportScale: Float = 1f
)

/**
 * Enhanced preset wrapper — backward-compatible with legacy raw DSP JSON.
 */
@Serializable
data class EnhancedPresetState(
    val dspState: String,
    val canvasLayout: CanvasLayout? = null
)

/** Pixel dimensions for each node type (in canvas dp units). */
object NodeDimensions {
    const val PLUGIN_WIDTH = 160f
    const val PLUGIN_HEIGHT = 100f
    const val BUS_INPUT_WIDTH = 120f
    const val BUS_INPUT_HEIGHT = 80f
    const val MASTER_WIDTH = 140f
    const val MASTER_HEIGHT = 100f
    const val OUTPUT_WIDTH = 80f
    const val OUTPUT_HEIGHT = 60f
    const val PORT_RADIUS = 8f
    const val PORT_HIT_RADIUS = 24f
}
