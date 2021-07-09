package com.memfault.bort.tokenbucket

import kotlin.time.milliseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenBucketMapTest {
    lateinit var bucketA: TokenBucket
    lateinit var mockElapsedRealtime: MockElapsedRealtime
    lateinit var tokenBucketFactory: TokenBucketFactory

    @BeforeEach
    fun setUp() {
        mockElapsedRealtime = MockElapsedRealtime()
        bucketA = TokenBucket(
            capacity = 3,
            period = 2.milliseconds,
            _count = 1,
            _periodStartElapsedRealtime = 0.milliseconds,
            elapsedRealtime = mockElapsedRealtime::get,
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
        assertEquals(true, bucket?.take(tag = "test"))
        // bucketA should still be untouched:
        assertEquals(originalCount, bucketA.count)
        assertEquals(bucketA.count - 1, bucket?.count)
    }

    @Test
    fun getExisting() {
        val initialMap = mapOf("x" to bucketA)
        val bucketMap = TokenBucketMap(initialMap, maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        assertEquals(bucketA, bucketMap.upsertBucket("x", bucketA.capacity, bucketA.period))
        assertEquals(initialMap, bucketMap.toMap())
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
        assertEquals(expectedBucket, bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period))
        assertEquals(mapOf("x" to expectedBucket), bucketMap.toMap())
    }

    @Test
    fun getExistingBucketWithSmallerCapacity() {
        val bucket = bucketA.copy(_count = bucketA.capacity)
        val bucketMap = TokenBucketMap(mapOf("x" to bucket), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val expectedBucket = bucket.copy(capacity = bucket.capacity + 1)
        assertEquals(expectedBucket, bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period))
        assertEquals(mapOf("x" to expectedBucket), bucketMap.toMap())
    }

    @Test
    fun getExistingWithDifferentPeriod() {
        val bucketMap = TokenBucketMap(mapOf("x" to bucketA), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val period = bucketA.period + 1.milliseconds
        val expectedBucket = bucketA.copy(period = period)
        assertEquals(expectedBucket, bucketMap.upsertBucket("x", bucketA.capacity, period))
        assertEquals(mapOf("x" to expectedBucket), bucketMap.toMap())
    }

    @Test
    fun getNewBucket() {
        val capacity = 3
        val period = 2.milliseconds
        val bucketMap = TokenBucketMap(
            emptyMap(),
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory
        )
        val expectedBucket = tokenBucketFactory.create(count = capacity, capacity = capacity, period = period)
        assertEquals(expectedBucket, bucketMap.upsertBucket("x", capacity, period))
        assertEquals(mapOf("x" to expectedBucket), bucketMap.toMap())
    }

    @Test
    fun getNewBucketMaxReached() {
        val bucketMap = TokenBucketMap(emptyMap(), maxBuckets = 0, tokenBucketFactory = tokenBucketFactory)
        assertEquals(null, bucketMap.upsertBucket("x", 1, 1.milliseconds))
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
        assertEquals(expectedBucket, bucketMap.upsertBucket("x", expectedBucket.capacity, expectedBucket.period))
    }

    @Test
    fun getNewWithDefaultParams() {
        val bucketMap = TokenBucketMap(emptyMap(), maxBuckets = 1, tokenBucketFactory = tokenBucketFactory)
        val expectedBucket = tokenBucketFactory.create()
        assertEquals(expectedBucket, bucketMap.upsertBucket("x"))
    }

    @Test
    fun getExistingWithDefaultParams() {
        val expectedBucket = tokenBucketFactory.create()
        val bucketMap = TokenBucketMap(
            mapOf(
                "x" to expectedBucket,
            ),
            maxBuckets = 1, tokenBucketFactory = tokenBucketFactory
        )
        assertEquals(expectedBucket, bucketMap.upsertBucket("x"))
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
        assertEquals(
            mapOf(
                "notFull" to notFull,
            ),
            bucketMap.toMap()
        )
    }
}
