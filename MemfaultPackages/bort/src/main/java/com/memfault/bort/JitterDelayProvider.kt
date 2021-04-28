package com.memfault.bort

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.milliseconds

/**
 * Generate a random jittery delay for server calls.
 */
class JitterDelayProvider(
    private val applyJitter: Boolean
) {
    fun randomJitterDelay() = if (applyJitter) Random.nextLong(0, MAX_JITTER_DELAY_MS).milliseconds else ZERO

    companion object {
        private const val MAX_JITTER_DELAY_MINUTES: Long = 15
        private val MAX_JITTER_DELAY_MS = TimeUnit.MINUTES.toMillis(MAX_JITTER_DELAY_MINUTES)
    }
}
