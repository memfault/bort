package com.memfault.bort.time

import java.time.Instant
import kotlinx.serialization.Serializable

interface BaseAbsoluteTime {
    /**
     * RTC timestamp.
     */
    val timestamp: Instant
}

@Serializable
data class AbsoluteTime(
    /**
     * RTC timestamp, formatted as ISO-8601 string when serialized.
     */
    @Serializable(with = InstantAsIso8601String::class)
    override val timestamp: Instant
) : BaseAbsoluteTime

fun Long.toAbsoluteTime() = AbsoluteTime(
    Instant.ofEpochMilli(this)
)
