package com.memfault.bort.tokenbucket

import androidx.annotation.VisibleForTesting
import com.memfault.bort.DevMode
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.DurationAsMillisecondsLong
import kotlinx.serialization.Serializable
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Qualifier
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

@Qualifier
annotation class BugReportRequestStore

@Qualifier
annotation class Reboots

@Qualifier
annotation class SelinuxViolations

@Qualifier
annotation class BugReportPeriodic

@Qualifier
annotation class Tombstone

@Qualifier
annotation class JavaException

@Qualifier
annotation class Wtf

@Qualifier
annotation class WtfTotal

@Qualifier
annotation class Anr

@Qualifier
annotation class Kmsg

@Qualifier
annotation class Other

@Qualifier
annotation class StructuredLog

@Qualifier
annotation class HighResMetricsFile

@Qualifier
annotation class KernelOops

@Qualifier
annotation class Logcat

@Qualifier
annotation class MetricsCollection

@Qualifier
annotation class MetricReportStore

@Qualifier
annotation class MarDropbox

@Qualifier
annotation class ContinuousLogFile

@Qualifier
annotation class SessionMetrics

interface TokenBucketStore {
    fun handleLinuxReboot(previousUptime: Duration)

    /**
     * Convenience method that can be used in the case there's only need for
     * a single key / bucket in the store.
     */
    fun takeSimple(key: String = "_", n: Int = 1, tag: String): Boolean

    /**
     * For testing purposes: resets all token buckets.
     */
    fun reset()
}

class RealTokenBucketStore(
    private val storage: TokenBucketStorage,
    private val getMaxBuckets: () -> Int,
    private val getTokenBucketFactory: () -> TokenBucketFactory,
    private val devMode: DevMode,
) : TokenBucketStore {
    private val lock = ReentrantLock()
    private var cachedMap: Map<String, TokenBucket>? = null

    override fun handleLinuxReboot(previousUptime: Duration) = lock.withLock {
        // After rebooting Linux, the
        // periodStartElapsedRealtime needs to be reset. We don't know the elapsedRealtime() value when the reboot
        // happened, so just "restart" to the current elapsedRealtime(). Effectively, the token feeding periods are
        // restarted and thus will the next token "feed" be delayed.
        storage.writeMap(
            StoredTokenBucketMap(
                storage.readMap().mapValues { (key, storedBucket) ->
                    // Time period in previous boot uptime, since last token was fed.
                    val previousBootTimeLeftOver = previousUptime - storedBucket.periodStartElapsedRealtime.duration
                    Logger.d(
                        "handleLinuxReboot tokenBucket key=$key " +
                            "previousBootTimeLeftOver=$previousBootTimeLeftOver",
                    )
                    Logger.logEvent(
                        "handleLinuxReboot tokenBucket key=$key " +
                            "previousBootTimeLeftOver=$previousBootTimeLeftOver",
                    )
                    val newValue = -previousBootTimeLeftOver.coerceAtLeast(Duration.ZERO) - BOOT_TIME
                    storedBucket.copy(periodStartElapsedRealtime = BoxedDuration(newValue))
                },
            ),
        )
    }

    override fun reset() = lock.withLock {
        storage.writeMap(StoredTokenBucketMap())
        cachedMap = null
    }

    private fun readMap() = cachedMap ?: storage.readMap().mapValues { (_, storedBucket) ->
        with(storedBucket) {
            getTokenBucketFactory().create(count, capacity, period.duration, periodStartElapsedRealtime.duration)
        }
    }.also {
        cachedMap = it
    }

    private fun writeMap(map: Map<String, TokenBucket>) = storage.writeMap(
        StoredTokenBucketMap(
            map.mapValues { (_, bucket) ->
                bucket.toStoredTokenBucket()
            },
        ),
    ).also {
        cachedMap = map
    }

    @VisibleForTesting
    internal fun <R> edit(block: (map: TokenBucketMap) -> R): R = lock.withLock {
        val initialMap = readMap()
        val map = TokenBucketMap(
            initialMap = initialMap,
            maxBuckets = getMaxBuckets(),
            tokenBucketFactory = getTokenBucketFactory(),
        )
        return block(map).also {
            val finalMap = map.toMap()
            if (finalMap != initialMap) {
                writeMap(finalMap)
            }
        }
    }

    override fun takeSimple(key: String, n: Int, tag: String): Boolean {
        val allowed = devMode.isEnabled() ||
            edit { map ->
                val bucket = map.upsertBucket(key) ?: return@edit false
                bucket.take(n, tag)
            }
        if (!allowed) Logger.d("Rate-limit applied: $key/$tag")
        return allowed
    }

    companion object {
        // Rough time taken to boot (to ensure that we keep feed tokens in case of a boot loop).
        private val BOOT_TIME = 3.minutes
    }
}
