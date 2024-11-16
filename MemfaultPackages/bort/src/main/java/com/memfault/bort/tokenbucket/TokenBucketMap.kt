package com.memfault.bort.tokenbucket

import kotlin.time.Duration

class TokenBucketMap(
    initialMap: Map<String, TokenBucket>,
    private val maxBuckets: Int,
    private val tokenBucketFactory: TokenBucketFactory,
) {
    private var map = initialMap.mapValues { (_, bucket) ->
        // TokenBucket is mutable -- make copies of our own:
        bucket.copy()
    }.toMutableMap()

    /**
     * Inserts or updates bucket with given key and parameters.
     */
    fun upsertBucket(key: String, capacity: Int? = null, period: Duration? = null): TokenBucket? {
        val capacityToUse = capacity ?: tokenBucketFactory.defaultCapacity
        val periodToUse = period ?: tokenBucketFactory.defaultPeriod
        val bucket = map.get(key)?.reconfigure(capacityToUse, periodToUse)
            ?: tokenBucketFactory.create(capacityToUse, capacityToUse, periodToUse).also {
                if (isFull) {
                    prune()
                    if (isFull) return null
                }
            }

        map.put(key, bucket)
        return bucket
    }

    fun prune() {
        map = map.onEach { (_, bucket) ->
            bucket.feed()
        }.filterValues { bucket ->
            !bucket.isFull
        }.toMutableMap()
    }

    fun toMap() = map.toMap()

    val isFull: Boolean
        get() = map.size >= maxBuckets
}

/** If needed, create new bucket with adjusted parameters, but retaining existing token count.
 *  This path may occur after an update of Bort that changed the capacity and/or period of a map that existed
 *  (had been persisted) before the change:
 */
fun TokenBucket.reconfigure(newCapacity: Int, newPeriod: Duration): TokenBucket =
    if (capacity == newCapacity && period == newPeriod) {
        this
    } else {
        copy(capacity = newCapacity, period = newPeriod, _count = minOf(newCapacity, count))
    }
