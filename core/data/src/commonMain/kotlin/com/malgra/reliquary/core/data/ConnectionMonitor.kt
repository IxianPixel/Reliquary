package com.malgra.reliquary.core.data

import com.malgra.reliquary.core.data.di.ApplicationScope
import com.malgra.reliquary.core.network.ServerReachability
import com.malgra.reliquary.core.network.ServerUrl
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.SessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the app is offline: no network, or the signed-in server didn't
 * answer its last request. While offline it checks the server every 30
 * seconds, and again whenever the [DeviceNetwork] changes, so it notices when
 * (say) Tailscale reconnects.
 */
@Singleton
class ConnectionMonitor @Inject constructor(
    private val device: DeviceNetwork,
    private val reachability: ServerReachability,
    private val network: TubeArchivistNetwork,
    private val sessions: SessionHolder,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val hasNetwork = MutableStateFlow(true)

    val isOffline: StateFlow<Boolean> = combine(hasNetwork, reachability.reachable) { network, reachable ->
        !network || reachable == false
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        hasNetwork.value = device.isConnected()
        device.listen(
            object : DeviceNetwork.Listener {
                override fun onAvailable() {
                    hasNetwork.value = true
                    scope.launch { checkServer() }
                }

                override fun onLost() {
                    hasNetwork.value = false
                }
            },
        )
        scope.launch {
            isOffline.collectLatest { offline ->
                while (offline && hasNetwork.value) {
                    delay(RECHECK_INTERVAL)
                    checkServer()
                }
            }
        }
    }

    /** Asks the server for its health; the shared client reports the answer to [ServerReachability]. */
    suspend fun checkServer() {
        val base = sessions.current?.baseUrl?.let(ServerUrl::normalize) ?: return
        network.checkServer(base)
    }

    private companion object {
        val RECHECK_INTERVAL = 30.seconds
    }
}
