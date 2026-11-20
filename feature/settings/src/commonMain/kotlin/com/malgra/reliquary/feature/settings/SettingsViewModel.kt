package com.malgra.reliquary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malgra.reliquary.core.data.auth.AuthRepository
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The storage limits Settings offers, in GB (10⁹ bytes, as file sizes are shown); no limit is the default. */
val StorageLimitPresetsGb = listOf(5, 10, 25, 50, 100)

const val BYTES_PER_GB = 1_000_000_000L

data class AccountInfo(
    val server: String,
    val username: String?,
    val usesToken: Boolean,
    val serverVersion: String?,
)

class SettingsViewModel @Inject constructor(
    sessions: SessionRepository,
    private val auth: AuthRepository,
    private val downloads: DownloadRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val wifiOnlyDownloads: StateFlow<Boolean> = settings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { downloads.setWifiOnly(enabled) }
    }

    val skipSponsorSegments: StateFlow<Boolean> = settings.skipSponsorSegments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSkipSponsorSegments(enabled: Boolean) {
        viewModelScope.launch { settings.setSkipSponsorSegments(enabled) }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** Null for no limit. */
    val storageLimitBytes: StateFlow<Long?> = settings.storageLimitBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStorageLimit(bytes: Long?) {
        viewModelScope.launch { downloads.setStorageLimit(bytes) }
    }

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
