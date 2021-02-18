package com.memfault.bort.tokenbucket

import android.os.SystemClock
import com.memfault.bort.settings.RateLimitingSettings
import kotlin.time.Duration
import kotlin.time.milliseconds

abstract class TokenBucketFactory(
    val defaultCapacity: Int,
    val defaultPeriod: Duration,
) {
    abstract fun create(count: Int, capacity: Int, period: Duration): TokenBucket

    fun create(count: Int? = null, capacity: Int? = null, period: Duration? = null): TokenBucket =
        create(
            count = count ?: defaultCapacity,
            capacity = capacity ?: defaultCapacity,
            period = period ?: defaultPeriod,
        )
}

internal fun realElapsedRealtime(): Duration = SystemClock.elapsedRealtime().milliseconds

class RealTokenBucketFactory(
    defaultCapacity: Int,
    defaultPeriod: Duration
) : TokenBucketFactory(defaultCapacity, defaultPeriod) {
    override fun create(count: Int, capacity: Int, period: Duration): TokenBucket =
        TokenBucket(
            capacity = capacity,
            period = period,
            _count = count,
            _periodStartElapsedRealtime = realElapsedRealtime(),
            elapsedRealtime = ::realElapsedRealtime,
        )

    companion object {
        fun from(rateLimitingSettings: RateLimitingSettings) =
            RealTokenBucketFactory(
                defaultCapacity = rateLimitingSettings.defaultCapacity,
                defaultPeriod = rateLimitingSettings.defaultPeriod.duration
            )
    }
}
