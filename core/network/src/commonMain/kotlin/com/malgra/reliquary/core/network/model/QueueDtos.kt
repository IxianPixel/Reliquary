package com.malgra.reliquary.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An item in TA's download queue (`/api/download/`). */
@Serializable
data class QueueItemDto(
    @SerialName("youtube_id") val youtubeId: String,
    val title: String = "",
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("channel_name") val channelName: String = "",
    @SerialName("channel_indexed") val channelIndexed: Boolean = false,
    /** Already formatted by TA, e.g. "12:34". */
    val duration: String = "",
    val published: String? = null,
    /** "pending" or "ignore". */
    val status: String? = null,
    /** When it was added to the queue, in epoch seconds. */
    val timestamp: Long? = null,
    @SerialName("vid_thumb_url") val vidThumbUrl: String? = null,
    @SerialName("vid_type") val vidType: String? = null,
    @SerialName("auto_start") val autoStart: Boolean = false,
    /** Why the last download attempt failed. */
    val message: String? = null,
)

@Serializable
data class QueueAddRequest(val data: List<QueueAddItem>)

/** No default for [status]: the shared Json leaves defaults out, and TA requires it. */
@Serializable
data class QueueAddItem(@SerialName("youtube_id") val youtubeId: String, val status: String)

/** For one item: "pending", "ignore" or "priority" (download it next). */
@Serializable
data class QueueStatusRequest(val status: String)

@Serializable
data class TaskStartedDto(@SerialName("task_id") val taskId: String, val message: String? = null)

/** A live message from TA's background tasks (`/api/notification/`). */
@Serializable
data class NotificationDto(
    val id: String,
    val title: String = "",
    val group: String = "",
    val level: String = "info",
    val messages: List<String> = emptyList(),
    /** 0 to 1, when the task reports it. */
    val progress: Double? = null,
    @SerialName("api_stop") val apiStop: Boolean = false,
)
