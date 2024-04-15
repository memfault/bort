package com.memfault.bort.chronicler

import com.memfault.bort.TimezoneWithId
import com.memfault.bort.time.AbsoluteTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientChroniclerEntry(
    @SerialName("event_type")
    val eventType: String,

    @SerialName("source")
    val source: String,

    @SerialName("event_data")
    val eventData: Map<String, String>,

    @SerialName("entry_time")
    val entryTime: AbsoluteTime,

    @SerialName("timezone")
    val timezone: TimezoneWithId,
)
