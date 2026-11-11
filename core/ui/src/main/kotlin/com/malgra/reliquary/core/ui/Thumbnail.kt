package com.malgra.reliquary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.malgra.reliquary.core.designsystem.theme.OverlayColors
import kotlin.time.Duration

/**
 * A 16:9 video thumbnail with its duration and, when the video is part
 * watched, a progress bar along the bottom. Decorative: the row around it
 * carries the text for screen readers.
 */
@Composable
fun VideoThumbnail(
    url: String?,
    duration: Duration,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (duration > Duration.ZERO) {
            Text(
                text = formatDuration(duration),
                color = Color.White,
                fontSize = if (compact) 10.sp else 11.sp,
                lineHeight = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (compact) 4.dp else 6.dp, bottom = if (progress != null) 8.dp else 6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(OverlayColors.ProgressTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(OverlayColors.Progress),
                )
            }
        }
    }
}

/** A round channel picture, or the channel's first letter while it loads or if there is none. */
@Composable
fun ChannelAvatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A decorative image from the server, loaded through the signed-in client. */
@Composable
fun ServerImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier)
}
