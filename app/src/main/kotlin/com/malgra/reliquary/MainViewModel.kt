package com.malgra.reliquary

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(sessions: SessionRepository) : ViewModel() {
    val session: StateFlow<SessionState> = sessions.state
}
