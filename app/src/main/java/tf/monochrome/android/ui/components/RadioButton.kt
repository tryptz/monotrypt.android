package tf.monochrome.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tf.monochrome.android.radio.RadioState

@Composable
fun RadioButton(
    state: RadioState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state is RadioState.Active
    val pulse by rememberInfiniteTransition(label = "radioPulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radioPulseAlpha",
    )
    val icon: ImageVector = if (active) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq

    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = state !is RadioState.Loading,
    ) {
        if (state is RadioState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = "Start radio",
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (active) pulse else 1f),
            )
        }
    }
}
