package com.malgra.reliquary.core.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.ui.VideoActions
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The video menu's actions, for every list in the app (see LocalVideoActions). */
class VideoActionsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    private val progress: ProgressRepository,
) : ViewModel(), VideoActions {

    override fun download(videoId: String) {
        viewModelScope.launch { downloads.start(videoId) }
    }

    override fun removeDownload(videoId: String) {
        viewModelScope.launch { downloads.remove(videoId) }
    }

    override fun setWatched(videoId: String, watched: Boolean) {
        viewModelScope.launch { progress.setWatched(videoId, watched) }
    }
}
