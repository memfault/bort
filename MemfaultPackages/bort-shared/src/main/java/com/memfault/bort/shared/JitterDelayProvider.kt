package com.memfault.bort.shared

import java.time.Duration
import java.time.Duration.ZERO
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Generate a random jittery delay for server calls.
 */
class JitterDelayProvider(
    private val applyJitter: Boolean
) {
    // Uses Java's Duration type: Kotlin's Duration is an inline class which causes a crash when a return value is
    // accessed from another module.
    fun randomJitterDelay(): Duration =
        if (applyJitter) Duration.ofMillis(Random.nextLong(0, MAX_JITTER_DELAY_MS)) else ZERO

    companion object {
        private const val MAX_JITTER_DELAY_MINUTES: Long = 15
        private val MAX_JITTER_DELAY_MS = TimeUnit.MINUTES.toMillis(MAX_JITTER_DELAY_MINUTES)
    }
}
