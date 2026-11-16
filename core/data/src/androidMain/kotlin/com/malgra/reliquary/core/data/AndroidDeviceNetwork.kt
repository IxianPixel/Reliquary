package com.malgra.reliquary.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The default network, from [ConnectivityManager]. */
@Singleton
class AndroidDeviceNetwork @Inject constructor(@ApplicationContext context: Context) : DeviceNetwork {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun isConnected(): Boolean = connectivity.activeNetwork
        ?.let(connectivity::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    override fun listen(listener: DeviceNetwork.Listener) {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = listener.onAvailable()

                override fun onLost(network: Network) = listener.onLost()
            },
        )
    }
}
