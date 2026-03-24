package tf.monochrome.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import tf.monochrome.android.ui.theme.MonoDimens

/**
 * Applies a liquid glass (glassmorphism) effect to a composable.
 *
 * When [hazeState] is provided, real backdrop blur is applied (best on API 31+).
 * When null, only the visual glass treatment (tint + border + gradient) is applied,
 * which is suitable for list items where blur would hurt scroll performance.
 */
fun Modifier.liquidGlass(
    hazeState: HazeState? = null,
    shape: Shape = MonoDimens.shapeMd,
    tintAlpha: Float = MonoDimens.glassAlpha,
    borderAlpha: Float = MonoDimens.glassBorderAlpha,
    borderWidth: Dp = MonoDimens.glassBorderWidth,
    blurRadius: Dp = MonoDimens.glassBlurRadius,
    showRefraction: Boolean = true
) = composed {
    val tintColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val luminousBorderBrush = Brush.linearGradient(
        colors = listOf(
            borderColor.copy(alpha = borderAlpha * 2f),
            borderColor.copy(alpha = borderAlpha * 0.5f),
            borderColor.copy(alpha = borderAlpha * 1.5f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    val refractionBrush = if (showRefraction) {
        val refractionColor = if (isLight) Color.Black else Color.White
        Brush.linearGradient(
            colors = listOf(
                refractionColor.copy(alpha = 0.04f),
                Color.Transparent,
                refractionColor.copy(alpha = 0.02f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else null

    var modifier = this

    // Layer 1: Backdrop blur (only when hazeState is provided)
    if (hazeState != null) {
        modifier = modifier.hazeEffect(state = hazeState) {
            this.blurRadius = blurRadius
            this.tints = listOf(
                HazeTint(color = tintColor.copy(alpha = tintAlpha))
            )
        }
    }

    // Layer 2: Clip + semi-transparent fill
    modifier = modifier
        .clip(shape)
        .background(
            color = tintColor.copy(alpha = tintAlpha),
            shape = shape
        )

    // Layer 3: Luminous border
    modifier = modifier.border(
        width = borderWidth,
        brush = luminousBorderBrush,
        shape = shape
    )

    // Layer 4: Refraction gradient overlay
    if (refractionBrush != null) {
        modifier = modifier.background(
            brush = refractionBrush,
            shape = shape
        )
    }

    modifier
}
