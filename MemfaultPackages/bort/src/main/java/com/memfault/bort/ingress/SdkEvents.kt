package com.memfault.bort.ingress

import kotlinx.serialization.Serializable

@Serializable
data class SdkEvent(
    val timestamp: Long,
    val name: String
)

@Serializable
data class SdkEventCollection(
    val opaque_device_id: String,
    val sdk_version: String,
    val sdk_events: List<SdkEvent>
)
