package com.memfault.bort.shared

import com.memfault.bort.DevMode
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter.APPLY
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Generate a random jittery delay for server calls.
 */
class JitterDelayProvider @Inject constructor(
    private val jitterDelayConfiguration: JitterDelayConfiguration,
    private val devMode: DevMode,
) {
    fun randomJitterDelay(maxDelay: Duration = DEFAULT_JITTER_DELAY): Duration =
        if (!devMode.isEnabled() && jitterDelayConfiguration.applyJitter() == APPLY) {
            Random.nextLong(0, maxDelay.inWholeMilliseconds).milliseconds
        } else {
            Duration.ZERO
        }

    enum class ApplyJitter {
        APPLY,
        DO_NOT_APPLY,
    }

    companion object {
        private const val DEFAULT_JITTER_DELAY_MINUTES: Long = 15
        private val DEFAULT_JITTER_DELAY = DEFAULT_JITTER_DELAY_MINUTES.minutes
    }
}
