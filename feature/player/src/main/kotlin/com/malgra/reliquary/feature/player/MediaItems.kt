package com.malgra.reliquary.feature.player

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.malgra.reliquary.core.model.SponsorSegment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.milliseconds

// PlayerItem as media3 carries it (Android only).

private const val EXTRA_PLAYLIST_ID = "com.malgra.reliquary.playlistId"

/** The playlist this item was queued from, if any. */
internal val MediaItem.playlistId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_PLAYLIST_ID)

internal fun MediaSource.toUri(): Uri = when (this) {
    is MediaSource.Remote -> url.toUri()
    is MediaSource.Local -> Uri.fromFile(file)
}

internal fun Uri.toMediaSource(): MediaSource =
    if (scheme == "file") MediaSource.Local(toFile()) else MediaSource.Remote(toString())

internal fun PlayerItem.toMediaItem(): MediaItem {
    val uri = source.toUri()
    val tracks = subtitles.map { SubtitleTrack(it.key, it.source.toUri(), it.language, it.label) }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(uri)
        .setSubtitleConfigurations(tracks.map { it.toConfiguration() })
        // The URI, subtitles and SponsorBlock segments don't survive the trip to the service as they are;
        // PlaybackService restores them from here.
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .setExtras(tracks.toBundle().putSponsorSegments(sponsorSegments))
                .build(),
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(channelName)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .apply { if (playlistId != null) setExtras(Bundle().apply { putString(EXTRA_PLAYLIST_ID, playlistId) }) }
                .build(),
        )
        .build()
}

/** The item [toMediaItem] made, on either side of the session. Null if it has no URI. */
internal fun MediaItem.toPlayerItem(): PlayerItem? {
    val uri = requestMetadata.mediaUri ?: localConfiguration?.uri ?: return null
    return PlayerItem(
        videoId = mediaId,
        source = uri.toMediaSource(),
        title = mediaMetadata.title?.toString().orEmpty(),
        channelName = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        playlistId = playlistId,
        subtitles = subtitleTracksFromRequest().map { SubtitleSource(it.id, it.uri.toMediaSource(), it.language, it.label) },
        sponsorSegments = sponsorSegments(),
    )
}

private const val KEY_STARTS = "com.malgra.reliquary.segmentStarts"
private const val KEY_ENDS = "com.malgra.reliquary.segmentEnds"
private const val KEY_CATEGORIES = "com.malgra.reliquary.segmentCategories"

/** Adds [segments] to a media item's request extras, which reach the playback service. */
internal fun Bundle.putSponsorSegments(segments: List<SponsorSegment>): Bundle = apply {
    putLongArray(KEY_STARTS, segments.map { it.start.inWholeMilliseconds }.toLongArray())
    putLongArray(KEY_ENDS, segments.map { it.end.inWholeMilliseconds }.toLongArray())
    putStringArrayList(KEY_CATEGORIES, ArrayList(segments.map { it.category }))
}

/** The segments a bundle made by [putSponsorSegments] holds. */
internal fun Bundle.sponsorSegments(): List<SponsorSegment> {
    val starts = getLongArray(KEY_STARTS) ?: return emptyList()
    val ends = getLongArray(KEY_ENDS) ?: return emptyList()
    val categories = getStringArrayList(KEY_CATEGORIES).orEmpty()
    return starts.indices.mapNotNull { i ->
        val end = ends.getOrNull(i) ?: return@mapNotNull null
        SponsorSegment(starts[i].milliseconds, end.milliseconds, categories.getOrElse(i) { "" })
    }
}

/** The segments an item was sent with. */
internal fun MediaItem.sponsorSegments(): List<SponsorSegment> = requestMetadata.extras?.sponsorSegments().orEmpty()

/** Skips the service made, passed on to the player screen for its Undo snackbar. */
internal object SponsorSkips {
    const val ACTION = "com.malgra.reliquary.SPONSOR_SKIPPED"

    private val skips = MutableSharedFlow<SponsorSegment>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<SponsorSegment> = skips

    fun toBundle(segment: SponsorSegment): Bundle = Bundle().putSponsorSegments(listOf(segment))

    fun received(args: Bundle) {
        args.sponsorSegments().firstOrNull()?.let { skips.tryEmit(it) }
    }
}
