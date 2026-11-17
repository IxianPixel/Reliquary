package com.malgra.reliquary.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.library.ChannelSettingsRepository
import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.model.ChannelSettings
import com.malgra.reliquary.core.model.LoadError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [settings] is what the server has; null while loading or after [loadError]. */
data class ChannelSettingsState(
    val open: Boolean = false,
    val settings: ChannelSettings? = null,
    val loadError: LoadError? = null,
    val saving: Boolean = false,
    val saveError: LoadError? = null,
)

/** The channel's own settings on the server (#26), for admins (the screen checks). Read fresh each time the sheet opens. */
class ChannelSettingsViewModel(
    private val channelId: String,
    private val repository: ChannelSettingsRepository,
) : ViewModel() {

    class Factory @Inject constructor(
        private val repository: ChannelSettingsRepository,
    ) {
        fun create(channelId: String) = ChannelSettingsViewModel(channelId, repository)
    }

    private val _state = MutableStateFlow(ChannelSettingsState())
    val state: StateFlow<ChannelSettingsState> = _state.asStateFlow()

    private var loading: Job? = null

    fun open() {
        _state.value = ChannelSettingsState(open = true)
        load()
    }

    fun retry() = load()

    fun close() {
        loading?.cancel()
        _state.update { it.copy(open = false) }
    }

    fun save(new: ChannelSettings) {
        val old = _state.value.settings ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val error = repository.save(channelId, old, new)
            _state.update {
                if (error == null) it.copy(open = false, saving = false, settings = new) else it.copy(saving = false, saveError = error)
            }
        }
    }

    private fun load() {
        loading?.cancel()
        _state.update { it.copy(settings = null, loadError = null) }
        loading = viewModelScope.launch {
            val result = repository.settings(channelId)
            _state.update {
                when (result) {
                    is LoadResult.Ok -> it.copy(settings = result.value)
                    is LoadResult.Failed -> it.copy(loadError = result.error)
                }
            }
        }
    }
}
