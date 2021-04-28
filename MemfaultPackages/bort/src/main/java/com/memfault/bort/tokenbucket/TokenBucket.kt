package com.memfault.bort.tokenbucket

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
) {
    val count: Int get() = _count
    val periodStartElapsedRealtime: Duration get() = _periodStartElapsedRealtime

    val isFull: Boolean
        get() = count >= capacity

    /**
     * Attempt to take n tokens. For convenience, the token count is re-up'd before attempting to take tokens.
     * @param n Number of tokens to take.
     * @return true if successful
     */
    fun take(n: Int = 1): Boolean {
        feed()
        return if (count < n) false
        else true.also {
            _count -= n
        }
    }

    /**
     * Re-ups the token count based on the current time.
     */
    internal fun feed() {
        val now = elapsedRealtime()
        val periods = floor((now - _periodStartElapsedRealtime) / period)
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
}
