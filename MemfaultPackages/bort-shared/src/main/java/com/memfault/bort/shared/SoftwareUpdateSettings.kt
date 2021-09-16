package com.memfault.bort.shared

import kotlinx.serialization.Serializable

/**
 * Note: please think carefully about forwards + backwards compatibility when changing this class. It is the interface
 * between Bort and the OTA app.
 */
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
