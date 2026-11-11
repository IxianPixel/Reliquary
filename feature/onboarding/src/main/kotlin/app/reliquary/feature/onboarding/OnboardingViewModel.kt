package app.reliquary.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reliquary.core.data.auth.AuthRepository
import app.reliquary.core.data.auth.ServerCheckResult
import app.reliquary.core.data.auth.SignInResult
import app.reliquary.core.data.session.SessionRepository
import app.reliquary.core.data.session.SessionState
import app.reliquary.core.network.Failure
import app.reliquary.core.network.ServerCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInMethod { Password, Token }

sealed interface ServerStatus {
    data object Idle : ServerStatus
    data object Checking : ServerStatus
    data object Found : ServerStatus
    data object InvalidUrl : ServerStatus
    data object NotTubeArchivist : ServerStatus
    data class Unreachable(val failure: Failure) : ServerStatus
}

sealed interface SignInError {
    data object InvalidCredentials : SignInError
    data object TokenRejected : SignInError
    data class UnsupportedVersion(val version: String) : SignInError
    data class Network(val failure: Failure) : SignInError
}

data class OnboardingUiState(
    val serverUrl: String = "",
    val server: ServerStatus = ServerStatus.Idle,
    val method: SignInMethod = SignInMethod.Password,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val signingIn: Boolean = false,
    val error: SignInError? = null,
    val sessionExpired: Boolean = false,
) {
    val canConnect: Boolean
        get() = server == ServerStatus.Found && !signingIn && when (method) {
            SignInMethod.Password -> username.isNotBlank() && password.isNotEmpty()
            SignInMethod.Token -> token.isNotBlank()
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        // Prefill after a sign-out or an expired session.
        viewModelScope.launch {
            val signedOut = sessions.state.first { it !is SessionState.Loading } as? SessionState.SignedOut
            val lastUrl = signedOut?.lastServerUrl ?: return@launch
            if (_state.value.serverUrl.isNotEmpty()) return@launch
            _state.update {
                it.copy(
                    serverUrl = lastUrl.removeSuffix("/"),
                    username = signedOut.lastUsername.orEmpty(),
                    sessionExpired = signedOut.expired,
                )
            }
            scheduleCheck(debounceMillis = 0)
        }
    }

    fun onServerUrlChange(value: String) {
        _state.update { it.copy(serverUrl = value, error = null) }
        scheduleCheck(DEBOUNCE_MILLIS)
    }

    fun onMethodChange(method: SignInMethod) = _state.update { it.copy(method = method, error = null) }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    fun connect() {
        val current = _state.value
        if (!current.canConnect) return
        _state.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            val result = when (current.method) {
                SignInMethod.Password ->
                    auth.signInWithPassword(current.serverUrl, current.username, current.password)
                SignInMethod.Token -> auth.signInWithToken(current.serverUrl, current.token)
            }
            // On success the session state changes and the app leaves this screen.
            val error = when (result) {
                SignInResult.Success -> null
                SignInResult.InvalidUrl -> {
                    _state.update { it.copy(server = ServerStatus.InvalidUrl) }
                    null
                }
                SignInResult.InvalidCredentials -> SignInError.InvalidCredentials
                SignInResult.TokenRejected -> SignInError.TokenRejected
                is SignInResult.UnsupportedVersion -> SignInError.UnsupportedVersion(result.version)
                is SignInResult.Failed -> SignInError.Network(result.failure)
            }
            _state.update { it.copy(signingIn = false, error = error, password = if (error != null) "" else it.password) }
        }
    }

    private fun scheduleCheck(debounceMillis: Long) {
        checkJob?.cancel()
        val url = _state.value.serverUrl
        if (url.isBlank()) {
            _state.update { it.copy(server = ServerStatus.Idle) }
            return
        }
        checkJob = viewModelScope.launch {
            delay(debounceMillis)
            _state.update { it.copy(server = ServerStatus.Checking) }
            val status = when (val result = auth.checkServer(url)) {
                ServerCheckResult.InvalidUrl -> ServerStatus.InvalidUrl
                is ServerCheckResult.Checked -> when (val check = result.check) {
                    ServerCheck.Found -> ServerStatus.Found
                    ServerCheck.NotTubeArchivist -> ServerStatus.NotTubeArchivist
                    is ServerCheck.Unreachable -> ServerStatus.Unreachable(check.failure)
                }
            }
            _state.update { it.copy(server = status) }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 600L
    }
}
