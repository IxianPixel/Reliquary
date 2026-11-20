package com.malgra.reliquary.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.ChannelReference
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.data.session.AccountRepository
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.LoadError
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChannelsViewModel @Inject constructor(
    private val repository: ChannelRepository,
    account: AccountRepository,
) : ViewModel() {
    val channels: Flow<PagingData<Channel>> = repository.channels().cachedIn(viewModelScope)

    /** Subscribing needs an admin account on the server. */
    val isAdmin: StateFlow<Boolean> = account.isAdmin

    private val _addSheet = MutableStateFlow<AddSheetState?>(null)

    /** The add sheet, or null when it's closed. */
    val addSheet: StateFlow<AddSheetState?> = _addSheet.asStateFlow()

    private val _events = EventChannel<AddEvent>(EventChannel.BUFFERED)
    val events: Flow<AddEvent> = _events.receiveAsFlow()

    fun openAddSheet() {
        _addSheet.value = AddSheetState()
    }

    fun closeAddSheet() {
        _addSheet.value = null
    }

    fun subscribe(input: String) {
        if (_addSheet.value?.busy == true) return
        val reference = ChannelReference.parse(input)
        if (reference == null) {
            _addSheet.update { it?.copy(error = AddError.NotAChannel) }
            return
        }
        _addSheet.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = repository.subscribe(reference)
            if (error != null) {
                _addSheet.update { it?.copy(busy = false, error = error.toAddError()) }
                return@launch
            }
            _addSheet.value = null
            _events.send(AddEvent.Subscribing)
            // Runs on after the sheet closes; the list refreshes once the server has it.
            _events.send(if (repository.awaitSubscribed(reference) != null) AddEvent.Added else AddEvent.StillAdding)
        }
    }

    data class AddSheetState(val busy: Boolean = false, val error: AddError? = null)

    enum class AddError { NotAChannel, Offline, ServerError }

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
    LoadError.Offline -> ChannelsViewModel.AddError.Offline
    LoadError.ServerError, LoadError.Unexpected -> ChannelsViewModel.AddError.ServerError
}
