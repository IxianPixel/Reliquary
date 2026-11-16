package com.malgra.reliquary.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.createVideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [VideoPlayer] over ComposeMediaPlayer (AVPlayer on macOS, Media Foundation on
 * Windows, GStreamer on Linux). The library plays one file at a time, so the
 * queue is kept here; server media goes through [MediaProxy] for its credentials.
 *
 * Its seeks land on a keyframe, so a seek is reported once it has settled, at
 * the position it reached. Everything runs on [scope]'s main (Swing) thread.
 */
internal class DesktopVideoPlayer(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) : VideoPlayer {

    internal val state: VideoPlayerState = createVideoPlayerState()
    private val proxy = MediaProxy(client)
    private val listeners = CopyOnWriteArrayList<VideoPlayerListener>()

    private var queue = emptyList<PlayerItem>()
    private var index = -1
    private var proxyToken: String? = null

    private var openJob: Job? = null
    private var seekJob: Job? = null
    private var subtitleJob: Job? = null

    /** Set while an item is opening: the position to report until it plays. */
    private var opening by mutableStateOf<Long?>(null)
    private var ended by mutableStateOf(false)
    private var ownError by mutableStateOf<PlayerError?>(null)

    private var lastStatus = PlayerStatus(PlaybackState.Idle, false, null)
    private var lastSize = VideoSize.Unknown

    /** The subtitles asked for: off, or a language to prefer (and a track key for this video). */
    private var subtitlesOff = true
    private var subtitleLanguage = ""
    private var subtitleKey: String? = null

    /** The cues of the subtitle track showing, for [SubtitleOverlay]. */
    internal var cues by mutableStateOf(emptyList<SubtitleCue>())
        private set

    override var playWhenReady = false
        private set

    init {
        state.loop = false
        state.onPlaybackEnded = { scope.launch { onItemEnded() } }
        proxy.onUpstreamError = { token, status ->
            scope.launch {
                if (token != proxyToken) return@launch
                ownError = when (status) {
                    401, 403 -> PlayerError.SignedOut
                    404 -> PlayerError.NotFound
                    null -> PlayerError.Network
                    else -> PlayerError.Playback("HTTP $status")
                }
            }
        }
        // Turn the library's Compose state into callbacks.
        scope.launch {
            snapshotFlow { Triple(computeStatus(), state.metadata.width to state.metadata.height, state.isPlaying) }
                .collect { (status, size, _) ->
                    publishStatus(status)
                    val videoSize = VideoSize(size.first ?: 0, size.second ?: 0)
                    if (videoSize != lastSize) {
                        lastSize = videoSize
                        listeners.forEach { it.onVideoSizeChanged(videoSize) }
                    }
                }
        }
    }

    override val currentItem: PlayerItem? get() = queue.getOrNull(index)
    override val isPlaying: Boolean get() = lastStatus.isPlaying
    override val positionMs: Long
        get() = opening ?: if (ended) durationMs else (state.currentTime * 1000).toLong().coerceAtLeast(0)
    override val durationMs: Long get() = (state.duration * 1000).toLong().coerceAtLeast(0)
    override val status: PlayerStatus get() = lastStatus
    override val videoSize: VideoSize get() = lastSize
    override val selectedSubtitleKey: String? get() = subtitleKey

    override fun addListener(listener: VideoPlayerListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: VideoPlayerListener) {
        listeners.remove(listener)
    }

    override fun setQueue(items: List<PlayerItem>, startIndex: Int, startPositionMs: Long) {
        val old = here()
        queue = items
        index = startIndex.coerceIn(-1, items.lastIndex)
        // As media3 reports replacing the playing video, so its place is kept.
        if (old.videoId != null) jumped(old, PlayerPosition(currentItem?.videoId, startPositionMs), PositionJumpReason.Other)
        listeners.forEach { it.onItemTransition(currentItem, ItemTransitionReason.Other) }
        open(startPositionMs)
    }

    override fun play() {
        playWhenReady = true
        if (opening == null) state.play()
        publishStatus(computeStatus())
    }

    override fun pause() {
        playWhenReady = false
        state.pause()
        publishStatus(computeStatus())
    }

    override fun prepare() {
        if (currentItem != null && lastStatus.state == PlaybackState.Idle) open(positionMs)
    }

    override fun clear() {
        val old = here()
        openJob?.cancel()
        seekJob?.cancel()
        state.stop()
        playWhenReady = false
        queue = emptyList()
        index = -1
        proxy.clear()
        proxyToken = null
        opening = null
        ended = false
        ownError = null
        showSubtitles(null)
        if (old.videoId != null) jumped(old, PlayerPosition(null, 0), PositionJumpReason.Other)
        listeners.forEach { it.onItemTransition(null, ItemTransitionReason.Other) }
        publishStatus(computeStatus())
    }

    override fun seekTo(positionMs: Long) {
        val item = currentItem ?: return
        val duration = durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        val old = here()
        if (opening != null || duration <= 0) {
            // Not loaded yet: start there instead.
            opening = target
            jumped(old, PlayerPosition(item.videoId, target), PositionJumpReason.Seek)
            return
        }
        ended = false
        state.seekTo(target.toFloat() / duration * 1000f)
        seekJob?.cancel()
        seekJob = scope.launch {
            settle()
            val landed = this@DesktopVideoPlayer.positionMs
            jumped(old, PlayerPosition(item.videoId, landed, target.takeIf { it != landed }), PositionJumpReason.Seek)
        }
    }

