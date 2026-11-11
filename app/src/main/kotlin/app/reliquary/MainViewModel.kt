package app.reliquary

import androidx.lifecycle.ViewModel
import app.reliquary.core.data.session.SessionRepository
import app.reliquary.core.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(sessions: SessionRepository) : ViewModel() {
    val session: StateFlow<SessionState> = sessions.state
}
