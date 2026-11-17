package com.malgra.reliquary.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Channel subscriptions (#24), from ChannelApiListView.post and
 * ChannelUpdateSerializer in backend/channel/.
 */

/** `POST /api/channel/`. [ChannelSubscription.channelId] may be a URL or handle: TA's subscribe task parses it. */
@Serializable
data class ChannelSubscribeRequest(val data: List<ChannelSubscription>)

@Serializable
data class ChannelSubscription(
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_subscribed") val channelSubscribed: Boolean,
)

/** `POST /api/channel/<id>/`; changes only the subscription. */
@Serializable
data class ChannelSubscribedUpdate(@SerialName("channel_subscribed") val channelSubscribed: Boolean)
