package com.malgra.reliquary.core.data

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * WorkManager's exponential backoff, for desktop's in-process jobs: runs
 * [attempt] until it returns true (finished, for good or bad), waiting
 * [initial] after the first failure and twice as long after each one after
 * that, never more than 5 hours.
 */
suspend fun retryWithBackoff(initial: Duration, attempt: suspend () -> Boolean) {
    var wait = initial
    while (!attempt()) {
        delay(wait)
        wait = (wait * 2).coerceAtMost(MAX_BACKOFF)
    }
}

private val MAX_BACKOFF = 5.hours
