package com.malgra.reliquary.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val videos: VideoRepository) : ViewModel() {

    /** Cached rows show at once; [refreshContinueWatching] updates them from the server. */
    val continueWatching: StateFlow<List<Video>> = videos.continueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: Flow<PagingData<Video>> = videos.recentlyAdded().cachedIn(viewModelScope)

    init {
        refreshContinueWatching()
    }

    /** Failures are quiet: the carousel keeps its cached rows and "Recently added" reports the error. */
    fun refreshContinueWatching() {
        viewModelScope.launch { videos.refreshContinueWatching() }
    }
}
