package com.memfault.bort

import android.os.Build
import com.memfault.bort.settings.AndroidBuildFormat
import com.memfault.bort.settings.DeviceInfoSettings

internal const val SOFTWARE_TYPE = "android-build"

data class DeviceInfo(
    val deviceSerial: String,
    val hardwareVersion: String,
    val softwareVersion: String
) {
    companion object {
        fun fromSettingsAndSystemProperties(
            settings: DeviceInfoSettings,
            props: Map<String, String>,
            getBuildFingerprint: () -> String = { Build.FINGERPRINT }
        ): DeviceInfo {
            val softwareVersion = when (settings.androidBuildFormat) {
                AndroidBuildFormat.SYSTEM_PROPERTY_ONLY -> props[settings.androidBuildVersionKey] ?: "unknown"
                AndroidBuildFormat.BUILD_FINGERPRINT_ONLY -> getBuildFingerprint()
                AndroidBuildFormat.BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY ->
                    "${getBuildFingerprint()}::${props[settings.androidBuildVersionKey] ?: "unknown"}"
            }
            return DeviceInfo(
                props[settings.androidSerialNumberKey] ?: "unknown",
                hardwareVersionFromSettingsAndSystemProperties(settings, props),
                softwareVersion
            )
        }

        fun getLegacyHardwareVersion(props: Map<String, String>) =
            listOf("ro.product.brand", "ro.product.name", "ro.product.device")
                .mapNotNull { key -> props[key] }
                .joinToString("-")

        fun hardwareVersionFromSettingsAndSystemProperties(
            settings: DeviceInfoSettings,
            props: Map<String, String>
        ): String =
            settings.androidHardwareVersionKey.let { key ->
                if (key != "") {
                    props[key] ?: "unknown"
                } else {
                    getLegacyHardwareVersion(props)
                }
            }
    }
}
