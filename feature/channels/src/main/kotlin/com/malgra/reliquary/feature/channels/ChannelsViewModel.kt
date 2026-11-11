package com.malgra.reliquary.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.malgra.reliquary.core.data.library.ChannelRepository
import com.malgra.reliquary.core.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(channels: ChannelRepository) : ViewModel() {
    val channels: Flow<PagingData<Channel>> = channels.channels().cachedIn(viewModelScope)
}
