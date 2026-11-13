package com.malgra.reliquary.feature.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.malgra.reliquary.core.model.SponsorSegment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.milliseconds

private const val KEY_STARTS = "com.malgra.reliquary.segmentStarts"
private const val KEY_ENDS = "com.malgra.reliquary.segmentEnds"
private const val KEY_CATEGORIES = "com.malgra.reliquary.segmentCategories"

/** Adds [segments] to a media item's request extras, which reach the playback service. */
internal fun Bundle.putSponsorSegments(segments: List<SponsorSegment>): Bundle = apply {
    putLongArray(KEY_STARTS, segments.map { it.start.inWholeMilliseconds }.toLongArray())
    putLongArray(KEY_ENDS, segments.map { it.end.inWholeMilliseconds }.toLongArray())
    putStringArrayList(KEY_CATEGORIES, ArrayList(segments.map { it.category }))
}

/** The segments a bundle made by [putSponsorSegments] holds. */
internal fun Bundle.sponsorSegments(): List<SponsorSegment> {
    val starts = getLongArray(KEY_STARTS) ?: return emptyList()
    val ends = getLongArray(KEY_ENDS) ?: return emptyList()
    val categories = getStringArrayList(KEY_CATEGORIES).orEmpty()
    return starts.indices.mapNotNull { i ->
        val end = ends.getOrNull(i) ?: return@mapNotNull null
        SponsorSegment(starts[i].milliseconds, end.milliseconds, categories.getOrElse(i) { "" })
    }
}

/** The segments an item was sent with. */
internal fun MediaItem.sponsorSegments(): List<SponsorSegment> = requestMetadata.extras?.sponsorSegments().orEmpty()

/**
 * When to skip. A segment is skipped when playback reaches it; one the user
 * seeks into (or goes back to with Undo) is left to play until they leave it.
 */
internal class SegmentSkipRules {
    private val watching = mutableSetOf<Int>()

    /** The user moved to [positionMs]: they mean to watch any segment they landed in. */
    fun onUserSeek(segments: List<SponsorSegment>, positionMs: Long) {
        segments.forEachIndexed { i, segment -> if (segment.contains(positionMs)) watching += i }
    }

    /** A new video: nothing chosen yet. */
    fun reset() = watching.clear()

    /** The segment to skip at [positionMs], if any. */
    fun segmentToSkip(segments: List<SponsorSegment>, positionMs: Long): SponsorSegment? {
        watching.retainAll { i -> segments.getOrNull(i)?.contains(positionMs) == true }
        return segments.withIndex().firstOrNull { (i, segment) ->
            i !in watching && positionMs >= segment.start.inWholeMilliseconds &&
                positionMs < segment.end.inWholeMilliseconds - END_MARGIN_MS
        }?.value
    }

    private fun SponsorSegment.contains(positionMs: Long) =
        positionMs >= start.inWholeMilliseconds && positionMs < end.inWholeMilliseconds

    private companion object {
        /** Not worth a jump when this little of the segment is left. */
        const val END_MARGIN_MS = 500L
    }
}

/**
 * Skips SponsorBlock segments in the playing video while [enabled] says so,
 * from the playback service so it works with the screen off too.
 */
internal class SponsorSkipper(
    private val player: Player,
    private val enabled: () -> Boolean,
    private val onSkipped: (SponsorSegment) -> Unit,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val rules = SegmentSkipRules()
    private var segments: List<SponsorSegment> = player.currentMediaItem?.sponsorSegments().orEmpty()
    private var skipping = false

    private val tick = object : Runnable {
        override fun run() {
            check()
            if (player.isPlaying) handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    init {
        player.addListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        segments = mediaItem?.sponsorSegments().orEmpty()
        rules.reset()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Our own skip isn't the user choosing where to watch (it may land at the start of the next segment).
        if (reason == Player.DISCONTINUITY_REASON_SEEK && !skipping) rules.onUserSeek(segments, newPosition.positionMs)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) handler.post(tick)
    }

    private fun check() {
        if (segments.isEmpty() || !enabled()) return
        val segment = rules.segmentToSkip(segments, player.currentPosition) ?: return
        skipping = true
        try {
            player.seekTo(segment.end.inWholeMilliseconds)
        } finally {
            skipping = false
        }
        onSkipped(segment)
    }

    fun release() {
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 250L
    }
}

/** Skips the service made, passed on to the player screen for its Undo snackbar. */
internal object SponsorSkips {
    const val ACTION = "com.malgra.reliquary.SPONSOR_SKIPPED"

    private val skips = MutableSharedFlow<SponsorSegment>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<SponsorSegment> = skips

    fun toBundle(segment: SponsorSegment): Bundle = Bundle().putSponsorSegments(listOf(segment))

    fun received(args: Bundle) {
        args.sponsorSegments().firstOrNull()?.let { skips.tryEmit(it) }
    }
}
