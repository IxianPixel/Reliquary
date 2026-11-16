package com.malgra.reliquary.core.data

/**
 * The device's own network connection, for [ConnectionMonitor]. Platforms
 * that can't tell (desktop) report always connected and rely on the server
 * checks alone.
 */
interface DeviceNetwork {
    /** Whether there's a network with internet access right now. */
    fun isConnected(): Boolean

    /** Starts reporting changes for the life of the app. */
    fun listen(listener: Listener)

    interface Listener {
        /** A network became the default, including a switch from one network to another. */
        fun onAvailable()

        /** There's no network at all. */
        fun onLost()
    }
}

/** For platforms without network callbacks: always connected, never changes. */
object AlwaysConnected : DeviceNetwork {
    override fun isConnected() = true

    override fun listen(listener: DeviceNetwork.Listener) = Unit
}
