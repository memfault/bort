package com.memfault.bort.settings

import androidx.work.Constraints
import androidx.work.NetworkType
import com.memfault.bort.DataScrubbingRule
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LoggerSettings
import com.memfault.bort.shared.SdkVersionInfo
import kotlin.time.Duration

enum class NetworkConstraint(
    val networkType: NetworkType
) {
    CONNECTED(NetworkType.CONNECTED),
    UNMETERED(NetworkType.UNMETERED)
}

interface BugReportSettings {
    val dataSourceEnabled: Boolean
    val requestInterval: Duration
    val defaultOptions: BugReportOptions
    val maxStorageBytes: Int
    val maxStoredAge: Duration
    val maxUploadAttempts: Int
    val firstBugReportDelayAfterBoot: Duration
    val rateLimitingSettings: RateLimitingSettings
    val periodicRateLimitingPercentOfPeriod: Int
}

interface DropBoxSettings {
    val dataSourceEnabled: Boolean
    val anrRateLimitingSettings: RateLimitingSettings
    val javaExceptionsRateLimitingSettings: RateLimitingSettings
    val wtfsRateLimitingSettings: RateLimitingSettings
    val wtfsTotalRateLimitingSettings: RateLimitingSettings
    val kmsgsRateLimitingSettings: RateLimitingSettings
    val structuredLogRateLimitingSettings: RateLimitingSettings
    val tombstonesRateLimitingSettings: RateLimitingSettings
    val metricReportRateLimitingSettings: RateLimitingSettings
    val marFileRateLimitingSettings: RateLimitingSettings
    val continuousLogFileRateLimitingSettings: RateLimitingSettings
    val excludedTags: Set<String>
    val scrubTombstones: Boolean
}

interface BatteryStatsSettings {
    val dataSourceEnabled: Boolean
    val commandTimeout: Duration
    val useHighResTelemetry: Boolean
}

interface MetricsSettings {
    val dataSourceEnabled: Boolean
    val collectionInterval: Duration
    val systemProperties: List<String>
    val appVersions: List<String>
    val maxNumAppVersions: Int
    val reporterCollectionInterval: Duration
}

interface LogcatSettings {
    val dataSourceEnabled: Boolean
    val collectionInterval: Duration
    val commandTimeout: Duration
    val filterSpecs: List<LogcatFilterSpec>
    val kernelOopsDataSourceEnabled: Boolean
    val kernelOopsRateLimitingSettings: RateLimitingSettings
    val storeUnsampled: Boolean
    val collectionMode: LogcatCollectionMode
    val continuousLogDumpThresholdBytes: Int
    val continuousLogDumpThresholdTime: Duration
    val continuousLogDumpWrappingTimeout: Duration
}

interface FileUploadHoldingAreaSettings {
    val trailingMargin: Duration
    val maxStoredEventsOfInterest: Int
}

interface HttpApiSettings {
    val projectKey: String
    val filesBaseUrl: String
    val deviceBaseUrl: String
    val ingressBaseUrl: String
    val uploadNetworkConstraint: NetworkConstraint
    val uploadCompressionEnabled: Boolean

    val uploadConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(uploadNetworkConstraint.networkType)
            .build()

    val connectTimeout: Duration
    val writeTimeout: Duration
    val readTimeout: Duration
    val callTimeout: Duration
    val zipCompressionLevel: Int
    suspend fun useMarUpload(): Boolean
    val batchMarUploads: Boolean
    val batchedMarUploadPeriod: Duration
    suspend fun useDeviceConfig(): Boolean
    val deviceConfigInterval: Duration
    val maxMarFileSizeBytes: Int
    val maxMarStorageBytes: Long
    val maxMarUnsampledStoredAge: Duration
}

interface RebootEventsSettings {
    val dataSourceEnabled: Boolean
    val rateLimitingSettings: RateLimitingSettings
}

interface DataScrubbingSettings {
    val rules: List<DataScrubbingRule>
}

interface PackageManagerSettings {
    val commandTimeout: Duration
}

interface StructuredLogSettings {
    val dataSourceEnabled: Boolean
    val rateLimitingSettings: RateLimitingSettings
    val dumpPeriod: Duration
    val numEventsBeforeDump: Long
    val maxMessageSizeBytes: Long
    val minStorageThresholdBytes: Long
    val metricsReportEnabled: Boolean
    val highResMetricsEnabled: Boolean
}

interface OtaSettings {
    val updateCheckInterval: Duration
    val downloadNetworkConstraint: NetworkConstraint
}

