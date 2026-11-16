package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.model.ChannelSettings
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.network.channelOverwrites
import com.malgra.reliquary.core.network.model.ChannelOverwritesDto
import com.malgra.reliquary.core.network.saveChannelOverwrites
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A channel's own settings on the server (#26). Online only and not cached:
 * they only matter to the server, and only an admin can change them.
 */
@Singleton
class ChannelSettingsRepository @Inject constructor(
    private val library: LibraryApi,
) {
    suspend fun settings(channelId: String): LoadResult<ChannelSettings> = load {
        library.server().api.channelOverwrites(channelId).toModel()
    }

    /** Sends only what changed from [old], the values [settings] returned; returns the error, or null on success. */
    suspend fun save(channelId: String, old: ChannelSettings, new: ChannelSettings): LoadError? = load {
        library.server().api.saveChannelOverwrites(channelId, old.toDto(), new.toDto())
    }.errorOrNull
}

internal fun ChannelOverwritesDto.toModel() = ChannelSettings(
    videosPageSize = videosSize,
    shortsPageSize = shortsSize,
    streamsPageSize = liveSize,
    downloadFormat = downloadFormat?.takeIf { it.isNotBlank() },
    autoDeleteDays = autodeleteDays,
    indexPlaylists = indexPlaylists == true,
    sponsorBlock = integrateSponsorblock,
)

// Off is TA's default, so it's sent as a clear rather than an explicit false.
internal fun ChannelSettings.toDto() = ChannelOverwritesDto(
    downloadFormat = downloadFormat?.trim()?.takeIf { it.isNotEmpty() },
    autodeleteDays = autoDeleteDays,
    indexPlaylists = indexPlaylists.takeIf { it },
    integrateSponsorblock = sponsorBlock,
    videosSize = videosPageSize,
    liveSize = streamsPageSize,
    shortsSize = shortsPageSize,
)
