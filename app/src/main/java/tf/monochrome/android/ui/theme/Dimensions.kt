package tf.monochrome.android.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Centralized design tokens for consistent spacing, shapes, and sizes
 * across the Monochrome UI.
 */
object MonoDimens {

    // ── Spacing ──────────────────────────────────────────────────────
    /** Minimal spacing between tightly related elements */
    val spacingXs = 4.dp
    /** Small spacing (icon-to-text gap, inner row padding) */
    val spacingSm = 8.dp
    /** Default spacing between elements */
    val spacingMd = 12.dp
    /** Standard horizontal page padding */
    val spacingLg = 16.dp
    /** Breathing room for section separators */
    val spacingXl = 24.dp

    // ── Corner Radii ─────────────────────────────────────────────────
    /** Small inline elements (cover thumbnails, chips) */
    val radiusSm = 6.dp
    /** Medium cards and containers */
    val radiusMd = 12.dp
    /** Large artwork and hero images */
    val radiusLg = 16.dp
    /** Pill-shaped buttons and search bars */
    val radiusPill = 24.dp

    // ── Shapes (reusable, no allocations per recompose) ─────────────
    val shapeSm = RoundedCornerShape(radiusSm)
    val shapeMd = RoundedCornerShape(radiusMd)
    val shapeLg = RoundedCornerShape(radiusLg)
    val shapePill = RoundedCornerShape(radiusPill)
    val shapeCircle = CircleShape

    // ── Icon Sizes ───────────────────────────────────────────────────
    /** Inline icons within text rows */
    val iconSm = 24.dp
    /** List-item leading icons (genre, folder, etc.) */
    val iconMd = 32.dp
    /** Cover thumbnails in lists */
    val iconLg = 48.dp
    /** Primary action buttons (play/pause) */
    val iconXl = 64.dp

    // ── Cover Art Sizes ──────────────────────────────────────────────
    /** Mini player artwork */
    val coverMini = 40.dp
    /** List item artwork */
    val coverList = 48.dp
    /** Album grid / artist card artwork */
    val coverCard = 160.dp
    /** Album detail hero artwork */
    val coverHero = 240.dp
    /** Now playing artwork */
    val coverPlayer = 300.dp

    // ── List Item Dimensions ─────────────────────────────────────────
    /** Standard horizontal padding for list items */
    val listItemPaddingH = 16.dp
    /** Standard vertical padding for list items */
    val listItemPaddingV = 10.dp
    /** Bottom padding to clear mini player / nav bar */
    val listBottomPadding = 80.dp

    // ── Card surface alpha ───────────────────────────────────────────
    /** Uniform alpha for all card/surface backgrounds */
    const val cardAlpha = 0.85f

    // ── Glass effect tokens ─────────────────────────────────────────
    /** Tint layer opacity for glass cards */
    const val glassAlpha = 0.25f
    /** Border glow opacity for glass cards */
    const val glassBorderAlpha = 0.15f
    /** Thin luminous border width */
    val glassBorderWidth = 0.5.dp
    /** Backdrop blur radius for glass surfaces */
    val glassBlurRadius = 20.dp
    /** Soft shadow elevation for glass cards */
    val glassElevation = 4.dp
}
