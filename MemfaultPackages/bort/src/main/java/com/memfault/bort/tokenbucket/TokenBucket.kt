package com.memfault.bort.tokenbucket

import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.RATE_LIMIT_APPLIED
import com.memfault.bort.requester.BugReportRequestWorker.Companion.BUGREPORT_RATE_LIMITING_TAG
import com.memfault.bort.shared.Logger
import kotlin.math.floor
import kotlin.time.Duration

class TokenBucket(
    /**
     * Maximum number of tokens the bucket can hold.
     */
    val capacity: Int,

    /**
     * The duration it takes for a new token to be added the bucket (the inverse of
     * the rate).
     */
    val period: Duration,

    /**
     * Number of tokens left in the bucket at periodStartElapsedRealtime.
     */
    private var _count: Int,

    /**
     * The start of the period that the count was last refreshed, in milliseconds
     * since boot as provided by SystemClock.elapsedRealtime().
     */
    private var _periodStartElapsedRealtime: Duration,

    /**
     * Function to get milliseconds since boot. Defaults to SystemClock.elapsedRealtime().
     * Injectable for testing purposes.
     */
    val elapsedRealtime: () -> Duration = ::realElapsedRealtime,
    private val metrics: BuiltinMetricsStore,
) {
    val count: Int get() = _count
    val periodStartElapsedRealtime: Duration get() = _periodStartElapsedRealtime

    val isFull: Boolean
        get() = count >= capacity

    /**
     * Attempt to take n tokens. For convenience, the token count is re-up'd before attempting to take tokens.
     * @param n Number of tokens to take.
     * @param tag for metrics
     * @return true if successful
     */
    fun take(n: Int = 1, tag: String): Boolean {
        feed(tag = tag)
        return if (count < n) {
            metrics.increment("${RATE_LIMIT_APPLIED}_$tag")
            Logger.test("Rate-limit applied: $tag")
            false
        } else {
            true.also {
                _count -= n
            }
        }
    }

    /**
     * Re-ups the token count based on the current time.
     */
    internal fun feed(tag: String? = null) {
        val now = elapsedRealtime()
        // Note that _periodStartElapsedRealtime may be negative, if time was carried over from the previous boot.
        val periods = floor((now - _periodStartElapsedRealtime) / period)
        if (tag in DEBUG_TAGS) {
            Logger.d(
                "feeding $tag: prevCount=$count now=$now " +
                    "_periodStartElapsedRealtime=$_periodStartElapsedRealtime periods=$periods",
            )
        }
        if (periods < 1.0) {
            return
        }
        _count = minOf(count + periods.toInt(), capacity)
        _periodStartElapsedRealtime += period * periods
    }

    fun copy(_count: Int? = null, capacity: Int? = null, period: Duration? = null): TokenBucket =
        TokenBucket(
            capacity = capacity ?: this.capacity,
            period = period ?: this.period,
            _count = _count ?: this._count,
            _periodStartElapsedRealtime = this._periodStartElapsedRealtime,
            elapsedRealtime = this.elapsedRealtime,
            metrics = this.metrics,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenBucket

        if (_count != other._count) return false
        if (capacity != other.capacity) return false
        if (_periodStartElapsedRealtime != other._periodStartElapsedRealtime) return false
        if (period != other.period) return false
        if (periodStartElapsedRealtime != other.periodStartElapsedRealtime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _count
        result = 31 * result + capacity
        result = 31 * result + _periodStartElapsedRealtime.hashCode()
        result = 31 * result + period.hashCode()
        result = 31 * result + periodStartElapsedRealtime.hashCode()
        return result
    }

    companion object {
        private val DEBUG_TAGS = listOf(BUGREPORT_RATE_LIMITING_TAG)
    }
}
