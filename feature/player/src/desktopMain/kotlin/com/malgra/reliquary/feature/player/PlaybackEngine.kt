package com.malgra.reliquary.feature.player

/**
 * What decodes and plays one file for [DesktopVideoPlayer]: libVLC in the app
 * ([VlcEngine]), a fake in tests, so the player's logic runs without native code.
 */
internal interface PlaybackEngine {
    /** Gets the engine's events, on any thread. */
    var listener: Listener?

    /** Where playback is, read at any time. Zero while unknown. */
    val positionMs: Long
    val durationMs: Long

    /** Loads [uri] and starts at [startMs], paused if [paused]. Replaces whatever was loaded. */
    fun open(uri: String, startMs: Long, paused: Boolean)
    fun play()
    fun pause()

    /** Unloads the media. */
    fun stop()

    /** Seeks exactly (libVLC doesn't snap to keyframes). */
    fun seekTo(positionMs: Long)
    fun release()

    interface Listener {
        /** Frames are showing and time moves on. */
        fun onPlaying()
        fun onPaused()

        /** Playback reached [positionMs]: frequent while it plays. */
        fun onTime(positionMs: Long)

        /** The length became known. */
        fun onLength(durationMs: Long)
        fun onVideoSize(width: Int, height: Int)

        /** Waiting for data: [percent] of the cache is filled. */
        fun onBuffering(percent: Float)
        fun onEnded()
        fun onError(message: String)
    }
}

/** Opens an engine, or says why there's none (e.g. the VLC libraries are missing). */
internal fun interface EngineFactory {
    fun create(): Result<PlaybackEngine>
}
