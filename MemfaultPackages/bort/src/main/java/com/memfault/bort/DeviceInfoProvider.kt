package com.memfault.bort

import com.memfault.bort.settings.DeviceInfoSettings

interface DeviceInfoProvider {
    suspend fun getDeviceInfo(): DeviceInfo

    // MFLT-3136: properly cache and prime this
    val lastDeviceInfo: DeviceInfo?
}

class RealDeviceInfoProvider(
    private val deviceInfoSettings: DeviceInfoSettings
) : DeviceInfoProvider {
    override var lastDeviceInfo: DeviceInfo? = null
        private set

    override suspend fun getDeviceInfo(): DeviceInfo =
        DeviceInfo.fromSettingsAndSystemProperties(
            deviceInfoSettings, DumpsterClient().getprop() ?: emptyMap(),
        ).also {
            lastDeviceInfo = it
        }
}
