package com.memfault.bort.clientserver

import com.memfault.bort.BugReportFileUploadPayload.ProcessingOptions
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class MarManifest(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("collection_time")
    val collectionTime: CombinedTime,
    @SerialName("type")
    val type: String,
    @SerialName("device")
    val device: MarDevice,
    @SerialName("metadata")
    val metadata: MarMetadata,
)

@Serializable
data class MarDevice(
    @SerialName("project_key")
    val projectKey: String,
    @SerialName("hardware_version")
    val hardwareVersion: String,
    @SerialName("software_version")
    val softwareVersion: String,
    @SerialName("software_type")
    val softwareType: String,
    @SerialName("device_serial")
    val deviceSerial: String,
)

@Serializable
sealed class MarMetadata {
    @Serializable
    @SerialName("android-heartbeat")
    data class HeartbeatMarMetadata(
        @SerialName("batterystats_file_name")
        val batteryStatsFileName: String?,
        @SerialName("heartbeat_interval_ms")
        val heartbeatIntervalMs: Long,
        @SerialName("custom_metrics")
        val customMetrics: Map<String, JsonPrimitive>,
        @SerialName("builtin_metrics")
        val builtinMetrics: Map<String, JsonPrimitive>,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-bugreport")
    data class BugReportMarMetadata(
        @SerialName("bug_report_file_name")
        val bugReportFileName: String,
        @SerialName("processing_options")
        val processingOptions: ProcessingOptions,
        @SerialName("request_id")
        val requestId: String? = null,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-dropbox")
    data class DropBoxMarMetadata(
        @SerialName("entry_file_name")
        val entryFileName: String,
        @SerialName("tag")
        val tag: String,
        @SerialName("entry_time")
        val entryTime: AbsoluteTime,
        @SerialName("timezone")
        val timezone: TimezoneWithId,
        @SerialName("cid_ref")
        val cidReference: LogcatCollectionId,
        @SerialName("packages")
        val packages: List<FileUploadPayload.Package>,
        @SerialName("file_time")
        val fileTime: AbsoluteTime?,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-logcat")
    data class LogcatMarMetadata(
        @SerialName("log_file_name")
        val logFileName: String,
        @SerialName("command")
        val command: List<String>,
        @SerialName("cid")
        val cid: LogcatCollectionId,
        @SerialName("next_cid")
        val nextCid: LogcatCollectionId,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-structured")
    data class StructuredLogMarMetadata(
        @SerialName("log_file_name")
        val logFileName: String,
        @SerialName("cid")
        val cid: LogcatCollectionId,
        @SerialName("next_cid")
        val nextCid: LogcatCollectionId,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-reboot")
    data class RebootMarMetadata(
        @SerialName("reason")
        val reason: String,
        @SerialName("subreason")
        val subreason: String? = null,
        @SerialName("details")
        val details: List<String>? = null,
    ) : MarMetadata()
}
