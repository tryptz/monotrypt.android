package tf.monochrome.android.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VisualizerComponent(
    isPlaying: Boolean,
    sensitivity: Int, // 0-100
    brightness: Int, // 0-100
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    // Base animation for "movement"
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = knotsAnimation(isPlaying, sensitivity),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Number of bars
    val barCount = 32
    val barGap = 4.dp
    val density = LocalDensity.current
    val gapPx = with(density) { barGap.toPx() }

    // Brightness factor
    val alpha = (brightness / 100f).coerceIn(0.1f, 1f)
    val colorPrimary = Color.White.copy(alpha = alpha)
    val colorSecondary = Color.White.copy(alpha = alpha * 0.3f)

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        val barWidth = (size.width - (barCount - 1) * gapPx) / barCount
        val maxHeight = size.height

        for (i in 0 until barCount) {
            // Calculate height based on sine wave + noise
            val factor = sensitivity / 100f
            val baseHeight = (sin(phase + i * 0.5f) * 0.4f + 0.6f) * maxHeight * factor
            val noise = if (isPlaying) Random.nextFloat() * 0.2f * maxHeight * factor else 0f
            val finalHeight = (baseHeight + noise).coerceIn(4.dp.toPx(), maxHeight)

            val x = i * (barWidth + gapPx)
            val y = maxHeight - finalHeight

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(colorPrimary, colorSecondary),
                    startY = y,
                    endY = maxHeight
                ),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, finalHeight)
            )
        }
    }
}

@Composable
private fun knotsAnimation(isPlaying: Boolean, sensitivity: Int): DurationBasedAnimationSpec<Float> {
    val duration = if (isPlaying) {
        (2000 - (sensitivity * 15)).coerceIn(200, 2000)
    } else {
        5000
    }
    return tween(duration, easing = LinearEasing)
}
