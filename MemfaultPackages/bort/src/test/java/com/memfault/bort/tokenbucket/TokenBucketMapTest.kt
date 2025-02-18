package com.memfault.bort.tokenbucket

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class TokenBucketMapTest {
    lateinit var bucketA: TokenBucket
    lateinit var mockElapsedRealtime: MockElapsedRealtime
    lateinit var tokenBucketFactory: TokenBucketFactory

    @Before
    fun setUp() {
        mockElapsedRealtime = MockElapsedRealtime()
        bucketA = TokenBucket(
            capacity = 3,
            period = 2.milliseconds,
            _count = 1,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime::get,
            mockk(relaxed = true),
        )
        tokenBucketFactory = MockTokenBucketFactory(
            defaultCapacity = 1,
            defaultPeriod = 1.milliseconds,
            mockElapsedRealtime = mockElapsedRealtime,
        )
    }

    @Test
    fun copiesInitialMap() {
        val initialMap = mapOf("x" to bucketA)
        val originalCount = bucketA.count
        val bucketMap = TokenBucketMap(initialMap, maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val bucket = bucketMap.upsertBucket("x", capacity = bucketA.capacity, period = bucketA.period)
        // Mutate bucket's count:
        assertThat(bucket?.take(tag = "test")).isNotNull().isTrue()
        // bucketA should still be untouched:
        assertThat(bucketA.count).isEqualTo(originalCount)
        assertThat(bucket?.count).isEqualTo(bucketA.count - 1)
    }

    @Test
    fun getExisting() {
        val initialMap = mapOf("x" to bucketA)
        val bucketMap = TokenBucketMap(initialMap, maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        assertThat(bucketMap.upsertBucket("x", bucketA.capacity, bucketA.period)).isEqualTo(bucketA)
        assertThat(bucketMap.toMap()).isEqualTo(initialMap)
    }

    @Test
    fun getExistingFullBucketWithLargerCapacity() {
        val fullBucket = bucketA.copy(_count = bucketA.capacity)
        val bucketMap = TokenBucketMap(
            mapOf("x" to fullBucket),
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        )
        // count should be decreased to avoid exceeding the capacity:
        val expectedBucket = fullBucket.copy(capacity = fullBucket.capacity - 1, _count = fullBucket.capacity - 1)
        assertThat(
            bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period),
        ).isEqualTo(expectedBucket)
        assertThat(bucketMap.toMap()).containsOnly("x" to expectedBucket)
    }

    @Test
    fun getExistingBucketWithSmallerCapacity() {
        val bucket = bucketA.copy(_count = bucketA.capacity)
        val bucketMap = TokenBucketMap(mapOf("x" to bucket), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val expectedBucket = bucket.copy(capacity = bucket.capacity + 1)
        assertThat(
            bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period),
        ).isEqualTo(expectedBucket)
        assertThat(bucketMap.toMap()).containsOnly("x" to expectedBucket)
    }

    @Test
    fun getExistingWithDifferentPeriod() {
        val bucketMap = TokenBucketMap(mapOf("x" to bucketA), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val period = bucketA.period + 1.milliseconds
        val expectedBucket = bucketA.copy(period = period)
        assertThat(bucketMap.upsertBucket("x", bucketA.capacity, period)).isEqualTo(expectedBucket)
        assertThat(bucketMap.toMap()).containsOnly("x" to expectedBucket)
    }

    @Test
    fun getNewBucket() {
        val capacity = 3
        val period = 2.milliseconds
        val bucketMap = TokenBucketMap(
            emptyMap(),
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        )
        val expectedBucket = tokenBucketFactory.create(count = capacity, capacity = capacity, period = period)
        assertThat(bucketMap.upsertBucket("x", capacity, period)).isEqualTo(expectedBucket)
        assertThat(bucketMap.toMap()).containsOnly("x" to expectedBucket)
    }

    @Test
    fun getNewBucketMaxReached() {
        val bucketMap = TokenBucketMap(emptyMap(), maxBuckets = 0, tokenBucketFactory = tokenBucketFactory)
        assertThat(bucketMap.upsertBucket("x", 1, 1.milliseconds)).isNull()
    }

    @Test
    fun getNewBucketAfterSuccessfulPruning() {
        val bucketMap = TokenBucketMap(
            mapOf(
                "full" to tokenBucketFactory.create(count = 1, capacity = 1, period = 1.milliseconds),
            ),
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        )
        val capacity = 3
        val expectedBucket = tokenBucketFactory.create(count = capacity, capacity = capacity, period = 1.milliseconds)
        assertThat(
            bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period),
        ).isEqualTo(expectedBucket)
    }

    @Test
    fun getNewWithDefaultParams() {
        val bucketMap = TokenBucketMap(emptyMap(), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val expectedBucket = tokenBucketFactory.create()
        assertThat(bucketMap.upsertBucket("x")).isEqualTo(expectedBucket)
    }

    @Test
    fun getExistingWithDefaultParams() {
        val expectedBucket = tokenBucketFactory.create()
        val bucketMap = TokenBucketMap(
            mapOf(
                "x" to expectedBucket,
            ),
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        )
        assertThat(bucketMap.upsertBucket("x")).isEqualTo(expectedBucket)
    }

    @Test
    fun prune() {
        val notFull = tokenBucketFactory.create(count = 0, capacity = 1, period = 100.milliseconds)
        val bucketMap = TokenBucketMap(
            mapOf(
                "full" to tokenBucketFactory.create(count = 1, capacity = 1, period = 1.milliseconds),
                "fullAfterFeed" to tokenBucketFactory.create(count = 0, capacity = 1, period = 1.milliseconds),
                "notFull" to notFull,
            ),
            maxBuckets = 10,
            tokenBucketFactory = tokenBucketFactory,
        )
        // Move time forward, fullAfterFeed will get count == capacity
        mockElapsedRealtime.now = 2.milliseconds
        bucketMap.prune()
        assertThat(bucketMap.toMap()).containsOnly("notFull" to notFull)
    }
}
