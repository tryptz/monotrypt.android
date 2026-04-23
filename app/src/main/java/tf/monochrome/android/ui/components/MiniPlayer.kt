package tf.monochrome.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.ui.theme.MonoDimens
import kotlin.math.abs

@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    if (track == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                hazeState = hazeState,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .pointerInput(Unit) {
                var totalHorizontalDrag = 0f
                var totalVerticalDrag = 0f
                detectDragGestures(
                    onDragStart = {
                        totalHorizontalDrag = 0f
                        totalVerticalDrag = 0f
                    },
                    onDragEnd = {
                        if (abs(totalVerticalDrag) > abs(totalHorizontalDrag) && totalVerticalDrag < -50f) {
                            // Swipe Up
                            onClick()
                        } else if (abs(totalVerticalDrag) > abs(totalHorizontalDrag) && totalVerticalDrag > 50f) {
                            // Swipe Down (Collapse logic, if any, could go here)
                        } else if (totalHorizontalDrag > 50f) {
                            onSkipPreviousClick()
                        } else if (totalHorizontalDrag < -50f) {
                            onSkipNextClick()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalHorizontalDrag += dragAmount.x
                        totalVerticalDrag += dragAmount.y
                    }
                )
            }
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progressProvider().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline
            )
            Row(
                modifier = Modifier.padding(horizontal = MonoDimens.spacingMd, vertical = MonoDimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = track.coverUrl,
                    contentDescription = track.title,
                    size = MonoDimens.coverMini,
                    cornerRadius = MonoDimens.radiusSm
                )
                Spacer(modifier = Modifier.width(MonoDimens.spacingMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onSkipNextClick) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip next",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
