package com.malgra.reliquary.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.download.Storage
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.model.DownloadedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    progress: ProgressRepository,
    settings: AppSettings,
) : ViewModel() {

    val wifiOnly: StateFlow<Boolean> = settings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Null until the first read, so the empty message doesn't flash. */
    val items: StateFlow<List<DownloadedVideo>?> = downloads.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val storage: StateFlow<Storage?> = downloads.storage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingSyncCount: StateFlow<Int> = progress.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun remove(videoId: String) {
        viewModelScope.launch { downloads.remove(videoId) }
    }
}
