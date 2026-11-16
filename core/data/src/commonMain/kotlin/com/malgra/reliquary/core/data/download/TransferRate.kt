package com.malgra.reliquary.core.data.download

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A download's speed, from how many bytes it had written at a few moments,
 * averaged over the last [windowMillis] so it doesn't jump with every report.
 */
class TransferRate(private val windowMillis: Long = 5_000) {

    private val samples = ArrayDeque<Pair<Long, Long>>()

    /**
     * Notes that [bytes] had been written at [timeMillis] (any steady clock) and
     * returns bytes per second, or null until there's a second's worth to go on.
     */
    fun add(timeMillis: Long, bytes: Long): Long? {
        // Fewer bytes than before means the download started over (say, the file changed).
        if (samples.isNotEmpty() && bytes < samples.last().second) samples.clear()
        samples.addLast(timeMillis to bytes)
        while (samples.size > 2 && timeMillis - samples.first().first > windowMillis) samples.removeFirst()
        val (firstTime, firstBytes) = samples.first()
        val elapsed = timeMillis - firstTime
        return if (elapsed >= MIN_SPAN_MILLIS) (bytes - firstBytes) * 1000 / elapsed else null
    }

    private companion object {
        const val MIN_SPAN_MILLIS = 1_000L
    }
}

/** How long the rest takes at [bytesPerSecond]; null when that's unknown or nothing is arriving. */
fun timeLeft(remainingBytes: Long, bytesPerSecond: Long?): Duration? =
    if (bytesPerSecond == null || bytesPerSecond <= 0 || remainingBytes < 0) null
    else (remainingBytes.toDouble() / bytesPerSecond).seconds
