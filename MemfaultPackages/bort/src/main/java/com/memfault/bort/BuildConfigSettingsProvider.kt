package com.memfault.bort

import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.LogLevel

open class BuildConfigSettingsProvider : SettingsProvider {

    override val minLogLevel =
        LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.VERBOSE

    override val isRuntimeEnableRequired = BuildConfig.RUNTIME_ENABLE_REQUIRED

    override val httpApiSettings = object : HttpApiSettings {
        override val uploadNetworkConstraint: NetworkConstraint =
            if (BuildConfig.UPLOAD_NETWORK_CONSTRAINT_ALLOW_METERED_CONNECTION) NetworkConstraint.CONNECTED
            else NetworkConstraint.UNMETERED
        override val projectKey = BuildConfig.MEMFAULT_PROJECT_API_KEY
        override val filesBaseUrl = BuildConfig.MEMFAULT_FILES_BASE_URL
        override val ingressBaseUrl = BuildConfig.MEMFAULT_INGRESS_BASE_URL
    }

    override val deviceInfoSettings = object : DeviceInfoSettings {
        override val androidBuildFormat = AndroidBuildFormat.getById(BuildConfig.ANDROID_BUILD_VERSION_SOURCE)
        override val androidBuildVersionKey = BuildConfig.ANDROID_BUILD_VERSION_KEY
        override val androidHardwareVersionKey = BuildConfig.ANDROID_HARDWARE_VERSION_KEY
        override val androidSerialNumberKey = BuildConfig.ANDROID_DEVICE_SERIAL_KEY
    }

    override val sdkVersionInfo = BuildConfigSdkVersionInfo

    override val bugReportSettings = object : BugReportSettings {
        override val dataSourceEnabled = BuildConfig.DATA_SOURCE_BUG_REPORTS_ENABLED
        override val requestIntervalHours = BuildConfig.BUG_REPORT_REQUEST_INTERVAL_HOURS.toLong()
        override val defaultOptions = BugReportOptions(minimal = BuildConfig.BUG_REPORT_MINIMAL_MODE)
        override val maxUploadAttempts = BuildConfig.BUG_REPORT_MAX_UPLOAD_ATTEMPTS
        override val firstBugReportDelayAfterBootMinutes =
            BuildConfig.FIRST_BUG_REPORT_DELAY_AFTER_BOOT_MINUTES.toLong()
    }

    override val dropBoxSettings = object : DropBoxSettings {
        override val dataSourceEnabled = BuildConfig.DATA_SOURCE_DROP_BOX_ENABLED
    }
}
