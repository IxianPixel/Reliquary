package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.network.ServerUrl
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.SessionHolder
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** The API of the signed-in server. */
@Singleton
class LibraryApi @Inject constructor(
    private val network: TubeArchivistNetwork,
    private val sessions: SessionHolder,
) {
    class Server(val baseUrl: HttpUrl, val api: TubeArchivistApi)

    /** @throws NotSignedInException when nobody is signed in. */
    fun server(): Server {
        val baseUrl = sessions.current?.baseUrl?.let(ServerUrl::normalize) ?: throw NotSignedInException()
        return Server(baseUrl, network.api(baseUrl))
    }
}

class NotSignedInException : IllegalStateException("Not signed in")
