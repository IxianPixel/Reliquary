package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.network.log.Log
import com.sun.jna.NativeLibrary
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/**
 * [PlaybackEngine] on libVLC, through vlcj. libVLC decodes into memory (its
 * vmem output) and [frames] hands the latest picture to Compose; audio goes
 * straight to the system. It plays what ExoPlayer does on the phone (H.264,
 * VP9, AV1, Opus), unlike AVFoundation.
 */
internal class VlcEngine private constructor(private val factory: MediaPlayerFactory) : PlaybackEngine {

    val frames = VideoFrames()
    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer()

    override var listener: PlaybackEngine.Listener? = null

    init {
        frames.onSize = { width, height -> listener?.onVideoSize(width, height) }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(frames, frames, true))
        // Called on libVLC's threads; the listener must not call back into libVLC from them.
        player.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    // The decoder's buffer is padded (1088 rows for 1080p) and libVLC reports that as the
                    // display size too; the picture's own size comes from the video track. submit() keeps
                    // the call off this event thread, where calling into libVLC would deadlock.
                    mediaPlayer.submit { mediaPlayer.video().videoDimension()?.let { frames.crop(it.width, it.height) } }
                    listener?.onPlaying()
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    listener?.onPaused()
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    listener?.onTime(newTime)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    listener?.onLength(newLength)
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    listener?.onBuffering(newCache)
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    listener?.onEnded()
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    listener?.onError("libVLC couldn't play the media")
                }
            },
        )
    }

    override val positionMs: Long get() = player.status().time().coerceAtLeast(0)
    override val durationMs: Long get() = player.status().length().coerceAtLeast(0)

    override fun open(uri: String, startMs: Long, paused: Boolean) {
        val options = buildList {
            if (startMs > 0) add(":start-time=" + String.format(Locale.ROOT, "%.3f", startMs / 1000.0))
            if (paused) add(":start-paused")
        }
        frames.forgetSize()
        player.media().play(uri, *options.toTypedArray())
    }

    override fun play() = player.controls().play()
    override fun pause() = player.controls().setPause(true)
    override fun stop() = player.controls().stop()
    override fun seekTo(positionMs: Long) = player.controls().setTime(positionMs)

    override fun release() {
        player.release()
        factory.release()
    }

    companion object {
        /** libVLC as the app ships it; a failure says the libraries are missing. */
        val Factory = EngineFactory {
            VlcLibraries.load().map { VlcEngine(MediaPlayerFactory(*VLC_ARGS)) }
        }

        // Subtitles are ours (SubtitleOverlay); no on-screen titles or messages from VLC.
        private val VLC_ARGS = arrayOf("--quiet", "--no-video-title-show", "--no-osd", "--no-spu", "--no-sub-autodetect-file")
    }
}

/**
 * The latest decoded picture. libVLC writes BGRA into a buffer padded past the
 * picture (1920×1088 for 1080p), so only the display size is shown.
 */
internal class VideoFrames : BufferFormatCallback, RenderCallback {
    private val lock = Any()
    private var pixels = ByteArray(0)
    private var bufferWidth = 0
    private var displayWidth = 0
    private var displayHeight = 0

    /** The picture's own size, once known; may arrive before the buffer does. */
    private var visibleWidth = 0
    private var visibleHeight = 0

    /** Goes up with each new picture. */
    @Volatile var version = 0L
        private set

    var onSize: ((width: Int, height: Int) -> Unit)? = null

    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        synchronized(lock) {
            bufferWidth = sourceWidth
            pixels = ByteArray(sourceWidth * sourceHeight * 4)
            displayWidth = if (visibleWidth > 0) minOf(visibleWidth, sourceWidth) else sourceWidth
            displayHeight = if (visibleHeight > 0) minOf(visibleHeight, sourceHeight) else sourceHeight
        }
        onSize?.invoke(displayWidth, displayHeight)
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    // libVLC reports the padded size here too; the picture's own size comes through crop().
    override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit

    /** A new video: its size isn't known yet. */
    fun forgetSize() = synchronized(lock) {
        visibleWidth = 0
        visibleHeight = 0
    }

    /** Shows [width]×[height] of the buffer, the picture without its padding. */
    fun crop(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val changed = synchronized(lock) {
            visibleWidth = width
            visibleHeight = height
            if (bufferWidth == 0) return // applied when the buffer arrives
            val w = minOf(width, bufferWidth)
            val h = minOf(height, pixels.size / 4 / bufferWidth)
            (w != displayWidth || h != displayHeight).also {
                displayWidth = w
                displayHeight = h
            }
        }
        if (changed) onSize?.invoke(displayWidth, displayHeight)
    }

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    override fun lock(mediaPlayer: MediaPlayer) = Unit
    override fun unlock(mediaPlayer: MediaPlayer) = Unit

    override fun display(
        mediaPlayer: MediaPlayer,
        nativeBuffers: Array<out ByteBuffer>,
        bufferFormat: BufferFormat,
        displayWidth: Int,
        displayHeight: Int,
    ) {
        synchronized(lock) {
            val buffer = nativeBuffers[0]
            buffer.rewind()
            buffer.get(pixels, 0, minOf(pixels.size, buffer.remaining()))
        }
        version++
    }

    /** Copies the latest picture into [bitmap], cropped to the display size. False before the first one. */
    fun copyInto(bitmap: Bitmap): Boolean = synchronized(lock) {
        if (displayWidth == 0) return false
        val info = ImageInfo(displayWidth, displayHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        // The row stride is the padded buffer's, so the padding is never read.
        bitmap.installPixels(info, pixels, bufferWidth * 4)
    }
}

/**
 * Finds libVLC: the copy the app ships, in the directory the system property
 * [DIR_PROPERTY] names. macOS and Windows never fall back to an installed VLC,
 * so they always run the pinned build; Linux uses the system's libvlc unless
 * the property is set.
 */
internal object VlcLibraries {
    const val DIR_PROPERTY = "reliquary.vlc.dir"
    private const val TAG = "VlcLibraries"

    private val result: Result<Unit> by lazy { find() }

    fun load(): Result<Unit> = result

    private fun find(): Result<Unit> {
        val dir = System.getProperty(DIR_PROPERTY)?.let(::File)
        val found = when {
            dir != null -> NativeDiscovery(BundledVlc(dir)).discover()
            RuntimeUtil.isNix() -> NativeDiscovery().discover()
            else -> false
        }
        if (found) return Result.success(Unit)
        val where = dir?.path ?: "(${DIR_PROPERTY} not set)"
        Log.e(TAG, "The VLC libraries aren't in $where")
        return Result.failure(IllegalStateException("The VLC libraries are missing: $where"))
    }

    /** The one directory; libvlccore is loaded first, as libvlc needs it. */
    private class BundledVlc(private val dir: File) : NativeDiscoveryStrategy {
        override fun supported() = true

        override fun discover(): String? =
            dir.path.takeIf { File(dir, System.mapLibraryName("vlc")).isFile || File(dir, "libvlc.dll").isFile }

        override fun onFound(path: String): Boolean {
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
            NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
            return true
        }

        override fun onSetPluginPath(path: String): Boolean {
            val plugins = File(path, "plugins").path
            return if (RuntimeUtil.isWindows()) {
                LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$plugins") == 0
            } else {
                LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", plugins, 1) == 0
            }
        }
    }
}
