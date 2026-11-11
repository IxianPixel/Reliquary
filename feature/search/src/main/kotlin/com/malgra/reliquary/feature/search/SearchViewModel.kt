package com.malgra.reliquary.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.data.library.SearchRepository
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val query: String, val results: SearchResults) : SearchState
    data class Failed(val error: LoadError) : SearchState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val search: SearchRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped by [retry] so the same query runs again. */
    private val attempt = MutableStateFlow(0)

    val state: StateFlow<SearchState> = combine(_query.debounce(DEBOUNCE_MILLIS), attempt) { q, _ -> q.trim() }
        .flatMapLatest { q ->
            flow {
                if (q.isEmpty()) {
                    emit(SearchState.Idle)
                    return@flow
                }
                emit(SearchState.Searching)
                emit(
                    when (val result = search.search(q)) {
                        is LoadResult.Ok -> SearchState.Results(q, result.value)
                        is LoadResult.Failed -> SearchState.Failed(result.error)
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() = attempt.update { it + 1 }

    private companion object {
        const val DEBOUNCE_MILLIS = 350L
    }
}
