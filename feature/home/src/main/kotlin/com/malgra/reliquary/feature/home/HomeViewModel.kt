package com.malgra.reliquary.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.ConnectionMonitor
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videos: VideoRepository,
    connection: ConnectionMonitor,
    progress: ProgressRepository,
) : ViewModel() {

    /** Cached rows show at once; [refreshContinueWatching] updates them from the server. */
    val continueWatching: StateFlow<List<Video>> = videos.continueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: Flow<PagingData<Video>> = videos.recentlyAdded().cachedIn(viewModelScope)

    val onDevice: StateFlow<List<Video>> = videos.downloaded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isOffline: StateFlow<Boolean> = connection.isOffline

    val pendingSync: StateFlow<Int> = progress.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refreshContinueWatching()
    }

    /** Failures are quiet: the carousel keeps its cached rows and "Recently added" reports the error. */
    fun refreshContinueWatching() {
        viewModelScope.launch { videos.refreshContinueWatching() }
    }
}
