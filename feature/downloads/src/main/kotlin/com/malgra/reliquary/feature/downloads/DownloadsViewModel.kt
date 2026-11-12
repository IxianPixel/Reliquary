package com.malgra.reliquary.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.download.Storage
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.data.rules.LibrarySyncScheduler
import com.malgra.reliquary.core.model.KeepOfflineRule
import kotlinx.coroutines.flow.first
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
    private val progress: ProgressRepository,
    settings: AppSettings,
    private val keepOffline: KeepOfflineRepository,
    private val librarySync: LibrarySyncScheduler,
    private val playlists: PlaylistRepository,
) : ViewModel() {

    val rules: StateFlow<List<KeepOfflineRule>> = keepOffline.rules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun typicalVideoBytes(rule: KeepOfflineRule): Long? = keepOffline.typicalVideoBytes(rule.kind, rule.sourceId)

    /** For a whole-playlist estimate; null if the playlist isn't cached. */
    suspend fun playlistSize(rule: KeepOfflineRule): Int? =
        if (rule.kind == KeepOfflineRule.Kind.Playlist) playlists.playlist(rule.sourceId).first()?.archivedCount else null

    fun saveRule(rule: KeepOfflineRule) {
        viewModelScope.launch { keepOffline.save(rule) }
    }

    fun deleteRule(rule: KeepOfflineRule) {
        viewModelScope.launch { keepOffline.delete(rule.id) }
    }

    /** Pushes progress and applies the rules now (once there's a connection). */
    fun syncNow() {
        progress.requestSync()
        librarySync.syncNow()
    }

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