    override fun seekBack() = seekTo((positionMs - SEEK_INCREMENT_MS).coerceAtLeast(0))
    override fun seekForward() = seekTo(positionMs + SEEK_INCREMENT_MS)

    override fun playItem(index: Int) {
        if (index !in queue.indices) return
        val old = here()
        this.index = index
        jumped(old, PlayerPosition(currentItem?.videoId, 0), PositionJumpReason.Seek)
        listeners.forEach { it.onItemTransition(currentItem, ItemTransitionReason.Seek) }
        open(0)
        play()
    }

    override fun selectSubtitle(key: String?, language: String) {
        subtitlesOff = false
        subtitleLanguage = language
        subtitleKey = key
        showSubtitles(chooseSubtitle(key))
    }

    override fun disableSubtitles() {
        subtitlesOff = true
        showSubtitles(null)
    }

    /** Stops for good; the app is quitting. */
    fun release() {
        openJob?.cancel()
        seekJob?.cancel()
        subtitleJob?.cancel()
        proxy.close()
        state.dispose()
    }

    private fun here() = PlayerPosition(currentItem?.videoId, positionMs)

    /** Loads the current item and starts it at [startMs], playing if [playWhenReady]. */
    private fun open(startMs: Long) {
        openJob?.cancel()
        seekJob?.cancel()
        ended = false
        ownError = null
        val item = currentItem
        if (item == null) {
            opening = null
            state.stop()
            return
        }
        opening = startMs
        val uri = when (val source = item.source) {
            is MediaSource.Local -> source.file.toURI().toString()
            is MediaSource.Remote -> proxy.register(source.url).also { proxyToken = it.token }.url
        }
        state.openUri(uri, InitialPlayerState.PAUSE)
        showSubtitles(if (subtitlesOff) null else chooseSubtitle(null))
        openJob = scope.launch {
            val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                snapshotFlow { state.duration > 0 || state.error != null || ownError != null }.first { it }
            }
            if (loaded == null) ownError = PlayerError.Network
            if (loaded == null || state.error != null || ownError != null) {
                opening = null
                return@launch
            }
            val start = opening ?: 0
            if (start > 0) {
                state.seekTo(start.toFloat() / durationMs * 1000f)
                settle()
            }
            opening = null
            if (playWhenReady) state.play()
        }
    }

    /** Waits for a seek to show its frame, so the position read after is where it landed. */
    private suspend fun settle() {
        delay(SETTLE_MS)
        withTimeoutOrNull(SEEK_TIMEOUT_MS) { snapshotFlow { state.isLoading }.first { !it } }
        delay(SETTLE_MS)
    }

    private fun onItemEnded() {
        if (index + 1 < queue.size) {
            val old = PlayerPosition(currentItem?.videoId, durationMs)
            index++
            jumped(old, PlayerPosition(currentItem?.videoId, 0), PositionJumpReason.Other)
            listeners.forEach { it.onItemTransition(currentItem, ItemTransitionReason.Auto) }
            open(0)
        } else {
            ended = true
            listeners.forEach { it.onEnded() }
            publishStatus(computeStatus())
        }
    }

    private fun computeStatus(): PlayerStatus {
        val error = ownError ?: state.error?.let { PlayerError.Playback(it.toString()) }
        val playbackState = when {
            index < 0 || error != null -> PlaybackState.Idle
            ended -> PlaybackState.Ended
            opening != null || state.isLoading -> PlaybackState.Buffering
            else -> PlaybackState.Ready
        }
        return PlayerStatus(playbackState, playbackState == PlaybackState.Ready && state.isPlaying, error)
    }

    private fun publishStatus(status: PlayerStatus) {
        if (status == lastStatus) return
        val wasPlaying = lastStatus.isPlaying
        lastStatus = status
        if (status.isPlaying != wasPlaying) listeners.forEach { it.onIsPlayingChanged(status.isPlaying) }
        listeners.forEach { it.onStatusChanged(status) }
    }

    private fun jumped(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) =
        listeners.forEach { it.onPositionJump(old, new, reason) }

    /** [key]'s track if this video has it, else the first in the preferred language (as media3 picks). */
    private fun chooseSubtitle(key: String?): SubtitleSource? {
        val tracks = currentItem?.subtitles.orEmpty()
        return tracks.firstOrNull { it.key == key }
            ?: tracks.firstOrNull { it.language.equals(subtitleLanguage, ignoreCase = true) }
            ?: tracks.firstOrNull { it.language.substringBefore('-').equals(subtitleLanguage.substringBefore('-'), ignoreCase = true) }
    }

    private fun showSubtitles(track: SubtitleSource?) {
        subtitleJob?.cancel()
        subtitleKey = track?.key
        cues = emptyList()
        listeners.forEach { it.onSubtitlesChanged() }
        if (track == null) return
        subtitleJob = scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    when (val source = track.source) {
                        is MediaSource.Local -> source.file.readText()
                        is MediaSource.Remote -> client.newCall(Request.Builder().url(source.url).build()).execute().use { response ->
                            if (response.isSuccessful) response.body.string() else null
                        }
                    }
                }.getOrNull()
            }
            cues = text?.let(::parseWebVtt).orEmpty()
        }
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val LOAD_TIMEOUT_MS = 20_000L
        const val SEEK_TIMEOUT_MS = 3_000L
        const val SETTLE_MS = 250L
    }
}
