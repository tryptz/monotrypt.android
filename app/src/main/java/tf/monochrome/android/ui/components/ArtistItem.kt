package tf.monochrome.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tf.monochrome.android.domain.model.Artist
import tf.monochrome.android.ui.theme.MonoDimens

@Composable
fun ArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(140.dp)
            .bounceClick(onClick = onClick),
        shape = MonoDimens.shapeMd,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = MonoDimens.cardAlpha)
    ) {
        Column(
            modifier = Modifier.padding(MonoDimens.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        if (artist.pictureUrl != null) {
            AsyncImage(
                model = artist.pictureUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(MonoDimens.shapeCircle)
            )
        } else {
            CoverImage(
                url = null,
                contentDescription = artist.name,
                size = 100.dp,
                cornerRadius = 50.dp
            )
        }
        Spacer(modifier = Modifier.height(MonoDimens.spacingSm))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
}
