package com.memfault.bort.tokenbucket

import com.memfault.bort.metrics.BuiltinMetricsStore
import io.mockk.mockk
import kotlin.time.Duration
import kotlin.time.milliseconds

class MockElapsedRealtime {
    var now: Duration = 0.milliseconds
    fun get(): Duration = now
}

class MockTokenBucketFactory(
    defaultCapacity: Int,
    defaultPeriod: Duration,
    val mockElapsedRealtime: MockElapsedRealtime = MockElapsedRealtime(),
    val metrics: BuiltinMetricsStore = mockk(relaxed = true),
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
            _periodStartElapsedRealtime = mockElapsedRealtime.get(),
            elapsedRealtime = mockElapsedRealtime::get,
            metrics = metrics,
        )
}
