package com.memfault.bort

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.TimeZone
import java.util.UUID

@Serializable
data class FileUploadToken(
    val token: String = "",
    val md5: String,
    val name: String,
)

@Serializable
data class AndroidPackage(
    val id: String,

    @SerialName("version_code")
    val versionCode: Long,

    @SerialName("version_name")
    val versionName: String,

    @SerialName("user_id")
    val userId: Int,

    @SerialName("code_path")
    val codePath: String,
)

@Serializable
data class ProcessingOptions(
    @SerialName("process_anrs")
    val processAnrs: Boolean = true,

    @SerialName("process_java_exceptions")
    val processJavaExceptions: Boolean = true,

    @SerialName("process_last_kmsg")
    val processLastKmsg: Boolean = true,

    @SerialName("process_recovery_kmsg")
    val processRecoveryKmsg: Boolean = true,

    @SerialName("process_tombstones")
    val processTombstones: Boolean = true,
)

@Serializable
data class LogcatCollectionId(
    @Serializable(with = UUIDAsString::class)
    val uuid: UUID,
)

@Serializable
data class TimezoneWithId(val id: String) {
    companion object {
        val deviceDefault: TimezoneWithId
            get() = TimeZone.getDefault().toZoneId().id.let { TimezoneWithId(it) }
    }
}

@Serializable
data class MarFileUploadPayload(
    @SerialName("file")
    val file: FileUploadToken,
    @SerialName("hardware_version")
    val hardwareVersion: String,
    @SerialName("device_serial")
    val deviceSerial: String,
    @SerialName("software_version")
    val softwareVersion: String,
    @SerialName("software_type")
    val softwareType: String = SOFTWARE_TYPE,
)

interface FileUploader {
    suspend fun upload(file: File, payload: Payload, shouldCompress: Boolean): TaskResult
}

@Serializable
sealed class Payload {
    @Serializable
    class MarPayload(val payload: MarFileUploadPayload) : Payload()
}
