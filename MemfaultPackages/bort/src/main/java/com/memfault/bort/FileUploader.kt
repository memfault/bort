package com.memfault.bort

import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.CombinedTime
import java.io.File
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit

@Serializable
data class FileUploadTokenOnly(
    val token: String
)

val FILE_UPLOAD_TOKEN_EMPTY = FileUploadTokenOnly(token = "")

@Serializable
data class FileUploadToken(
    val token: String = "",
    val md5: String,
    val name: String,
)

@Serializable
sealed class FileUploadPayload {
    @Serializable
    data class Package(
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
}

@Serializable
@SerialName("bugreport")
data class BugReportFileUploadPayload(
    val file: FileUploadTokenOnly = FILE_UPLOAD_TOKEN_EMPTY,

    @SerialName("processing_options")
    val processingOptions: ProcessingOptions = ProcessingOptions(),

    @SerialName("request_id")
    val requestId: String? = null,
) : FileUploadPayload() {
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
}

@Serializable
@SerialName("heartbeat")
data class HeartbeatFileUploadPayload(
    @SerialName("hardware_version")
    val hardwareVersion: String,

    @SerialName("device_serial")
    val deviceSerial: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("software_type")
    val softwareType: String = SOFTWARE_TYPE,

    @SerialName("collection_time")
    val collectionTime: CombinedTime,

    @SerialName("heartbeat_interval_ms")
    val heartbeatIntervalMs: Long,

    @SerialName("custom_metrics")
    val customMetrics: Map<String, Float>,

    @SerialName("builtin_metrics")
    val builtinMetrics: Map<String, Float>,

    val attachments: Attachments,

    @SerialName("cid_ref")
    val cidReference: LogcatCollectionId,
) : FileUploadPayload() {
    @Serializable
    data class Attachments(
        @SerialName("batterystats")
        val batteryStats: BatteryStats,
    ) {
        @Serializable
        data class BatteryStats(
            val file: FileUploadToken,
        )
    }
}

@Serializable
data class DropBoxEntryFileUploadPayload(
    val file: FileUploadTokenOnly = FILE_UPLOAD_TOKEN_EMPTY,

    @SerialName("hardware_version")
    val hardwareVersion: String,

    @SerialName("device_serial")
    val deviceSerial: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("software_type")
    val softwareType: String = SOFTWARE_TYPE,

    @SerialName("cid_ref")
    val cidReference: LogcatCollectionId,

    val metadata: DropBoxEntryFileUploadMetadata,
) : FileUploadPayload()

@Serializable
data class LogcatCollectionId(
    @Serializable(with = UUIDAsString::class)
    val uuid: UUID,
)

@Serializable
@SerialName("logcat")
data class LogcatFileUploadPayload(
    val file: FileUploadToken,

    @SerialName("hardware_version")
    val hardwareVersion: String,

    @SerialName("device_serial")
    val deviceSerial: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("software_type")
    val softwareType: String = SOFTWARE_TYPE,

    @SerialName("collection_time")
    val collectionTime: CombinedTime,

    val command: List<String>,

    val cid: LogcatCollectionId,

    @SerialName("next_cid")
    val nextCid: LogcatCollectionId,
) : FileUploadPayload()

@Serializable
data class TimezoneWithId(val id: String) {
    companion object {
        val deviceDefault: TimezoneWithId?
            get() = TimeZone.getDefault()?.toZoneId()?.id?.let { TimezoneWithId(it) }
    }
}

@Serializable
sealed class DropBoxEntryFileUploadMetadata {

    /**
     * The DropBoxManager tag associated with the uploaded file.
     */
    abstract val tag: String

    /**
     * File modification timestamp (Epoch/UTC) in milliseconds of the DropBox file on disk or null in case it could not
     * be retrieved.
     */
    @SerialName("file_time")
    abstract val fileTime: AbsoluteTime?

    /**
     * DropBox Entry timestamp (Epoch/UTC) in milliseconds.
     * The DropBoxManagerService backdates this time in case of a backwards time change, therefore, it's recommended to
     * use file_time_ms instead.
     */
    @SerialName("entry_time")
    abstract val entryTime: AbsoluteTime

    /**
     * The time when the DropBox Entry was collected.
     */
    @SerialName("collection_time")
    abstract val collectionTime: BootRelativeTime

    /**
     * DropBox Entry family
     */
    abstract val family: String

    /**
     * The device's timezone at the time the DropBox Entry was collected.
     */
    abstract val timezone: TimezoneWithId?
}

@Serializable
@SerialName("tombstone")
data class TombstoneFileUploadMetadata(
    override val tag: String,

    @SerialName("file_time")
    override val fileTime: AbsoluteTime?,

    @SerialName("entry_time")
    override val entryTime: AbsoluteTime,

    val packages: List<FileUploadPayload.Package>,

    @SerialName("collection_time")
    override val collectionTime: BootRelativeTime,

    override val timezone: TimezoneWithId?,
) : DropBoxEntryFileUploadMetadata() {
    override val family: String
        get() = "tombstone"
}

@Serializable
@SerialName("java_exception")
data class JavaExceptionFileUploadMetadata(
    override val tag: String,

    @SerialName("file_time")
    override val fileTime: AbsoluteTime?,

    @SerialName("entry_time")
    override val entryTime: AbsoluteTime,

    @SerialName("collection_time")
    override val collectionTime: BootRelativeTime,

    override val timezone: TimezoneWithId?,
) : DropBoxEntryFileUploadMetadata() {
    override val family: String
        get() = "java_exception"
}

@Serializable
@SerialName("anr")
data class AnrFileUploadMetadata(
    override val tag: String,

    @SerialName("file_time")
    override val fileTime: AbsoluteTime?,

    @SerialName("entry_time")
    override val entryTime: AbsoluteTime,

    @SerialName("collection_time")
    override val collectionTime: BootRelativeTime,

    override val timezone: TimezoneWithId?,
) : DropBoxEntryFileUploadMetadata() {
    override val family: String
        get() = "anr"
}

@Serializable
@SerialName("kmsg")
data class KmsgFileUploadMetadata(
    override val tag: String,

    @SerialName("file_time")
    override val fileTime: AbsoluteTime?,

    @SerialName("entry_time")
    override val entryTime: AbsoluteTime,

    @SerialName("collection_time")
    override val collectionTime: BootRelativeTime,

    override val timezone: TimezoneWithId?,
) : DropBoxEntryFileUploadMetadata() {
    override val family: String
        get() = "kmsg"
}

interface FileUploader {
    suspend fun upload(file: File, payload: FileUploadPayload, shouldCompress: Boolean): TaskResult
}

interface FileUploaderFactory {
    fun create(
        retrofit: Retrofit,
        projectApiKey: String
    ): FileUploader
}
