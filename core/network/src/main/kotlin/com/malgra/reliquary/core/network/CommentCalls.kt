package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.CommentDto
import retrofit2.HttpException

/**
 * The video's saved comments. Tube Archivist answers 404 for a video it saved
 * no comments for, which here is just an empty list; other failures throw
 * HttpException like the typed calls do.
 */
suspend fun TubeArchivistApi.commentsOrEmpty(videoId: String): List<CommentDto> {
    val response = comments(videoId)
    return when {
        response.code() == HTTP_NOT_FOUND -> emptyList()
        !response.isSuccessful -> throw HttpException(response)
        else -> response.body().orEmpty()
    }
}

private const val HTTP_NOT_FOUND = 404
