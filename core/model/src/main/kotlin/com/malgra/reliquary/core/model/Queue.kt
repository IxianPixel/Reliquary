package com.malgra.reliquary.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/** A video in the server's download queue (#23); not on the server yet. */
data class QueueItem(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    /** Zero when TA doesn't know it yet. */
    val duration: Duration,
    val published: Instant?,
    val thumbnailUrl: String?,
    val type: VideoType,
    val ignored: Boolean,
    /** Why the last download attempt failed, if it did. */
    val error: String?,
)

/** One of the server's background tasks that touch the queue, as TA reports it. */
data class ServerTask(
    val title: String,
    val messages: List<String>,
    /** 0 to 1, when the task reports it. */
    val progress: Float?,
    val failed: Boolean,
)
