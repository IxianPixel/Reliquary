package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.database.entity.PlaylistEntity
import com.malgra.reliquary.core.database.entity.VideoEntity
import com.malgra.reliquary.core.database.entity.VideoWithPending
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.ChannelTabs
import com.malgra.reliquary.core.model.Comment
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.model.SponsorSegment
import com.malgra.reliquary.core.model.Subtitle
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.network.model.ChannelDto
import com.malgra.reliquary.core.network.model.CommentDto
import com.malgra.reliquary.core.network.model.PlaylistDto
import com.malgra.reliquary.core.network.model.SponsorBlockDto
import com.malgra.reliquary.core.network.model.VideoDto
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

// Server -> cache. Media paths are server-relative ("/media/..."), so they are
// resolved against the base URL here and stored absolute.

internal fun VideoDto.toEntity(baseUrl: HttpUrl, now: Long) = VideoEntity(
    id = youtubeId,
    title = title,
    channelId = channel?.channelId.orEmpty(),
    channelName = channel?.channelName.orEmpty(),
    description = description,
    type = vidType,
    durationSeconds = player.duration.toLong(),
    publishedEpochSeconds = parseServerDate(published),
    addedEpochSeconds = dateDownloaded.takeIf { it > 0 },
    mediaUrl = baseUrl.absolute(mediaUrl).orEmpty(),
    mediaSizeBytes = mediaSize,
    thumbnailUrl = baseUrl.absolute(vidThumbUrl),
    watched = player.watched,
    watchedEpochSeconds = player.watchedDate,
    positionSeconds = player.position ?: 0.0,
    fetchedAtMillis = now,
    subtitlesJson = subtitles
        .mapNotNull { dto ->
            baseUrl.absolute(dto.mediaUrl)?.let { url -> StoredSubtitle(dto.lang, dto.name, url, dto.source == "auto") }
        }
        .takeIf { it.isNotEmpty() }
        ?.let { rowJson.encodeToString(ListSerializer(StoredSubtitle.serializer()), it) },
    sponsorSegmentsJson = sponsorblock?.storedSegments()
        ?.takeIf { it.isNotEmpty() }
        ?.let { rowJson.encodeToString(ListSerializer(StoredSegment.serializer()), it) },
)

/** Only segments meant to be skipped (not highlights or chapters), with a sensible range, in order. */
private fun SponsorBlockDto.storedSegments(): List<StoredSegment> =
    if (!isEnabled) {
        emptyList()
    } else {
        segments
            .filter { it.actionType == "skip" && it.segment.size == 2 && it.segment[1] > it.segment[0] }
            .map { StoredSegment(it.segment[0], it.segment[1], it.category) }
            .sortedBy { it.start }
    }

/** How SponsorBlock segments are kept in the video row; times in seconds. */
@Serializable
private data class StoredSegment(val start: Double, val end: Double, val category: String)

private fun parseSegments(json: String?): List<SponsorSegment> = json?.let {
    runCatching { rowJson.decodeFromString(ListSerializer(StoredSegment.serializer()), it) }.getOrNull()
}.orEmpty().map { SponsorSegment(it.start.seconds, it.end.seconds, it.category) }

/** How subtitles are kept in the video row. */
@Serializable
private data class StoredSubtitle(val lang: String, val name: String, val url: String, val auto: Boolean)

/** For the JSON kept in cache rows (subtitles, SponsorBlock segments). */
private val rowJson = Json { ignoreUnknownKeys = true }

private fun parseSubtitles(json: String?): List<Subtitle> = json?.let {
    runCatching { rowJson.decodeFromString(ListSerializer(StoredSubtitle.serializer()), it) }.getOrNull()
}.orEmpty().map { Subtitle(it.lang, it.name, it.url, it.auto) }

/**
 * Comments as threads: top-level comments in the server's order, each with its
 * replies oldest first. Replies arrive nested, but are also accepted flat (by
 * `comment_parent`), and a reply whose comment is missing is shown on its own.
 */
internal fun List<CommentDto>.toComments(): List<Comment> {
    val all = flatMap { listOf(it) + it.replies }
    val ids = all.mapTo(HashSet()) { it.id }
    val repliesByParent = all.filter { it.isReplyTo(ids) }.groupBy { it.parent }
    return all.filterNot { it.isReplyTo(ids) }.distinctBy { it.id }.map { top ->
        top.toComment(
            repliesByParent[top.id].orEmpty().distinctBy { it.id }
                .sortedBy { it.timestamp ?: Long.MAX_VALUE }
                .map { it.toComment(emptyList()) },
        )
    }
}

