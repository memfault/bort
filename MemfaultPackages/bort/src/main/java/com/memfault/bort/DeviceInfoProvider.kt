package com.memfault.bort

import com.memfault.bort.settings.DeviceInfoSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DeviceInfoProvider {
    suspend fun getDeviceInfo(): DeviceInfo
}

@ContributesBinding(SingletonComponent::class)
@Singleton
class RealDeviceInfoProvider @Inject constructor(
    private val deviceInfoSettings: DeviceInfoSettings,
    private val dumpsterClient: DumpsterClient,
) : DeviceInfoProvider {
    private val deviceInfo = CachedAsyncProperty {
        DeviceInfo.fromSettingsAndSystemProperties(
            lastSettings, dumpsterClient.getprop() ?: emptyMap(),
        )
    }
    private var lastSettings = deviceInfoSettings.asParams()
    private val mutex = Mutex()

    override suspend fun getDeviceInfo(): DeviceInfo = mutex.withLock {
        if (lastSettings != deviceInfoSettings.asParams()) {
            Logger.d("Invalidating deviceInfo")
            lastSettings = deviceInfoSettings.asParams()
            deviceInfo.invalidate()
        }
        deviceInfo.get()
    }
}

private fun DeviceInfoSettings.asParams() = DeviceInfoParams(
    androidBuildFormat = androidBuildFormat,
    androidBuildVersionKey = androidBuildVersionKey,
    androidHardwareVersionKey = androidHardwareVersionKey,
    androidSerialNumberKey = androidSerialNumberKey,
)
