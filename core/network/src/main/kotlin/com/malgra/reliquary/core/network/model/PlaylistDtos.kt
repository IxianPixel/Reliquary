package com.malgra.reliquary.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/playlist/`: starts a task that indexes each playlist and subscribes to it. */
@Serializable
data class PlaylistSubscribeRequest(val data: List<PlaylistSubscribeItem>)

/** TA only accepts true here; unsubscribing goes through [PlaylistUpdateRequest]. */
@Serializable
data class PlaylistSubscribeItem(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("playlist_subscribed") val playlistSubscribed: Boolean,
)

/** `POST /api/playlist/<id>/`, for a playlist the server already has. */
@Serializable
data class PlaylistUpdateRequest(@SerialName("playlist_subscribed") val playlistSubscribed: Boolean)
