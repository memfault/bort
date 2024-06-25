package com.memfault.bort

import android.content.Context
import android.os.Build
import android.provider.Settings.Secure
import com.memfault.bort.settings.AndroidBuildFormat

const val SOFTWARE_TYPE = "android-build"

data class DeviceInfoParams(
    val androidBuildFormat: AndroidBuildFormat,
    val androidBuildVersionKey: String,
    val androidHardwareVersionKey: String,
    val androidSerialNumberKey: String,
    val overriddenSerialNumber: String?,
)

data class DeviceInfo(
    val deviceSerial: String,
    val hardwareVersion: String,
    val softwareVersion: String,
) {
    companion object {
        fun fromSettingsAndSystemProperties(
            settings: DeviceInfoParams,
            props: Map<String, String>,
            context: Context,
            getBuildFingerprint: () -> String = { Build.FINGERPRINT },
            getFallbackAndroidId: () -> String = { Secure.getString(context.contentResolver, Secure.ANDROID_ID) },
        ): DeviceInfo {
            val softwareVersion = when (settings.androidBuildFormat) {
                AndroidBuildFormat.SYSTEM_PROPERTY_ONLY -> props[settings.androidBuildVersionKey] ?: "unknown"
                AndroidBuildFormat.BUILD_FINGERPRINT_ONLY -> getBuildFingerprint()
                AndroidBuildFormat.BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY ->
                    "${getBuildFingerprint()}::${props[settings.androidBuildVersionKey] ?: "unknown"}"
            }
            return DeviceInfo(
                settings.overriddenSerialNumber ?: props[settings.androidSerialNumberKey] ?: getFallbackAndroidId(),
                hardwareVersionFromSettingsAndSystemProperties(settings, props),
                softwareVersion,
            )
        }

        fun getLegacyHardwareVersion(props: Map<String, String>) =
            listOf("ro.product.brand", "ro.product.name", "ro.product.device")
                .mapNotNull { key -> props[key] }
                .joinToString("-")

        fun hardwareVersionFromSettingsAndSystemProperties(
            settings: DeviceInfoParams,
            props: Map<String, String>,
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
