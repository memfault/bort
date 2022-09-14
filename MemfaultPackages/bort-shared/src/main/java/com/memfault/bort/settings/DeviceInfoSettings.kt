package com.memfault.bort.settings

interface DeviceInfoSettings {
    val androidBuildFormat: AndroidBuildFormat
    val androidBuildVersionKey: String
    val androidHardwareVersionKey: String
    val androidSerialNumberKey: String
}

fun FetchedSettings.deviceInfoSettings() = object : DeviceInfoSettings {
    override val androidBuildFormat
        get() = AndroidBuildFormat.getById(deviceInfoAndroidBuildVersionSource)
    override val androidBuildVersionKey
        get() = deviceInfoAndroidBuildVersionKey
    override val androidHardwareVersionKey
        get() = deviceInfoAndroidHardwareVersionKey
    override val androidSerialNumberKey
        get() = deviceInfoAndroidDeviceSerialKey
}
