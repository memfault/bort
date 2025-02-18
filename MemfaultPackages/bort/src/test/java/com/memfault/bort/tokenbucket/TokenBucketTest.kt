package com.memfault.bort.tokenbucket

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TokenBucketTest {
    var now: Duration = 0.milliseconds
    val mockElapsedRealtime = { now }

    @Before
    fun setUp() {
        now = 0.milliseconds
    }

    @Test
    fun testFeed() {
        val bucket = TokenBucket(
            capacity = 3,
            period = 2.milliseconds,
            _count = 0,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime,
            metrics = mockk(relaxed = true),
        )
        now = 1.milliseconds
        bucket.feed()
        assertThat(bucket.count).isEqualTo(0)
        now = 2.milliseconds
        bucket.feed()
        assertThat(bucket.count).isEqualTo(1)
        now = 10.milliseconds
        bucket.feed()
        assertThat(bucket.count).isEqualTo(3)
    }

    @Test
    fun testTake() {
        val bucket = TokenBucket(
            capacity = 3,
            period = 2.milliseconds,
            _count = 3,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime,
            metrics = mockk(relaxed = true),
        )
        assertThat(bucket.take(n = 4, tag = "test")).isFalse()
        assertThat(bucket.count).isEqualTo(3)
        assertThat(bucket.take(n = 2, tag = "test")).isTrue()
        assertThat(bucket.count).isEqualTo(1)
        assertThat(bucket.take(tag = "test")).isTrue()
        assertThat(bucket.count).isEqualTo(0)
        assertThat(bucket.take(tag = "test")).isFalse()
        assertThat(bucket.count).isEqualTo(0)

        // For convenience, take() also feeds():
        now = 10.milliseconds
        assertThat(bucket.take(tag = "test")).isTrue()
        assertThat(bucket.count).isEqualTo(2)
    }

    @Test
    fun testTakeWithLongPeriod() {
        val bucket = TokenBucket(
            capacity = 1,
            period = 1.milliseconds,
            _count = 0,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime,
            metrics = mockk(relaxed = true),
        )

        now = 5.milliseconds
        assertThat(bucket.take(1, tag = "test")).isTrue()
        assertThat(bucket.take(1, tag = "test")).isFalse()

        // Check that the externally observable value did change
        assertThat(bucket.periodStartElapsedRealtime).isEqualTo(5.milliseconds)
    }

    @Test
    fun testFeedRate() {
        val bucket = TokenBucket(
            capacity = 10,
            period = 1.milliseconds,
            _count = 0,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime,
            metrics = mockk(relaxed = true),
        )

        now = 10.milliseconds
        bucket.feed()
        assertThat(bucket.count).isEqualTo(10)

        bucket.take(10, tag = "test")
        assertThat(bucket.count).isEqualTo(0)

        repeat(10) {
            now += 1.milliseconds
            bucket.feed()
            assertThat(bucket.count).isEqualTo(it + 1)
        }
    }
}
