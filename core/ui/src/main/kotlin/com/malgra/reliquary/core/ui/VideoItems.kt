package com.malgra.reliquary.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malgra.reliquary.core.model.Video

/** Watched videos are dimmed, as in the mockups. */
private const val WATCHED_ALPHA = 0.6f

@Composable
private fun VideoTitle(title: String, compact: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = if (compact) 14.sp else 15.sp,
            lineHeight = if (compact) 19.sp else 20.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Meta(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Watched", "5 min left", or null for an unstarted video. */
@Composable
fun watchStateText(video: Video): String? = when {
    video.watched -> stringResource(R.string.ui_watched)
    video.progress != null -> minutesLeftText(video.remaining)
    else -> null
}

/**
 * A video as a row: thumbnail on the left, title and details on the right.
 * [showChannel] is off inside a channel's own list.
 */
@Composable
fun VideoListItem(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChannel: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (video.watched) WATCHED_ALPHA else 1f)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        VideoThumbnail(
            url = video.thumbnailUrl,
            duration = video.duration,
            progress = video.progress,
            modifier = Modifier.size(width = 152.dp, height = 86.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VideoTitle(video.title)
            if (showChannel) Meta(metaLine(video.channelName, if (video.watched) watchStateText(video) else null))
            val second = when {
                !video.watched && video.progress != null -> watchStateText(video)
                !showChannel && video.watched -> metaLine(video.published?.let(::formatDate), watchStateText(video))
                !showChannel -> video.published?.let(::formatDate)
                else -> video.addedToArchive?.let { addedText(it) }
            }
            second?.let { Meta(it) }
        }
    }
}

/** A wide card for the "Continue watching" carousel. */
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Not clipped to the thumbnail's 20dp corners: that would shave the first
    // letter off the last line of text.
    Column(
        modifier = modifier
            .width(264.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoThumbnail(
            url = video.thumbnailUrl,
            duration = video.duration,
            progress = video.progress,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(width = 264.dp, height = 149.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            VideoTitle(video.title)
            Meta(metaLine(video.channelName, watchStateText(video)))
        }
    }
}

/** A playlist entry: its position, a smaller thumbnail, title and watch state. */
@Composable
fun NumberedVideoItem(
    number: Int,
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (video.watched) WATCHED_ALPHA else 1f)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        VideoThumbnail(
            url = video.thumbnailUrl,
            duration = video.duration,
            progress = video.progress,
            shape = RoundedCornerShape(10.dp),
            compact = true,
            modifier = Modifier.size(width = 112.dp, height = 63.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VideoTitle(video.title, compact = true)
            watchStateText(video)?.let { Meta(it) }
        }
    }
}
