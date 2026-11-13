package com.malgra.reliquary.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.model.KeepOfflineRule
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.model.Video
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(playlists: PlaylistRepository) : ViewModel() {
    val playlists: Flow<PagingData<Playlist>> = playlists.playlists().cachedIn(viewModelScope)
}

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel @AssistedInject constructor(
    @Assisted val playlistId: String,
    private val playlists: PlaylistRepository,
    videos: VideoRepository,
    private val keepOffline: KeepOfflineRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistId: String): PlaylistViewModel
    }

    val playlist: StateFlow<Playlist?> = playlists.playlist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** In the playlist's own order. */
    val videos: Flow<PagingData<Video>> = videos.playlistVideos(playlistId).cachedIn(viewModelScope)

    val keepOfflineRule: StateFlow<KeepOfflineRule?> = keepOffline.rule(KeepOfflineRule.Kind.Playlist, playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { playlists.refreshPlaylist(playlistId) }
    }

    suspend fun typicalVideoBytes(): Long? = keepOffline.typicalVideoBytes(KeepOfflineRule.Kind.Playlist, playlistId)

    fun saveRule(rule: KeepOfflineRule) {
        viewModelScope.launch { keepOffline.save(rule) }
    }

    fun deleteRule() {
        viewModelScope.launch { keepOffline.delete(KeepOfflineRule.idFor(KeepOfflineRule.Kind.Playlist, playlistId)) }
    }
}
