package com.malgra.reliquary.feature.player

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi

/** A subtitle track as handed to the player. [id] is the Subtitle key, e.g. "en.user". */
internal data class SubtitleTrack(val id: String, val uri: Uri, val language: String, val label: String) {
    fun toConfiguration(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setId(id)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(language)
            .setLabel(label)
            .build()
}

private const val KEY_IDS = "com.malgra.reliquary.subtitleIds"
private const val KEY_URIS = "com.malgra.reliquary.subtitleUris"
private const val KEY_LANGUAGES = "com.malgra.reliquary.subtitleLanguages"
private const val KEY_LABELS = "com.malgra.reliquary.subtitleLabels"

internal fun List<SubtitleTrack>.toBundle(): Bundle = Bundle().apply {
    putStringArrayList(KEY_IDS, ArrayList(map { it.id }))
    putStringArrayList(KEY_URIS, ArrayList(map { it.uri.toString() }))
    putStringArrayList(KEY_LANGUAGES, ArrayList(map { it.language }))
    putStringArrayList(KEY_LABELS, ArrayList(map { it.label }))
}

/** The subtitle configurations an item was sent with, from its request metadata. */
internal fun MediaItem.subtitleConfigurationsFromRequest(): List<MediaItem.SubtitleConfiguration> {
    val extras = requestMetadata.extras ?: return emptyList()
    val ids = extras.getStringArrayList(KEY_IDS) ?: return emptyList()
    val uris = extras.getStringArrayList(KEY_URIS).orEmpty()
    val languages = extras.getStringArrayList(KEY_LANGUAGES).orEmpty()
    val labels = extras.getStringArrayList(KEY_LABELS).orEmpty()
    return ids.indices.mapNotNull { i ->
        val uri = uris.getOrNull(i) ?: return@mapNotNull null
        SubtitleTrack(ids[i], uri.toUri(), languages.getOrElse(i) { "" }, labels.getOrElse(i) { "" }).toConfiguration()
    }
}

/** The key of the subtitle track playing now, or null when subtitles are off. */
internal fun Player.selectedSubtitleKey(): String? =
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        .firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).id }
        }
        // Merged sources prefix track ids with their index ("1:en.user").
        ?.substringAfterLast(':')

/** Turns subtitles off. */
internal fun Player.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

/**
 * Shows [key]'s track if this video has it; otherwise prefers [language], which
 * also carries over to the next video in the queue.
 */
@OptIn(UnstableApi::class)
internal fun Player.selectSubtitle(key: String?, language: String) {
    val builder = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setPreferredTextLanguage(language)
    if (key != null) {
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
            val index = (0 until group.length).firstOrNull { group.getTrackFormat(it).id?.substringAfterLast(':') == key }
            if (index != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
    }
    trackSelectionParameters = builder.build()
}
