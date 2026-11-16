package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.SponsorSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * When to skip. A segment is skipped when playback reaches it; one the user
 * seeks into (or goes back to with Undo) is left to play until they leave it.
 */
internal class SegmentSkipRules {
    private val watching = mutableSetOf<Int>()

    /**
     * Segments just skipped. A seek may land a little short of the end (desktop
     * players seek to a keyframe), and that mustn't trigger the skip again.
     */
    private val skipped = mutableSetOf<Int>()

    /** The user moved to [positionMs]: they mean to watch any segment they landed in. */
    fun onUserSeek(segments: List<SponsorSegment>, positionMs: Long) {
        segments.forEachIndexed { i, segment -> if (segment.contains(positionMs)) watching += i }
    }

    /** [segment] was skipped. */
    fun onSkipped(segments: List<SponsorSegment>, segment: SponsorSegment) {
        val i = segments.indexOf(segment)
        if (i >= 0) skipped += i
    }

    /** A new video: nothing chosen yet. */
    fun reset() {
        watching.clear()
        skipped.clear()
    }

    /** The segment to skip at [positionMs], if any. */
    fun segmentToSkip(segments: List<SponsorSegment>, positionMs: Long): SponsorSegment? {
        watching.retainAll { i -> segments.getOrNull(i)?.contains(positionMs) == true }
        skipped.retainAll { i -> segments.getOrNull(i)?.contains(positionMs) == true }
        return segments.withIndex().firstOrNull { (i, segment) ->
            i !in watching && i !in skipped && positionMs >= segment.start.inWholeMilliseconds &&
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
 * Skips SponsorBlock segments in the playing video while [enabled] says so.
 * On Android it runs in the playback service, so it works with the screen off too.
 */
internal class SponsorSkipper(
    private val player: VideoPlayer,
    private val enabled: () -> Boolean,
    main: CoroutineDispatcher = Dispatchers.Main,
    private val onSkipped: (SponsorSegment) -> Unit,
) : VideoPlayerListener {

    private val ticks = CoroutineScope(SupervisorJob() + main)
    private var tick: Job? = null
    private val rules = SegmentSkipRules()
    private var segments: List<SponsorSegment> = player.currentItem?.sponsorSegments.orEmpty()

    /** Our own skip's seek is still to be reported (media3 reports it within seekTo, desktop later). */
    private var skipSeekPending = false

    init {
        player.addListener(this)
    }

    override fun onItemTransition(item: PlayerItem?, reason: ItemTransitionReason) {
        segments = item?.sponsorSegments.orEmpty()
        rules.reset()
        skipSeekPending = false
    }

    override fun onPositionJump(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) {
        if (reason != PositionJumpReason.Seek) return
        // Our own skip isn't the user choosing where to watch (it may land at the start of the next segment).
        if (skipSeekPending) {
            skipSeekPending = false
            return
        }
        rules.onUserSeek(segments, new.positionMs)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        tick?.cancel()
        if (isPlaying) {
            tick = ticks.launch {
                while (isActive && player.isPlaying) {
                    check()
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }

    private fun check() {
        if (segments.isEmpty() || !enabled()) return
        val segment = rules.segmentToSkip(segments, player.positionMs) ?: return
        rules.onSkipped(segments, segment)
        skipSeekPending = true
        player.seekTo(segment.end.inWholeMilliseconds)
        onSkipped(segment)
    }

    fun release() {
        ticks.cancel()
        player.removeListener(this)
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 250L
    }
}
