package com.memfault.bort.settings

import androidx.work.Constraints
import androidx.work.NetworkType
import com.memfault.bort.DataScrubbingRule
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
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
    val maxUploadAttempts: Int
    val firstBugReportDelayAfterBoot: Duration
    val rateLimitingSettings: RateLimitingSettings
}

interface DropBoxSettings {
    val dataSourceEnabled: Boolean
    val anrRateLimitingSettings: RateLimitingSettings
    val javaExceptionsRateLimitingSettings: RateLimitingSettings
    val kmsgsRateLimitingSettings: RateLimitingSettings
    val tombstonesRateLimitingSettings: RateLimitingSettings
    val excludedTags: Set<String>
}

interface BatteryStatsSettings {
    val dataSourceEnabled: Boolean
    val commandTimeout: Duration
}

interface MetricsSettings {
    val dataSourceEnabled: Boolean
    val collectionInterval: Duration
}

interface LogcatSettings {
    val dataSourceEnabled: Boolean
    val collectionInterval: Duration
    val commandTimeout: Duration
    val filterSpecs: List<LogcatFilterSpec>
}

interface FileUploadHoldingAreaSettings {
    val trailingMargin: Duration
    val maxStoredEventsOfInterest: Int
}

enum class AndroidBuildFormat(val id: String) {
    SYSTEM_PROPERTY_ONLY("system_property_only"),
    BUILD_FINGERPRINT_ONLY("build_fingerprint_only"),
    BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY("build_fingerprint_and_system_property");

    companion object {
        fun getById(id: String) = AndroidBuildFormat.values().first { it.id == id }
    }
}

interface DeviceInfoSettings {
    val androidBuildFormat: AndroidBuildFormat
    val androidBuildVersionKey: String
    val androidHardwareVersionKey: String
    val androidSerialNumberKey: String
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

interface SettingsProvider {
    val minLogLevel: LogLevel
    val eventLogEnabled: Boolean
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

    fun invalidate()
}

interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
}

typealias ConfigValue<T> = () -> T
