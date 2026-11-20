package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.ChannelSubscribeRequest
import com.malgra.reliquary.core.network.model.ChannelSubscribedUpdate
import com.malgra.reliquary.core.network.model.ChannelSubscription

/*
 * Channel subscriptions (#24). TA echoes the request back, which tells us
 * nothing, so these only check the status and throw HttpException on failure.
 */

/** Queues TA's subscribe task for [urlOrId]; the channel only exists on the server once that has run. */
suspend fun TubeArchivistApi.subscribeToChannel(urlOrId: String) =
    subscribeChannels(ChannelSubscribeRequest(listOf(ChannelSubscription(urlOrId, channelSubscribed = true)))).orThrow()

/** For a channel already on the server; takes effect straight away. */
suspend fun TubeArchivistApi.setChannelSubscribed(channelId: String, subscribed: Boolean) =
    updateChannelSubscribed(channelId, ChannelSubscribedUpdate(subscribed)).orThrow()
