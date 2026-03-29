package tf.monochrome.android.ui.mixer.canvas.model

import androidx.compose.ui.graphics.Color
import tf.monochrome.android.audio.dsp.SnapinCategory
import tf.monochrome.android.audio.dsp.SnapinType

/**
 * Neon accent color palette for the node-based DSP canvas.
 * Each SnapinCategory gets a distinct, vibrant neon color for
 * header bars, port circles, and connection glow.
 */
object NodeColorScheme {

    // ── Category neon accents ───────────────────────────────────────────
    val Cyan       = Color(0xFF00E5FF)   // UTILITY
    val Green      = Color(0xFF00E676)   // EQ_FILTER
    val Amber      = Color(0xFFFF9100)   // DYNAMICS
    val Red        = Color(0xFFFF1744)   // DISTORTION
    val Magenta    = Color(0xFFE040FB)   // MODULATION
    val Blue       = Color(0xFF448AFF)   // SPACE

    // ── Bus / structural node accents ───────────────────────────────────
    val BusInput   = Color(0xFF80DEEA)   // Soft cyan for bus input nodes
    val Master     = Color(0xFFFFD740)   // Gold for master bus
    val OutputNode = Color(0xFFB0BEC5)   // Silver for output

    // ── Node surface colors ─────────────────────────────────────────────
    val NodeBackground     = Color(0xFF1E1E2E)   // Deep charcoal
    val NodeBackgroundHover = Color(0xFF262640)   // Slightly lighter on hover
    val NodeBorder         = Color(0xFF2A2A3E)   // Subtle border
    val NodeText           = Color(0xFFE0E0E0)   // Light text
    val NodeTextDim        = Color(0xFF9E9E9E)   // Dimmed text
    val BypassedOverlay    = Color(0x66000000)    // Semi-transparent overlay for bypassed plugins

    // ── Canvas ──────────────────────────────────────────────────────────
    val CanvasBackground   = Color(0xFF121218)    // Near-black canvas background
    val GridDot            = Color(0xFF2A2A3A)    // Subtle grid dots
    val SelectionGlow      = Color(0x40FFFFFF)    // Soft white glow for selection

    // ── Delete mode ─────────────────────────────────────────────────────
    val DeleteRed          = Color(0xFFFF1744)
    val DeleteBackground   = Color(0x40FF1744)

    fun categoryColor(category: SnapinCategory): Color = when (category) {
        SnapinCategory.UTILITY    -> Cyan
        SnapinCategory.EQ_FILTER  -> Green
        SnapinCategory.DYNAMICS   -> Amber
        SnapinCategory.DISTORTION -> Red
        SnapinCategory.MODULATION -> Magenta
        SnapinCategory.SPACE      -> Blue
    }

    fun nodeAccentColor(node: DspNode): Color = when (node) {
        is DspNode.BusInput  -> BusInput
        is DspNode.Plugin    -> node.snapinType.category.let { categoryColor(it) }
        is DspNode.BusMaster -> Master
        is DspNode.Output    -> OutputNode
    }

    /**
     * Glow color for connections — derived from the category of the source node.
     */
    fun connectionColor(busIndex: Int, sourceNode: DspNode?): Color = when (sourceNode) {
        is DspNode.Plugin    -> categoryColor(sourceNode.snapinType.category)
        is DspNode.BusInput  -> BusInput
        is DspNode.BusMaster -> Master
        else -> BusInput.copy(alpha = 0.5f)
    }
}
