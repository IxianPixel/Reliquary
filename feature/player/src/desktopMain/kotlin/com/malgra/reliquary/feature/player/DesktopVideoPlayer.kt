package com.malgra.reliquary.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.malgra.reliquary.core.network.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * [VideoPlayer] on a [PlaybackEngine] (libVLC in the app). The engine plays one
 * file at a time, so the queue is kept here; server media goes through
 * [MediaProxy] for its credentials. The engine is made on the first open, so a
 * missing libVLC only matters once something is played.
 *
 * Everything runs on [scope]'s main thread; engine events are moved there.
 * RELIQUARY_PLAYER_DEBUG=1 in the environment logs every media request.
 */
internal class DesktopVideoPlayer(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val engines: EngineFactory = VlcEngine.Factory,
    private val clock: () -> Long = System::currentTimeMillis,
) : VideoPlayer {

    private val proxy = MediaProxy(client, verbose = System.getenv("RELIQUARY_PLAYER_DEBUG") != null)
    private val listeners = CopyOnWriteArrayList<VideoPlayerListener>()

    private var engine: PlaybackEngine? = null
    private var queue = emptyList<PlayerItem>()
    private var index = -1
    private var proxyToken: String? = null

    /** Set while an item is opening: where it starts, reported as the position until it shows. */
    private var opening: Long? = null
    private var playing = false
    private var buffering = false
    private var ended = false
    private var error: PlayerError? = null

    /** The current item has shown a picture: after that, a reported problem only counts if playback stops. */
    private var started = false
    private var lastTime = -1L
    private var suspectJob: Job? = null

    /** The last seek, reported until the engine's clock catches up with it. */
    private var lastSeek: Long? = null
    private var lastSeekAt = 0L

    private var startJob: Job? = null
    private var subtitleJob: Job? = null

    /** An open not yet handed to the engine: it waits a turn, so a play() right after it (as PlayerSheet does) opens unpaused. */
    private var pendingOpen: Job? = null

    private var lastStatus = PlayerStatus(PlaybackState.Idle, false, null)
    private var lastSize = VideoSize.Unknown

    /** The subtitles asked for: off, or a language to prefer (and a track key for this video). */
    private var subtitlesOff = true
    private var subtitleLanguage = ""
    private var subtitleKey: String? = null

    /** The cues of the subtitle track showing, for [SubtitleOverlay]. */
    internal var cues by mutableStateOf(emptyList<SubtitleCue>())
        private set

    /** The pictures, once libVLC is playing something. */
    internal val frames: VideoFrames? get() = (engine as? VlcEngine)?.frames

    init {
        proxy.onUpstreamError = { token, status ->
            scope.launch {
                if (token != proxyToken) return@launch
                fail(
                    when (status) {
                        401, 403 -> PlayerError.SignedOut
                        404 -> PlayerError.NotFound
                        null -> PlayerError.Network
                        else -> PlayerError.Playback("HTTP $status")
                    },
                    "the server answered ${status ?: "nothing"}",
                    source = "the media proxy",
                )
            }
        }
    }

    override var playWhenReady = false
        private set
    override val currentItem: PlayerItem? get() = queue.getOrNull(index)
    override val isPlaying: Boolean get() = lastStatus.isPlaying
    override val durationMs: Long get() = engine?.durationMs ?: 0
    override val status: PlayerStatus get() = lastStatus
    override val videoSize: VideoSize get() = lastSize
    override val selectedSubtitleKey: String? get() = subtitleKey

    override val positionMs: Long
        get() {
            opening?.let { return it }
            if (ended) return durationMs
            val position = engine?.positionMs ?: 0
            val seek = lastSeek ?: return position
            // libVLC's clock moves to a seek's target a moment after it.
            return if (clock() - lastSeekAt < SEEK_CATCH_UP_MS && abs(position - seek) > SEEK_TOLERANCE_MS) seek else position
        }

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
        if (currentItem != null && error == null) {
            if (pendingOpen == null) engine?.play()
            watchStart()
        }
        publish()
    }

    override fun pause() {
        playWhenReady = false
        engine?.pause()
        startJob?.cancel()
        publish()
    }

    override fun prepare() {
        if (currentItem != null && lastStatus.state == PlaybackState.Idle) open(positionMs)
    }

    override fun clear() {
        val old = here()
        pendingOpen?.cancel()
        pendingOpen = null
        engine?.stop()
        startJob?.cancel()
        playWhenReady = false
        queue = emptyList()
        index = -1
        proxy.clear()
        proxyToken = null
        resetPlayback()
        showSubtitles(null)
        if (old.videoId != null) jumped(old, PlayerPosition(null, 0), PositionJumpReason.Other)
        listeners.forEach { it.onItemTransition(null, ItemTransitionReason.Other) }
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val item = currentItem ?: return
        val duration = durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        val old = here()
        if (opening != null) {
            // Not showing yet: it starts there instead, once loaded.
            opening = target
        } else {
            ended = false
            engine?.seekTo(target)
            lastSeek = target
            lastSeekAt = clock()
        }
        // libVLC seeks exactly, so the target is where it lands.
        jumped(old, PlayerPosition(item.videoId, target), PositionJumpReason.Seek)
        publish()
    }

    override fun seekBack() = seekTo((positionMs - SEEK_INCREMENT_MS).coerceAtLeast(0))
    override fun seekForward() = seekTo(positionMs + SEEK_INCREMENT_MS)

    override fun playItem(index: Int) {
        if (index !in queue.indices) return
        val old = here()
        this.index = index
        jumped(old, PlayerPosition(currentItem?.videoId, 0), PositionJumpReason.Seek)
        listeners.forEach { it.onItemTransition(currentItem, ItemTransitionReason.Seek) }
        playWhenReady = true
        open(0)
    }

    override fun selectSubtitle(key: String?, language: String) {
        subtitlesOff = false
        subtitleLanguage = language
        showSubtitles(chooseSubtitle(key))
    }

    override fun disableSubtitles() {
        subtitlesOff = true
        showSubtitles(null)
    }

    /** Stops for good; the app is quitting. */
    fun release() {
        pendingOpen?.cancel()
        startJob?.cancel()
        subtitleJob?.cancel()
        engine?.listener = null
        engine?.release()
        engine = null
        proxy.close()
    }

    private fun here() = PlayerPosition(currentItem?.videoId, positionMs)

    private fun resetPlayback() {
        suspectJob?.cancel()
        started = false
        lastTime = -1L
        opening = null
        playing = false
        buffering = false
        ended = false
        error = null
        lastSeek = null
    }

    /** Loads the current item and starts it at [startMs], playing if [playWhenReady]. */
    private fun open(startMs: Long) {
        startJob?.cancel()
        pendingOpen?.cancel()
        pendingOpen = null
        resetPlayback()
        val item = currentItem
        if (item == null) {
            engine?.stop()
            publish()
            return
        }
        val engine = this.engine ?: engines.create().getOrElse {
            fail(PlayerError.Unavailable, it.message ?: "no engine", source = "libVLC discovery")
            return
        }.also {
            it.listener = EngineEvents()
            this.engine = it
        }
        opening = startMs
        val uri = when (val source = item.source) {
            is MediaSource.Local -> localMediaUri(source.file)
            is MediaSource.Remote -> proxy.register(source.url).also { proxyToken = it.token }.url
        }
        pendingOpen = scope.launch {
            pendingOpen = null
            // A seek asked for meanwhile moved the start.
            engine.open(uri, opening ?: startMs, paused = !playWhenReady)
        }
        showSubtitles(if (subtitlesOff) null else chooseSubtitle(null))
        watchStart()
        publish()
    }

    /**
     * Turns a start that never comes into an error. libVLC reports most
     * failures itself; this catches a stream that loads and then stalls.
     */
    private fun watchStart() {
        startJob?.cancel()
        if (!playWhenReady || playing) return
        startJob = scope.launch {
            delay(START_TIMEOUT_MS)
            if (playWhenReady && !playing && error == null && !ended) {
                fail(PlayerError.Playback("didn't start"), "nothing played for ${START_TIMEOUT_MS / 1000} s", source = "the start watchdog")
            }
        }
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
            playing = false
            listeners.forEach { it.onEnded() }
            publish()
        }
    }

    /** libVLC's events, on its threads: each is handled on the main thread. */
    private inner class EngineEvents : PlaybackEngine.Listener {
        private fun onMain(block: () -> Unit) {
            scope.launch { block() }
        }

        override fun onPlaying() = onMain {
            playing = true
            buffering = false
            started = true
            startJob?.cancel()
            showStarted()
            recovered()
            publish()
        }

        override fun onTime(positionMs: Long) = onMain {
            if (lastTime >= 0 && positionMs != lastTime) {
                started = true
                recovered()
            }
            lastTime = positionMs
        }

        override fun onPaused() = onMain {
            playing = false
            showStarted()
            publish()
        }

        override fun onLength(durationMs: Long) = onMain { publish() }

        override fun onVideoSize(width: Int, height: Int) = onMain {
            val size = VideoSize(width, height)
            if (size != lastSize) {
                lastSize = size
                listeners.forEach { it.onVideoSizeChanged(size) }
            }
        }

        override fun onBuffering(percent: Float) = onMain {
            buffering = percent < 100f
            publish()
        }

        override fun onEnded() = onMain { onItemEnded() }

        override fun onError(message: String) = onMain { fail(PlayerError.Playback(message), message, source = "libVLC") }
    }

    /** The item is showing: a seek asked for while it opened is made now. */
    private fun showStarted() {
        val start = opening ?: return
        opening = null
        val engineAt = engine?.positionMs ?: 0
        if (abs(engineAt - start) > SEEK_TOLERANCE_MS) {
            engine?.seekTo(start)
            lastSeek = start
            lastSeekAt = clock()
        }
    }

    /**
     * [error] from [source]. Before the picture shows it stops playback; after,
     * libVLC and the proxy report problems it gets past (a dropped range, a
     * probe past the end), so it only shows if playback then stops moving.
     */
    private fun fail(error: PlayerError, why: String, source: String) {
        val id = currentItem?.videoId
        if (started && error != PlayerError.Unavailable) {
            Log.w(TAG, "$source reported a problem while playing $id ($why); shown only if playback stops")
            val at = engine?.positionMs
            suspectJob?.cancel()
            suspectJob = scope.launch {
                delay(SUSPECT_MS)
                if (playWhenReady && engine?.positionMs == at && this@DesktopVideoPlayer.error == null) {
                    Log.w(TAG, "Playback of $id stopped after $source reported $why")
                    this@DesktopVideoPlayer.error = error
                    publish()
                }
            }
            return
        }
        Log.w(TAG, "Can't play $id: $source reported $why")
        this.error = error
        startJob?.cancel()
        publish()
    }

    /** Playback moves on: whatever was reported didn't stop it. */
    private fun recovered() {
        suspectJob?.cancel()
        val shown = error ?: return
        if (shown == PlayerError.Unavailable) return
        Log.w(TAG, "Playing ${currentItem?.videoId} after all; clearing $shown")
        error = null
        publish()
    }

    private fun publish() {
        val playbackState = when {
            index < 0 || error != null -> PlaybackState.Idle
            ended -> PlaybackState.Ended
            opening != null || buffering -> PlaybackState.Buffering
            else -> PlaybackState.Ready
        }
        // libVLC reports buffering often while it plays; it doesn't count as stopping (ProgressTracker saves on each stop).
        val status = PlayerStatus(playbackState, playing && opening == null && (playbackState == PlaybackState.Ready || playbackState == PlaybackState.Buffering), error)
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
        const val TAG = "DesktopVideoPlayer"
        const val SEEK_INCREMENT_MS = 10_000L
        const val START_TIMEOUT_MS = 20_000L

        /** How long playback may stand still after a reported problem before it's shown. */
        const val SUSPECT_MS = 8_000L
        const val SEEK_CATCH_UP_MS = 1_000L
        const val SEEK_TOLERANCE_MS = 1_500L
    }
}

/**
 * The URI libVLC opens a local file by. File.toURI() gives file:/path, which libVLC reads as a path
 * relative to its working directory, so the file never opens. Path.toUri() gives file:///path with
 * spaces and non-ASCII characters percent-encoded, and file:///C:/... for a Windows drive.
 */
internal fun localMediaUri(file: File): String = file.absoluteFile.toPath().toUri().toString()
