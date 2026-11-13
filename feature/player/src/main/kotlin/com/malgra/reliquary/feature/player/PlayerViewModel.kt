package com.malgra.reliquary.feature.player

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.Comment
import com.malgra.reliquary.core.model.Download
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Video
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What to load into the player: the queue, which item to start with, and where in it. */
data class PlaybackRequest(val items: List<MediaItem>, val startIndex: Int, val startPositionMs: Long)

/** A video's comments as the screen sees them: nothing shown until some have loaded. */
sealed interface CommentsState {
    data object Loading : CommentsState
    /** Empty when the server saved none. */
    data class Loaded(val comments: List<Comment>) : CommentsState {
        /** Comments and replies together. */
        val total: Int = comments.sumOf { 1 + it.replies.size }
    }
    data class Failed(val error: LoadError) : CommentsState
}

/** The playlist being played as a queue, in order. */
data class PlayQueue(val playlistId: String, val name: String, val videos: List<Video>)

/**
 * The player screen's state. It opens on [videoId] (in [playlistId]'s queue if
 * given) and then follows whatever the player moves on to, via [onNowPlaying].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel @AssistedInject constructor(
    @Assisted("videoId") val videoId: String,
    @Assisted("playlistId") val playlistId: String?,
    private val videos: VideoRepository,
    private val progress: ProgressRepository,
    private val downloads: DownloadRepository,
    channels: ChannelRepository,
    playlists: PlaylistRepository,
    private val settings: AppSettings,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("videoId") videoId: String, @Assisted("playlistId") playlistId: String?): PlayerViewModel
    }

    /** The video the screen shows: the one opened, then whatever is playing. */
    private val shownId = MutableStateFlow(videoId)
    private val queuePlaylistId = MutableStateFlow(playlistId)

    val video: StateFlow<Video?> = shownId.flatMapLatest { videos.video(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channel: StateFlow<Channel?> = video.map { it?.channelId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isNullOrEmpty()) flowOf(null) else channels.channel(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val download: StateFlow<Download?> = shownId.flatMapLatest { downloads.download(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val queue: StateFlow<PlayQueue?> = queuePlaylistId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(playlists.playlist(id), videos.playlistListing(id)) { playlist, list ->
                    PlayQueue(id, playlist?.name.orEmpty(), list)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Videos like the one showing, from the server; empty while loading, offline, or if it has none. */
    val similar: StateFlow<List<Video>> = shownId
        .flatMapLatest { id ->
            flow {
                emit(emptyList())
                emit((videos.similar(id) as? LoadResult.Ok)?.value.orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The showing video's saved comments, from the server. */
    val comments: StateFlow<CommentsState> = shownId
        .flatMapLatest { id ->
            flow {
                emit(CommentsState.Loading)
                emit(
                    when (val result = videos.comments(id)) {
                        is LoadResult.Ok -> CommentsState.Loaded(result.value)
                        is LoadResult.Failed -> CommentsState.Failed(result.error)
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommentsState.Loading)

    /** Fresh details and server watch state; the cached row is shown meanwhile (or offline). */
    private val refresh = viewModelScope.async { videos.refreshVideo(videoId) }

    /** The player moved to [id] (from [playlistId]'s queue, if any). */
    fun onNowPlaying(id: String, playlistId: String?) {
        if (playlistId != null) queuePlaylistId.value = playlistId
        if (id == shownId.value) return
        shownId.value = id
        viewModelScope.launch { videos.refreshVideo(id) }
    }

    /**
     * The whole playlist when opened from one, else just this video. Null if the
     * video isn't cached and the server can't be reached.
     */
    suspend fun playbackRequest(): PlaybackRequest? {
        val start = progress.resumePosition(videoId).inWholeMilliseconds
        if (playlistId != null) {
            val list = videos.playlistQueue(playlistId)
            val index = list.indexOfFirst { it.id == videoId }
            if (index >= 0) {
                val items = list.map { it.toMediaItem(downloads.localFile(it.id)?.let(Uri::fromFile), playlistId, downloads.localSubtitles(it.id)) }
                return PlaybackRequest(items, index, start)
            }
        }
        // The cached row may predate the server's subtitles, so give the refresh a moment first.
        withTimeoutOrNull(REFRESH_WAIT_MS) { refresh.await() }
        val video = withTimeoutOrNull(LOAD_TIMEOUT_MS) { videos.get(videoId) ?: this@PlayerViewModel.video.filterNotNull().first() }
            ?: return null
        // A downloaded copy plays without the server.
        val local = downloads.localFile(videoId)?.let(Uri::fromFile)
        return PlaybackRequest(listOf(video.toMediaItem(local, null, downloads.localSubtitles(videoId))), 0, start)
    }

    /** The subtitle language to start with; null means off. */
    suspend fun subtitleLanguage(): String? = settings.subtitleLanguage.first()

    /** Remembers the choice for the next video. */
    fun rememberSubtitleLanguage(language: String?) {
        viewModelScope.launch { settings.setSubtitleLanguage(language) }
    }

    fun startDownload() {
        val id = shownId.value
        viewModelScope.launch { downloads.start(id) }
    }

    fun removeDownload() {
        val id = shownId.value
        viewModelScope.launch { downloads.remove(id) }
    }

    fun setWatched(watched: Boolean) {
        val id = shownId.value
        viewModelScope.launch { progress.setWatched(id, watched) }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L

        /** How long playback waits for fresh details before using the cached ones. */
        const val REFRESH_WAIT_MS = 3_000L
    }
}

private const val EXTRA_PLAYLIST_ID = "com.malgra.reliquary.playlistId"

/** The playlist this item was queued from, if any. */
internal val MediaItem.playlistId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_PLAYLIST_ID)

/**
 * [localFile] and [localSubtitles] (by track key) replace the server's copies
 * when the video is downloaded.
 */
internal fun Video.toMediaItem(
    localFile: Uri? = null,
    playlistId: String? = null,
    localSubtitles: Map<String, File> = emptyMap(),
): MediaItem {
    val uri: Uri = localFile ?: mediaUrl.toUri()
    val tracks = subtitles.map { subtitle ->
        SubtitleTrack(
            id = subtitle.key,
            uri = localSubtitles[subtitle.key]?.let(Uri::fromFile) ?: subtitle.url.toUri(),
            language = subtitle.language,
            label = subtitle.name,
        )
    }
    return MediaItem.Builder()
        .setMediaId(id)
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
