package com.memfault.bort

data class DeviceInfo(
    val deviceSerial: String,
    val hardwareVersion: String,
    val softwareVersion: String
) {
    companion object {
        fun fromSettingsAndSystemProperties(
            settingsProvider: SettingsProvider,
            props: Map<String, String>
        ): DeviceInfo {
            return DeviceInfo(
                props[settingsProvider.androidSerialNumberKey()] ?: "unknown",
                hardwareVersionFromSettingsAndSystemProperties(settingsProvider, props),
                props[settingsProvider.androidBuildVersionKey()] ?: "unknown"
            )
        }

        fun getLegacyHardwareVersion(props: Map<String, String>) =
            listOf("ro.product.brand", "ro.product.name", "ro.product.device")
                .mapNotNull { key -> props[key] }
                .joinToString("-")

        fun hardwareVersionFromSettingsAndSystemProperties(
            settingsProvider: SettingsProvider,
            props: Map<String, String>
        ): String =
            settingsProvider.androidHardwareVersionKey().let { key ->
                if (key != "") {
                    props[key] ?: "unknown"
                } else {
                    getLegacyHardwareVersion(props)
                }
            }
    }
}
