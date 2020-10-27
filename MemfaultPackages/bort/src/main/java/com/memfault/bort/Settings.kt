package com.memfault.bort

import androidx.work.Constraints
import androidx.work.NetworkType
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.SdkVersionInfo

enum class NetworkConstraint(
    val networkType: NetworkType
) {
    CONNECTED(NetworkType.CONNECTED),
    UNMETERED(NetworkType.UNMETERED)
}

interface BugReportSettings {
    val dataSourceEnabled: Boolean
    val requestIntervalHours: Long
    val defaultOptions: BugReportOptions
    val maxUploadAttempts: Int
    val firstBugReportDelayAfterBootMinutes: Long
}

interface DropBoxSettings {
    val dataSourceEnabled: Boolean
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
    val ingressBaseUrl: String
    val uploadNetworkConstraint: NetworkConstraint

    val uploadConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(uploadNetworkConstraint.networkType)
            .build()
}

interface SettingsProvider {
    val minLogLevel: LogLevel
    val isRuntimeEnableRequired: Boolean

    val httpApiSettings: HttpApiSettings
    val sdkVersionInfo: SdkVersionInfo
    val deviceInfoSettings: DeviceInfoSettings
    val bugReportSettings: BugReportSettings
    val dropBoxSettings: DropBoxSettings
}

interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
}
