package com.memfault.bort.shared

import android.database.MatrixCursor
import androidx.work.NetworkType
import androidx.work.NetworkType.UNMETERED
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Note: please think carefully about forwards + backwards compatibility when changing this class. It is the interface
 * between Bort and the OTA app.
 *
 * New fields should have a default value.
 *
 * See [LegacySoftwareUpdateSettings].
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
    // Any new fields below this point must have a default value.
    val downloadNetworkTypeConstraint: NetworkType = UNMETERED,
) {
    companion object {
        fun deserialize(string: String): SoftwareUpdateSettings? = try {
            BortSharedJson.decodeFromString(serializer(), string)
        } catch (ex: SerializationException) {
            Logger.i("SoftwareUpdateSettings deserialize", ex)
            null
        }

        fun createCursor(config: SoftwareUpdateSettings) = MatrixCursor(arrayOf("legacySettings", "settings")).apply {
            addRow(
                listOf(
                    // Old versions cannot deserialize unknown fields, so the first column must always match the old
                    // settings format. See LegacyOtaSettings.
                    config.asLegacyOtaSettings().serialize(),
                    config.serialize(),
                ),
            )
        }
    }
}

fun SoftwareUpdateSettings.serialize() = BortSharedJson.encodeToString(SoftwareUpdateSettings.serializer(), this)

/**
 * NEVER ADD FIELDS TO THIS CLASS!
 *
 * This is a version of [SoftwareUpdateSettings] which old versions of the ota app (<=4.1) can deserialize. The JSON
 * deserializer used in those versions does not set `ignoreUnknownKeys = true`, so will crash on any new unknown fields.
 */
@Serializable
data class LegacySoftwareUpdateSettings(
    val deviceSerial: String,
    val currentVersion: String,
    val hardwareVersion: String,
    val softwareType: String,
    val updateCheckIntervalMs: Long,
    val baseUrl: String,
    val projectApiKey: String,
)

fun SoftwareUpdateSettings.asLegacyOtaSettings() = LegacySoftwareUpdateSettings(
    deviceSerial = deviceSerial,
    currentVersion = currentVersion,
    hardwareVersion = hardwareVersion,
    softwareType = softwareType,
    updateCheckIntervalMs = updateCheckIntervalMs,
    baseUrl = baseUrl,
    projectApiKey = projectApiKey,
)

fun LegacySoftwareUpdateSettings.serialize() =
    BortSharedJson.encodeToString(LegacySoftwareUpdateSettings.serializer(), this)
