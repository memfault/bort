package com.memfault.bort.tokenbucket

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
) : TokenBucketFactory(defaultCapacity, defaultPeriod) {
    override fun create(count: Int, capacity: Int, period: Duration): TokenBucket =
        TokenBucket(
            capacity = capacity,
            period = period,
            _count = count,
            _periodStartElapsedRealtime = mockElapsedRealtime.get(),
            elapsedRealtime = mockElapsedRealtime::get,
        )
}
