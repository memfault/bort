package com.memfault.bort.shared

import com.memfault.bort.DevMode
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter.APPLY
import java.time.Duration
import java.time.Duration.ZERO
import javax.inject.Inject
import kotlin.random.Random

/**
 * Generate a random jittery delay for server calls.
 */
class JitterDelayProvider @Inject constructor(
    private val jitterDelayConfiguration: JitterDelayConfiguration,
    private val devMode: DevMode,
) {
    // Uses Java's Duration type: Kotlin's Duration is an inline class which causes a crash when a return value is
    // accessed from another module.
    fun randomJitterDelay(maxDelay: Duration = MAX_JITTER_DELAY): Duration =
        if (!devMode.isEnabled() && jitterDelayConfiguration.applyJitter() == APPLY) {
            Duration.ofMillis(Random.nextLong(0, maxDelay.toMillis()))
        } else {
            ZERO
        }

    enum class ApplyJitter {
        APPLY,
        DO_NOT_APPLY,
    }

    companion object {
        private const val MAX_JITTER_DELAY_MINUTES: Long = 15
        private val MAX_JITTER_DELAY = Duration.ofMinutes(MAX_JITTER_DELAY_MINUTES)
    }
}
