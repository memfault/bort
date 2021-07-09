package com.memfault.bort.shared

import kotlinx.serialization.Serializable

@Serializable
data class SoftwareUpdateSettings(
    val deviceSerial: String,
    val currentVersion: String,
    val hardwareVersion: String,
    val softwareType: String,
    val updateCheckIntervalMs: Long,
    val baseUrl: String,
    val projectApiKey: String,
)