interface StorageSettings {
    val maxClientServerFileTransferStorageBytes: Long
}

interface FleetSamplingSettings {
    /** Is this aspect enabled for the project? (does not determine what the resolution should be) */
    val loggingActive: Boolean
    /** Is this aspect enabled for the project? (does not determine what the resolution should be) */
    val debuggingActive: Boolean
    /** Is this aspect enabled for the project? (does not determine what the resolution should be) */
    val monitoringActive: Boolean
}

interface SettingsProvider {
    val minLogcatLevel: LogLevel
    val minStructuredLogLevel: LogLevel
    val eventLogEnabled: Boolean
    val internalLogToDiskEnabled: Boolean
    val isRuntimeEnableRequired: Boolean
    val settingsUpdateInterval: Duration

    val httpApiSettings: HttpApiSettings
    val sdkVersionInfo: SdkVersionInfo
    val deviceInfoSettings: DeviceInfoSettings
    val bugReportSettings: BugReportSettings
    val dropBoxSettings: DropBoxSettings
    val metricsSettings: MetricsSettings
    val batteryStatsSettings: BatteryStatsSettings
    val logcatSettings: LogcatSettings
    val fileUploadHoldingAreaSettings: FileUploadHoldingAreaSettings
    val rebootEventsSettings: RebootEventsSettings
    val dataScrubbingSettings: DataScrubbingSettings
    val packageManagerSettings: PackageManagerSettings
    val structuredLogSettings: StructuredLogSettings
    val otaSettings: OtaSettings
    val storageSettings: StorageSettings
    val fleetSamplingSettings: FleetSamplingSettings

    fun invalidate()
}

fun SettingsProvider.selectSettingsToMap(): Map<String, Any> = mapOf(
    "Settings" to mapOf(
        "minLogcatLevel" to minLogcatLevel,
        "minStructuredLogLevel" to minStructuredLogLevel,
        "isRuntimeEnableRequired" to isRuntimeEnableRequired,
        "eventLogEnabled" to eventLogEnabled,
    ),
    "Http Api Settings" to mapOf(
        "deviceBaseUrl" to httpApiSettings.deviceBaseUrl,
        "filesBaseUrl" to httpApiSettings.filesBaseUrl,
        "ingressBaseUrl" to httpApiSettings.ingressBaseUrl,
        "uploadNetworkConstraint" to httpApiSettings.uploadNetworkConstraint,
        "connectTimeout" to httpApiSettings.connectTimeout,
        "writeTimeout" to httpApiSettings.writeTimeout,
        "readTimeout" to httpApiSettings.readTimeout,
        "callTimeout" to httpApiSettings.callTimeout,
    ),
    "Device Info Settings" to mapOf(
        "androidBuildVersionKey" to deviceInfoSettings.androidBuildVersionKey,
        "androidHardwareVersionKey" to deviceInfoSettings.androidHardwareVersionKey,
        "androidSerialNumberKey" to deviceInfoSettings.androidSerialNumberKey,
    ),
    "Bug Report Settings" to mapOf(
        "dataSourceEnabled" to bugReportSettings.dataSourceEnabled,
        "requestInterval" to bugReportSettings.requestInterval,
        "defaultOptions" to bugReportSettings.defaultOptions,
        "maxStorageBytes" to bugReportSettings.maxStorageBytes,
        "maxStoredAge" to bugReportSettings.maxStoredAge,
        "maxUploadAttempts" to bugReportSettings.maxUploadAttempts,
        "firstBugReportDelayAfterBoot" to bugReportSettings.firstBugReportDelayAfterBoot,
    ),
    "DropBox Settings" to mapOf(
        "dataSourceEnabled" to dropBoxSettings.dataSourceEnabled,
    ),
    "Metrics Settings" to mapOf(
        "dataSourceEnabled" to metricsSettings.dataSourceEnabled,
        "collectionInterval" to metricsSettings.collectionInterval,
    ),
    "BatteryStats Settings" to mapOf(
        "dataSourceEnabled" to batteryStatsSettings.dataSourceEnabled,
    ),
)

fun SettingsProvider.asLoggerSettings(): LoggerSettings = LoggerSettings(
    eventLogEnabled = eventLogEnabled,
    logToDisk = internalLogToDiskEnabled,
    minLogcatLevel = minLogcatLevel,
    minStructuredLevel = minStructuredLogLevel,
    hrtEnabled = structuredLogSettings.highResMetricsEnabled,
)

interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
    fun requiresRuntimeEnable(): Boolean
}

typealias ConfigValue<T> = () -> T
