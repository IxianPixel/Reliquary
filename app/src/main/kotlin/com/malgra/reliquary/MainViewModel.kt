package com.malgra.reliquary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.progress.ProgressRepository
import com.malgra.reliquary.core.data.session.AccountRepository
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    sessions: SessionRepository,
    progress: ProgressRepository,
    account: AccountRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessions.state

    /** Shown as a badge on the Downloads tab, as in the offline mockup. */
    val pendingSync: StateFlow<Int> = progress.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Admins get the Queue tab. */
    val isAdmin: StateFlow<Boolean> = account.isAdmin
}
