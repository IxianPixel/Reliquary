package com.malgra.reliquary.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.ChannelTabs
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.model.WatchFilter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChannelTab(val videoType: VideoType?) {
    Videos(VideoType.Videos),
    Streams(VideoType.Streams),
    Shorts(VideoType.Shorts),
    Playlists(null),
}

/** The tabs a channel shows, in order. Before `/nav/` answers, only Videos. */
internal fun ChannelTabs?.available(): List<ChannelTab> {
    if (this == null) return listOf(ChannelTab.Videos)
    return buildList {
        if (videos) add(ChannelTab.Videos)
        if (streams) add(ChannelTab.Streams)
        if (shorts) add(ChannelTab.Shorts)
        if (playlists) add(ChannelTab.Playlists)
    }.ifEmpty { listOf(ChannelTab.Videos) }
}

/** [videoTab] is the last video tab, so the Playlists tab doesn't reload the video list. */
data class ChannelSelection(
    val tab: ChannelTab = ChannelTab.Videos,
    val videoTab: ChannelTab = ChannelTab.Videos,
    val filter: WatchFilter = WatchFilter.All,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChannelViewModel.Factory::class)
class ChannelViewModel @AssistedInject constructor(
    @Assisted val channelId: String,
    private val channels: ChannelRepository,
    videos: VideoRepository,
    playlists: PlaylistRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(channelId: String): ChannelViewModel
    }

    val channel: StateFlow<Channel?> = channels.channel(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selection = MutableStateFlow(ChannelSelection())
    val selection: StateFlow<ChannelSelection> = _selection.asStateFlow()

    val videos: Flow<PagingData<Video>> = _selection
        .map { (it.videoTab.videoType ?: VideoType.Videos) to it.filter }
        .distinctUntilChanged()
        .flatMapLatest { (type, filter) -> videos.channelVideos(channelId, type, filter) }
        .cachedIn(viewModelScope)

    val playlists: Flow<PagingData<Playlist>> = playlists.playlists(channelId).cachedIn(viewModelScope)

    /** Shown under the name once the full video list has loaded once. */
    val videoCount: StateFlow<Int?> = videos.channelVideoCount(channelId, VideoType.Videos, WatchFilter.All)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { channels.refreshChannel(channelId) }
    }

    fun selectTab(tab: ChannelTab) = _selection.update {
        it.copy(tab = tab, videoTab = if (tab.videoType != null) tab else it.videoTab)
    }

    fun selectFilter(filter: WatchFilter) = _selection.update { it.copy(filter = filter) }
}
