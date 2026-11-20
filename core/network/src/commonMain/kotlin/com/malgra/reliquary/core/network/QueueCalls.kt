package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.QueueAddItem
import com.malgra.reliquary.core.network.model.QueueAddRequest
import com.malgra.reliquary.core.network.model.QueueStatusRequest

/*
 * Download queue writes (#23). TA answers status changes with the request echoed
 * back and removals with 204, so only the status is checked.
 */

/** Each entry may be a video, channel or playlist URL or ID; TA expands channels and playlists. */
suspend fun TubeArchivistApi.queueUp(entries: List<String>, autostart: Boolean) {
    addToQueue(QueueAddRequest(entries.map { QueueAddItem(it, status = "pending") }), autostart = autostart.takeIf { it })
}

suspend fun TubeArchivistApi.setQueueItemStatus(videoId: String, status: String) =
    setQueueStatus(videoId, QueueStatusRequest(status)).orThrow()

suspend fun TubeArchivistApi.dropFromQueue(videoId: String) = removeFromQueue(videoId).orThrow()

suspend fun TubeArchivistApi.clearQueueByStatus(status: String) = clearQueue(status).orThrow()
