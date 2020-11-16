package com.memfault.bort

import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable
data class AbsoluteTime(
    /**
     * RTC timestamp formatted as ISO-8601 string.
     */
    val timestamp: String
)

fun Long.toAbsoluteTime() = AbsoluteTime(
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(this))
)
