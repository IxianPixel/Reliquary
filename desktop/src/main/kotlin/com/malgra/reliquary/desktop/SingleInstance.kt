package com.malgra.reliquary.desktop

import com.malgra.reliquary.core.network.log.Log
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps one copy of the app running: two would fight over the database and the downloads.
 * The first copy holds a lock file and listens on a Unix domain socket beside it (a file,
 * so no network port is opened); a later copy asks it to show its window, then quits.
 */
class SingleInstance private constructor(
    private val lock: FileLock,
    private val server: ServerSocketChannel?,
) : AutoCloseable {

    /** Called on a background thread when another copy is started. */
    @Volatile var onShowRequest: () -> Unit = {}

    init {
        server?.let { channel ->
            thread(isDaemon = true, name = "single-instance") {
                while (channel.isOpen) {
                    try {
                        channel.accept().use { it.read(ByteBuffer.allocate(SHOW.size)) }
                        onShowRequest()
                    } catch (_: IOException) {
                        // Closed on quit, or a copy that gave up early: keep listening while open.
                    }
                }
            }
        }
    }

    override fun close() {
        server?.close()
        lock.release()
        lock.channel().close()
    }

    companion object {
        private const val TAG = "SingleInstance"
        private val SHOW = "show".toByteArray()

        /**
         * Takes [dir]'s lock, or, when another copy holds it, asks that copy to show
         * its window and returns null: the caller should quit.
         */
        fun acquire(dir: Path): SingleInstance? {
            Files.createDirectories(dir)
            val socket = dir.resolve("reliquary.sock")
            val channel = FileChannel.open(dir.resolve("reliquary.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // held by this process already
            }
            if (lock == null) {
                channel.close()
                askToShow(socket)
                return null
            }
            return SingleInstance(lock, listen(socket))
        }

        /** Null when the socket can't be made (e.g. a path too long for one); only the hand-over is lost. */
        private fun listen(socket: Path): ServerSocketChannel? = try {
            Files.deleteIfExists(socket) // left by a copy that crashed; we hold the lock, so it's ours
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        } catch (e: IOException) {
            Log.w(TAG, "Can't listen for other copies", e)
            null
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Can't listen for other copies", e)
            null
        }

        private fun askToShow(socket: Path) {
            try {
                SocketChannel.open(UnixDomainSocketAddress.of(socket)).use { it.write(ByteBuffer.wrap(SHOW)) }
            } catch (e: IOException) {
                // The other copy is still starting, and its window will appear anyway.
                Log.i(TAG, "The running copy didn't answer", e)
            }
        }
    }
}
