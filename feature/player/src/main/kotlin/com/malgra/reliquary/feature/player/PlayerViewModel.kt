package com.malgra.reliquary.feature.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.Download
import com.malgra.reliquary.core.model.Video
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What to load into the player: the item and where to start. */
data class PlaybackRequest(val item: MediaItem, val startPositionMs: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel @AssistedInject constructor(
    @Assisted val videoId: String,
    private val videos: VideoRepository,
    private val progress: ProgressRepository,
    private val downloads: DownloadRepository,
    channels: ChannelRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(videoId: String): PlayerViewModel
    }

    val video: StateFlow<Video?> = videos.video(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channel: StateFlow<Channel?> = video.map { it?.channelId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isNullOrEmpty()) flowOf(null) else channels.channel(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Fresh details and server watch state; the cached row plays meanwhile (or offline).
        viewModelScope.launch { videos.refreshVideo(videoId) }
    }

    /** Null if the video isn't cached and the server can't be reached. */
    suspend fun playbackRequest(): PlaybackRequest? {
        val video = withTimeoutOrNull(LOAD_TIMEOUT_MS) { this@PlayerViewModel.video.filterNotNull().first() }
            ?: return null
        // A downloaded copy plays without the server.
        val local = downloads.localFile(videoId)?.let(Uri::fromFile)
        return PlaybackRequest(video.toMediaItem(local), progress.resumePosition(videoId).inWholeMilliseconds)
    }

    val download: StateFlow<Download?> = downloads.download(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startDownload() {
        viewModelScope.launch { downloads.start(videoId) }
    }

    fun removeDownload() {
        viewModelScope.launch { downloads.remove(videoId) }
    }

    fun setWatched(watched: Boolean) {
        viewModelScope.launch { progress.setWatched(videoId, watched) }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}

/** [localFile] replaces the server URL when the video is downloaded. */
internal fun Video.toMediaItem(localFile: Uri? = null): MediaItem {
    val uri: Uri = localFile ?: mediaUrl.toUri()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        // The URI doesn't survive the trip to the service; PlaybackService restores it from here.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(channelName)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}
