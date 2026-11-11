package com.malgra.reliquary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.auth.AuthRepository
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.core.model.Credentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountInfo(
    val server: String,
    val username: String?,
    val usesToken: Boolean,
    val serverVersion: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    sessions: SessionRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val account: StateFlow<AccountInfo?> = sessions.state
        .map { state ->
            (state as? SessionState.SignedIn)?.session?.let {
                AccountInfo(
                    server = it.baseUrl.removeSuffix("/"),
                    username = it.username,
                    usesToken = it.credentials is Credentials.ApiToken,
                    serverVersion = it.serverVersion,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
