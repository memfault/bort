package com.memfault.bort.clientserver

import com.memfault.bort.AndroidPackage
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.chronicler.ClientChroniclerEntry
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.ProjectKey
import com.memfault.bort.settings.Resolution
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
    @SerialName("debugging_resolution")
    val debuggingResolution: Resolution,
    @SerialName("logging_resolution")
    val loggingResolution: Resolution,
    @SerialName("monitoring_resolution")
    val monitoringResolution: Resolution,
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
        @SerialName("report_type")
        val reportType: String,
        @SerialName("report_name")
        val reportName: String? = null,
    ) : MarMetadata()

    @Serializable
    @SerialName("custom-data-recording")
    data class CustomDataRecordingMarMetadata(
        @SerialName("recording_file_name")
        val recordingFileName: String,
        @SerialName("start_time")
        val startTime: AbsoluteTime,
        @SerialName("duration_ms")
        val durationMs: Long,
        @SerialName("mimetypes")
        val mimeTypes: List<String>,
        @SerialName("reason")
        val reason: String,
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
    @SerialName("client-chronicler")
    data class ClientChroniclerMarMetadata(
        @SerialName("entries")
        val entries: List<ClientChroniclerEntry>,
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
        val packages: List<AndroidPackage>,
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
        @SerialName("contains_oops")
        val containsOops: Boolean? = null,
        @SerialName("collection_mode")
        val collectionMode: LogcatCollectionMode? = null,
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

    @Serializable
    @SerialName("android-selinux")
    data class SelinuxViolationMarMetadata(
        @SerialName("raw_denial")
        val rawDenial: String,
        @SerialName("package_name")
        val packageName: String?,
        @SerialName("package_version_name")
        val packageVersionName: String?,
        @SerialName("package_version_code")
        val packageVersionCode: Long?,
    ) : MarMetadata()

    @Serializable
    @SerialName("android-device-config")
    data class DeviceConfigMarMetadata(
        @SerialName("revision")
        val revision: Int,
    ) : MarMetadata()

    companion object {
        suspend fun createManifest(
            metadata: MarMetadata,
            collectionTime: CombinedTime,
            deviceInfoProvider: DeviceInfoProvider,
            projectKey: ProjectKey,
        ): MarManifest {
            val device = deviceInfoProvider.getDeviceInfo().asMarDevice(projectKey())
            return when (metadata) {
                is HeartbeatMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-heartbeat",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NOT_APPLICABLE,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NORMAL,
                )
                is BugReportMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-bugreport",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is ClientChroniclerMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "client-chronicler",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is DropBoxMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-dropbox",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is LogcatMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-logcat",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NORMAL,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is StructuredLogMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-structured",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is CustomDataRecordingMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "custom-data-recording",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
                is DeviceConfigMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-device-config",
                    device = device,
                    metadata = metadata,
                    // All resolutions are set to OFF. This means that the file is always uploaded, regardless of device
                    // fleet-sampling configuration.
                    debuggingResolution = Resolution.OFF,
                    loggingResolution = Resolution.OFF,
                    monitoringResolution = Resolution.OFF,
                )
                is RebootMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-reboot",
                    device = device,
                    metadata = metadata,
                    // All resolutions are set to OFF. This means that the file is always uploaded, regardless of device
                    // fleet-sampling configuration.
                    debuggingResolution = Resolution.OFF,
                    loggingResolution = Resolution.OFF,
                    monitoringResolution = Resolution.OFF,
                )
                is SelinuxViolationMarMetadata -> MarManifest(
                    collectionTime = collectionTime,
                    type = "android-selinux",
                    device = device,
                    metadata = metadata,
                    debuggingResolution = Resolution.NORMAL,
                    loggingResolution = Resolution.NOT_APPLICABLE,
                    monitoringResolution = Resolution.NOT_APPLICABLE,
                )
            }
        }
    }
}
