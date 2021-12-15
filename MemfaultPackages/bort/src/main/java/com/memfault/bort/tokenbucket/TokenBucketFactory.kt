package com.memfault.bort.tokenbucket

import android.os.SystemClock
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.RateLimitingSettings
import kotlin.time.Duration
import kotlin.time.milliseconds

abstract class TokenBucketFactory(
    val defaultCapacity: Int,
    val defaultPeriod: Duration,
) {
    abstract fun create(count: Int, capacity: Int, period: Duration, periodStartElapsedRealtime: Duration?): TokenBucket

    fun create(
        count: Int? = null,
        capacity: Int? = null,
        period: Duration? = null,
        periodStartElapsedRealtime: Duration? = null
    ): TokenBucket =
        create(
            count = count ?: defaultCapacity,
            capacity = capacity ?: defaultCapacity,
            period = period ?: defaultPeriod,
            periodStartElapsedRealtime = periodStartElapsedRealtime,
        )
}

internal fun realElapsedRealtime(): Duration = SystemClock.elapsedRealtime().milliseconds

class RealTokenBucketFactory(
    defaultCapacity: Int,
    defaultPeriod: Duration,
    private val metrics: BuiltinMetricsStore,
) : TokenBucketFactory(defaultCapacity, defaultPeriod) {
    override fun create(
        count: Int,
        capacity: Int,
        period: Duration,
        periodStartElapsedRealtime: Duration?
    ): TokenBucket =
        TokenBucket(
            capacity = capacity,
            period = period,
            _count = count,
            _periodStartElapsedRealtime = periodStartElapsedRealtime ?: realElapsedRealtime(),
            elapsedRealtime = ::realElapsedRealtime,
            metrics = metrics,
        )

    companion object {
        fun from(rateLimitingSettings: RateLimitingSettings, metrics: BuiltinMetricsStore) =
            RealTokenBucketFactory(
                defaultCapacity = rateLimitingSettings.defaultCapacity,
                defaultPeriod = rateLimitingSettings.defaultPeriod.duration,
                metrics = metrics,
            )
    }
}
