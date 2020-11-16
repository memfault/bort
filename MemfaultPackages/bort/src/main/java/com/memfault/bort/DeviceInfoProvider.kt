package com.memfault.bort

interface DeviceInfoProvider {
    suspend fun getDeviceInfo(): DeviceInfo
}

class RealDeviceInfoProvider(
    private val deviceInfoSettings: DeviceInfoSettings
) : DeviceInfoProvider {
    override suspend fun getDeviceInfo(): DeviceInfo =
        DeviceInfo.fromSettingsAndSystemProperties(
            deviceInfoSettings, DumpsterClient().getprop() ?: emptyMap(),
        )
}
