package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.model.ChannelOverwritesDto
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.AUTODELETE_DAYS
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.DOWNLOAD_FORMAT
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.INDEX_PLAYLISTS
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.INTEGRATE_SPONSORBLOCK
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.LIVE_SIZE
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.SHORTS_SIZE
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto.Companion.VIDEOS_SIZE
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Channel settings (#26). TA clears an overwrite when it's sent as null, but
 * the shared Json leaves nulls out, so the body is built by hand. Only changed
 * keys are sent: index_playlists = true starts a slow playlist scan each time.
 */

suspend fun TubeArchivistApi.channelOverwrites(channelId: String): ChannelOverwritesDto =
    channelSettings(channelId).overwrites ?: ChannelOverwritesDto()

/** Sends what differs between [old] and [new]; nothing at all when they match. */
suspend fun TubeArchivistApi.saveChannelOverwrites(channelId: String, old: ChannelOverwritesDto, new: ChannelOverwritesDto) {
    val changes = overwriteChanges(old, new)
    if (changes.isEmpty()) return
    val body = buildJsonObject { put("channel_overwrites", changes) }.toString()
    updateChannelOverwrites(channelId, body.toRequestBody("application/json".toMediaType())).orThrow()
}

internal fun overwriteChanges(old: ChannelOverwritesDto, new: ChannelOverwritesDto): JsonObject {
    val fields = listOf(
        DOWNLOAD_FORMAT to ChannelOverwritesDto::downloadFormat,
        AUTODELETE_DAYS to ChannelOverwritesDto::autodeleteDays,
        INDEX_PLAYLISTS to ChannelOverwritesDto::indexPlaylists,
        INTEGRATE_SPONSORBLOCK to ChannelOverwritesDto::integrateSponsorblock,
        VIDEOS_SIZE to ChannelOverwritesDto::videosSize,
        LIVE_SIZE to ChannelOverwritesDto::liveSize,
        SHORTS_SIZE to ChannelOverwritesDto::shortsSize,
    )
    return JsonObject(
        fields.mapNotNull { (key, field) ->
            val value = field(new)
            if (value == field(old)) return@mapNotNull null
            key to when (value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> error("Unexpected overwrite $key")
            }
        }.toMap(),
    )
}