private fun CommentDto.isReplyTo(ids: Set<String>) = parent != null && parent != "root" && parent in ids

private fun CommentDto.toComment(replies: List<Comment>) = Comment(
    id = id,
    author = author.orEmpty(),
    text = text.orEmpty(),
    published = timestamp?.let(Instant::fromEpochSeconds),
    likeCount = likeCount,
    byUploader = authorIsUploader,
    favorited = isFavorited,
    replies = replies,
)

/** [previous] keeps the tab flags, which only `/nav/` provides. */
internal fun ChannelDto.toEntity(baseUrl: HttpUrl, now: Long, previous: ChannelEntity?) = ChannelEntity(
    id = channelId,
    name = channelName,
    description = channelDescription,
    thumbnailUrl = baseUrl.absolute(channelThumbUrl),
    bannerUrl = baseUrl.absolute(channelBannerUrl),
    subscribed = channelSubscribed,
    subscriberCount = channelSubs,
    hasVideos = previous?.hasVideos,
    hasStreams = previous?.hasStreams,
    hasShorts = previous?.hasShorts,
    hasPlaylists = previous?.hasPlaylists,
    fetchedAtMillis = now,
)

internal fun PlaylistDto.toEntity(baseUrl: HttpUrl, now: Long) = PlaylistEntity(
    id = playlistId,
    name = playlistName,
    channelId = playlistChannelId.ifEmpty { null },
    channelName = playlistChannel.ifEmpty { null },
    description = playlistDescription,
    thumbnailUrl = baseUrl.absolute(playlistThumbnail),
    subscribed = playlistSubscribed,
    entryCount = playlistEntries.size,
    archivedCount = playlistEntries.count { it.downloaded },
    fetchedAtMillis = now,
)

private fun HttpUrl.absolute(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { resolve(it)?.toString() }

/** TA sends ISO 8601 with an offset ("2021-01-01T00:00:00+00:00"); older data may be a bare date. */
internal fun parseServerDate(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toEpochSecond() }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull()
}

// Cache -> app

internal fun VideoWithPending.toModel(): Video = video.toModel(pending).copy(
    download = download?.let { row -> DownloadStatus.entries.firstOrNull { it.name == row.state } },
).withLocalThumbnail(download?.thumbnailPath)

/** Points the thumbnail at the copy saved with the download, when there is one, so it shows offline. */
internal fun Video.withLocalThumbnail(path: String?): Video =
    if (path == null) this else copy(thumbnailUrl = "file://$path")

/** Local progress that hasn't synced wins over the server's, so the app shows where you are. */
internal fun VideoEntity.toModel(pending: PendingProgressEntity? = null) = Video(
    id = id,
    title = title,
    channelId = channelId,
    channelName = channelName,
    description = description,
    type = VideoType.fromApi(type),
    duration = durationSeconds.seconds,
    published = publishedEpochSeconds?.let(Instant::fromEpochSeconds),
    addedToArchive = addedEpochSeconds?.let(Instant::fromEpochSeconds),
    mediaUrl = mediaUrl,
    mediaSizeBytes = mediaSizeBytes,
    thumbnailUrl = thumbnailUrl,
    watched = pending?.watched ?: watched,
    position = (pending?.positionSeconds ?: positionSeconds).seconds,
    syncPending = pending != null,
    subtitles = parseSubtitles(subtitlesJson),
    sponsorSegments = parseSegments(sponsorSegmentsJson),
)

internal fun ChannelEntity.toModel(): Channel = Channel(
    id = id,
    name = name,
    description = description,
    thumbnailUrl = thumbnailUrl,
    bannerUrl = bannerUrl,
    subscribed = subscribed,
    subscriberCount = subscriberCount,
    tabs = hasVideos?.let { videos ->
        ChannelTabs(
            videos = videos,
            streams = hasStreams == true,
            shorts = hasShorts == true,
            playlists = hasPlaylists == true,
        )
    },
)

internal fun PlaylistEntity.toModel() = Playlist(
    id = id,
    name = name,
    channelId = channelId,
    channelName = channelName,
    description = description,
    thumbnailUrl = thumbnailUrl,
    subscribed = subscribed,
    entryCount = entryCount,
    archivedCount = archivedCount,
)
