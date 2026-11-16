package com.malgra.reliquary.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.PlaylistRepository
import com.malgra.reliquary.core.data.library.PlaylistRepository.SubscribeResult
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.data.session.AccountRepository
import com.malgra.reliquary.core.model.KeepOfflineRule
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.model.Video
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    account: AccountRepository,
) : ViewModel() {
    val playlists: Flow<PagingData<Playlist>> = repository.playlists().cachedIn(viewModelScope)

    /** Adding playlists needs an admin account on the server. */
    val isAdmin: StateFlow<Boolean> = account.isAdmin

    private val _addSheet = MutableStateFlow<AddSheetState?>(null)

    /** The add sheet, or null when it's closed. */
    val addSheet: StateFlow<AddSheetState?> = _addSheet.asStateFlow()

    private val _events = Channel<AddEvent>(Channel.BUFFERED)
    val events: Flow<AddEvent> = _events.receiveAsFlow()

    fun openAddSheet() {
        _addSheet.value = AddSheetState()
    }

    fun closeAddSheet() {
        _addSheet.value = null
    }

    fun subscribe(input: String) {
        if (_addSheet.value?.busy == true) return
        _addSheet.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.subscribe(input)) {
                is SubscribeResult.Started -> {
                    _addSheet.value = null
                    _events.send(AddEvent.Subscribing)
                    // Runs on after the sheet closes; the list refreshes once the server has it.
                    _events.send(if (repository.awaitSubscribed(result.playlistId)) AddEvent.Added else AddEvent.StillAdding)
                }
                SubscribeResult.NotAPlaylist -> _addSheet.update { it?.copy(busy = false, error = AddError.NotAPlaylist) }
                is SubscribeResult.Failed -> _addSheet.update { it?.copy(busy = false, error = result.error.toAddError()) }
            }
        }
    }

    data class AddSheetState(val busy: Boolean = false, val error: AddError? = null)

    enum class AddError { NotAPlaylist, Offline, ServerError }

    sealed interface AddEvent {
        /** The server has started adding it. */
        data object Subscribing : AddEvent

        /** It's on the server and in the cache; the list should refresh. */
        data object Added : AddEvent

        /** Still not there after a short wait; it'll show on a later refresh. */
        data object StillAdding : AddEvent
    }
}

private fun LoadError.toAddError() = when (this) {
    LoadError.Offline -> PlaylistsViewModel.AddError.Offline
    LoadError.ServerError, LoadError.Unexpected -> PlaylistsViewModel.AddError.ServerError
}

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel @AssistedInject constructor(
    @Assisted val playlistId: String,
    private val playlists: PlaylistRepository,
    videos: VideoRepository,
    private val keepOffline: KeepOfflineRepository,
    account: AccountRepository,
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

    /** Subscribing needs an admin account on the server. */
    val isAdmin: StateFlow<Boolean> = account.isAdmin

    private val _changingSubscription = MutableStateFlow(false)
    val changingSubscription: StateFlow<Boolean> = _changingSubscription.asStateFlow()

    private val _subscriptionErrors = Channel<LoadError>(Channel.BUFFERED)
    val subscriptionErrors: Flow<LoadError> = _subscriptionErrors.receiveAsFlow()

    init {
        refresh()
    }

    /** Room updates [playlist] with the server's answer. */
    fun setSubscribed(subscribed: Boolean) {
        if (_changingSubscription.value) return
        _changingSubscription.value = true
        viewModelScope.launch {
            playlists.setSubscribed(playlistId, subscribed)?.let { _subscriptionErrors.send(it) }
            _changingSubscription.value = false
        }
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
