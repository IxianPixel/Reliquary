package com.malgra.reliquary.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.data.queue.QueueRepository
import com.malgra.reliquary.core.data.session.AccountRepository
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.QueueItem
import com.malgra.reliquary.core.model.ServerTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QueueTab { Pending, Ignored }

data class QueueListState(
    val tab: QueueTab = QueueTab.Pending,
    /** Null until the first page arrives. */
    val items: List<QueueItem>? = null,
    val total: Int? = null,
    val nextPage: Int? = null,
    val refreshing: Boolean = false,
    val appending: Boolean = false,
    val error: LoadError? = null,
)

/** The add sheet's request: [Done] closes it. */
enum class AddState { Idle, Busy, Failed, Done }

/** One-off results shown in a snackbar. */
enum class QueueMessage { Added, RescanStarted, DownloadStarted, Failed }

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queue: QueueRepository,
    account: AccountRepository,
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = account.isAdmin

    private val _list = MutableStateFlow(QueueListState())
    val list: StateFlow<QueueListState> = _list.asStateFlow()

    private val _messages = Channel<QueueMessage>(Channel.BUFFERED)
    val messages: Flow<QueueMessage> = _messages.receiveAsFlow()

    /** Polled while the screen is showing; the list reloads when a task finishes. */
    val tasks: StateFlow<List<ServerTask>> = flow {
        var wasBusy = false
        while (true) {
            val running = (queue.tasks() as? LoadResult.Ok)?.value.orEmpty()
            emit(running)
            if (wasBusy && running.isEmpty()) refresh()
            wasBusy = running.isNotEmpty()
            delay(if (wasBusy) BUSY_POLL_MS else IDLE_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _adding = MutableStateFlow(AddState.Idle)
    val adding: StateFlow<AddState> = _adding.asStateFlow()

    private var loading: Job? = null

    init {
        refresh()
    }

    fun selectTab(tab: QueueTab) {
        if (tab == _list.value.tab) return
        _list.value = QueueListState(tab = tab)
        refresh()
    }

    fun refresh() {
        loading?.cancel()
        _list.update { it.copy(refreshing = true, appending = false) }
        loading = viewModelScope.launch {
            val tab = _list.value.tab
            when (val result = queue.page(ignored = tab == QueueTab.Ignored)) {
                is LoadResult.Ok -> _list.update {
                    it.copy(items = result.value.items, total = result.value.total, nextPage = result.value.next, refreshing = false, error = null)
                }
                is LoadResult.Failed -> _list.update { it.copy(refreshing = false, error = result.error) }
            }
        }
    }

    fun loadMore() {
        val state = _list.value
        val page = state.nextPage ?: return
        if (state.refreshing || state.appending) return
        _list.update { it.copy(appending = true) }
        loading = viewModelScope.launch {
            when (val result = queue.page(ignored = state.tab == QueueTab.Ignored, page = page)) {
                is LoadResult.Ok -> _list.update { current ->
                    val seen = current.items.orEmpty().mapTo(HashSet()) { it.videoId }
                    current.copy(
                        items = current.items.orEmpty() + result.value.items.filterNot { it.videoId in seen },
                        nextPage = result.value.next,
                        appending = false,
                    )
                }
                is LoadResult.Failed -> _list.update { it.copy(appending = false, error = result.error) }
            }
        }
    }

    /** Several links pasted at once are added together. */
    fun add(text: String) {
        val entries = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        if (entries.isEmpty() || _adding.value == AddState.Busy) return
        _adding.value = AddState.Busy
        viewModelScope.launch {
            val ok = queue.add(entries, downloadNow = false) is LoadResult.Ok
            _adding.value = if (ok) AddState.Done else AddState.Failed
            if (ok) _messages.send(QueueMessage.Added)
        }
    }

    fun resetAdd() {
        _adding.value = AddState.Idle
    }

    fun rescan() = act(QueueMessage.RescanStarted) { queue.rescanSubscriptions() }

    fun downloadAll() = act(QueueMessage.DownloadStarted) { queue.downloadAll() }

    /** Stays on the list: it's still pending until the server has fetched it. */
    fun downloadNext(item: QueueItem) = act(QueueMessage.DownloadStarted) { queue.downloadNext(item.videoId) }

    fun ignore(item: QueueItem) = changeItem(item) { queue.ignore(item.videoId) }

    fun restore(item: QueueItem) = changeItem(item) { queue.restore(item.videoId) }

    fun remove(item: QueueItem) = changeItem(item) { queue.remove(item.videoId) }

    fun clear() {
        viewModelScope.launch {
            when (queue.clear(ignored = _list.value.tab == QueueTab.Ignored)) {
                is LoadResult.Ok -> refresh()
                is LoadResult.Failed -> _messages.send(QueueMessage.Failed)
            }
        }
    }

    /** Takes the item off this list straight away, and puts it back if the server says no. */
    private fun changeItem(item: QueueItem, block: suspend () -> LoadResult<Unit>) {
        val before = _list.value
        _list.update { state ->
            state.copy(items = state.items?.filterNot { it.videoId == item.videoId }, total = state.total?.minus(1))
        }
        viewModelScope.launch {
            if (block() is LoadResult.Failed) {
                if (_list.value.tab == before.tab) _list.value = before
                _messages.send(QueueMessage.Failed)
            }
        }
    }

    private fun act(success: QueueMessage, block: suspend () -> LoadResult<Unit>) {
        viewModelScope.launch {
            _messages.send(if (block() is LoadResult.Ok) success else QueueMessage.Failed)
        }
    }

    private companion object {
        const val BUSY_POLL_MS = 2_000L
        const val IDLE_POLL_MS = 10_000L
    }
}
