package com.malgra.reliquary.core.data.session

import com.malgra.reliquary.core.data.ConnectionMonitor
import com.malgra.reliquary.core.data.di.ApplicationScope
import com.malgra.reliquary.core.model.ServerSession
import com.malgra.reliquary.core.network.NetworkResult
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.RequestAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the signed-in account can manage the server: subscriptions, the
 * download queue and channel settings all need TA's staff flag. Asked once per
 * account rather than stored, since none of that works offline anyway; false
 * until the server answers, so admin controls stay hidden when it can't, and
 * asked again when the connection comes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountRepository @Inject constructor(
    private val sessions: SessionRepository,
    private val network: TubeArchivistNetwork,
    connection: ConnectionMonitor,
    @ApplicationScope scope: CoroutineScope,
) {
    private var known: Pair<AccountKey, Boolean>? = null

    val isAdmin: StateFlow<Boolean> = combine(
        sessions.state.map { (it as? SessionState.SignedIn)?.session?.let(::AccountKey) }.distinctUntilChanged(),
        connection.isOffline,
    ) { account, offline -> account to offline }
        .distinctUntilChanged()
        .mapLatest { (account, offline) ->
            when {
                account == null -> false
                known?.first == account -> known!!.second
                offline -> false
                else -> fetchIsStaff()?.also { known = account to it } ?: false
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private suspend fun fetchIsStaff(): Boolean? {
        val session = sessions.current ?: return null
        val result = network.account(RequestAuth(session.baseUrl, session.credentials))
        return (result as? NetworkResult.Ok)?.value?.isStaff
    }

    private data class AccountKey(val baseUrl: String, val username: String?) {
        constructor(session: ServerSession) : this(session.baseUrl, session.username)
    }
}
