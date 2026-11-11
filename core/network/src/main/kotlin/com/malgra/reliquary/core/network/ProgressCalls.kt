package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.ProgressRequest
import com.malgra.reliquary.core.network.model.WatchedRequest
import retrofit2.HttpException
import retrofit2.Response

/*
 * Progress writes. The bodies TA answers with aren't needed (sync reads the
 * video back afterwards), so these only check the status and throw
 * HttpException on failure, like the typed calls do.
 */

suspend fun TubeArchivistApi.savePosition(videoId: String, seconds: Double) =
    setProgress(videoId, ProgressRequest(seconds)).orThrow()

suspend fun TubeArchivistApi.deletePosition(videoId: String) = clearProgress(videoId).orThrow()

suspend fun TubeArchivistApi.markWatched(videoId: String, watched: Boolean) =
    setWatched(WatchedRequest(videoId, watched)).orThrow()

private fun Response<*>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}
