package com.memfault.bort.tokenbucket

import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.DurationAsMillisecondsLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlinx.serialization.Serializable

@Serializable
data class StoredTokenBucketMap(
    private val map: Map<String, StoredTokenBucket> = emptyMap(),
) : Map<String, StoredTokenBucket> by map

@Serializable
data class StoredTokenBucket(
    val count: Int,
    val capacity: Int,
    @Serializable(with = DurationAsMillisecondsLong::class)
    val periodStartElapsedRealtime: BoxedDuration,
    @Serializable(with = DurationAsMillisecondsLong::class)
    val period: BoxedDuration,
)

fun TokenBucket.toStoredTokenBucket(): StoredTokenBucket = StoredTokenBucket(
    count = count,
    capacity = capacity,
    periodStartElapsedRealtime = BoxedDuration(periodStartElapsedRealtime),
    period = BoxedDuration(period),
)

class TokenBucketStore(
    private val storage: TokenBucketStorage,
    private val maxBuckets: Int,
    private val tokenBucketFactory: TokenBucketFactory,
    val elapsedRealtime: () -> Duration = ::realElapsedRealtime,
) {
    private val lock = ReentrantLock()
    private var cachedMap: Map<String, TokenBucket>? = null

    fun handleBoot() = lock.withLock {
        // After booting (BOOT_COMPLETED intent), so either full reboot or system server restart, the
        // periodStartElapsedRealtime needs to be reset. We don't know the elapsedRealtime() value when the reboot
        // happened, so just "restart" to the current elapsedRealtime(). Effectively, the token feeding periods are
        // restarted and thus will the next token "feed" be delayed.
        storage.writeMap(
            StoredTokenBucketMap(
                storage.readMap().mapValues { (_, storedBucket) ->
                    storedBucket.copy(periodStartElapsedRealtime = BoxedDuration(elapsedRealtime()))
                }
            )
        )
    }

    private fun readMap() = cachedMap ?: storage.readMap().mapValues { (_, storedBucket) ->
        with(storedBucket) {
            tokenBucketFactory.create(count, capacity, period.duration)
        }
    }.also {
        cachedMap = it
    }

    private fun writeMap(map: Map<String, TokenBucket>) = storage.writeMap(
        StoredTokenBucketMap(
            map.mapValues { (_, bucket) ->
                bucket.toStoredTokenBucket()
            }
        )
    ).also {
        cachedMap = map
    }

    fun <R> edit(block: (map: TokenBucketMap) -> R): R = lock.withLock {
        val initialMap = readMap()
        val map = TokenBucketMap(
            initialMap = initialMap,
            maxBuckets = maxBuckets,
            tokenBucketFactory = tokenBucketFactory
        )
        return block(map).also {
            val finalMap = map.toMap()
            if (finalMap != initialMap) {
                writeMap(finalMap)
            }
        }
    }
}
