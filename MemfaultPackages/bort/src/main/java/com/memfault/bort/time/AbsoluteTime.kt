package com.memfault.bort.time

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.serialization.Serializable

interface BaseAbsoluteTime {
    /**
     * RTC timestamp.
     */
    val timestamp: Instant

    operator fun compareTo(other: BaseAbsoluteTime): Int = timestamp.compareTo(other.timestamp)

    operator fun minus(duration: Duration): AbsoluteTime =
        AbsoluteTime(this.timestamp.minus(duration.toJavaDuration()))
}

@Serializable
data class AbsoluteTime(
    /**
     * RTC timestamp, formatted as ISO-8601 string when serialized.
     */
    @Serializable(with = InstantAsIso8601String::class)
    override val timestamp: Instant
) : BaseAbsoluteTime {
    constructor(time: BaseAbsoluteTime) : this(time.timestamp)

    companion object {
        fun now() = AbsoluteTime(Instant.ofEpochMilli(System.currentTimeMillis()))
    }
}

fun interface AbsoluteTimeProvider : () -> AbsoluteTime

@ContributesBinding(SingletonComponent::class)
class RealAbsoluteTimeProvider @Inject constructor() : AbsoluteTimeProvider {
    override fun invoke(): AbsoluteTime = AbsoluteTime.now()
}

fun Long.toAbsoluteTime() = AbsoluteTime(
    Instant.ofEpochMilli(this)
)

fun Duration.toAbsoluteTime() = AbsoluteTime(
    Instant.ofEpochMilli(this.toLongMilliseconds())
)
