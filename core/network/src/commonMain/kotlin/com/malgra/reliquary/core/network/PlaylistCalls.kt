package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.PlaylistSubscribeItem
import com.malgra.reliquary.core.network.model.PlaylistSubscribeRequest

/*
 * Playlist subscriptions (#25). Adding only starts a task on the server, whose
 * answer is just the task's ID, so this checks the status and throws
 * HttpException on failure, like the typed calls do.
 */

suspend fun TubeArchivistApi.subscribeToPlaylist(playlistId: String) =
    subscribePlaylists(PlaylistSubscribeRequest(listOf(PlaylistSubscribeItem(playlistId, playlistSubscribed = true)))).orThrow()
