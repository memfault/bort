package com.memfault.bort

import android.app.Application
import com.memfault.bort.settings.DeviceInfoSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceInfoProvider {
    suspend fun getDeviceInfo(): DeviceInfo
}

@ContributesBinding(SingletonComponent::class)
@Singleton
class RealDeviceInfoProvider @Inject constructor(
    private val deviceInfoSettings: DeviceInfoSettings,
    private val dumpsterClient: DumpsterClient,
    private val application: Application,
    private val overrideSerial: OverrideSerial,
) : DeviceInfoProvider {
    private val deviceInfo = CachedAsyncProperty {
        DeviceInfo.fromSettingsAndSystemProperties(
            lastSettings,
            dumpsterClient.getprop() ?: emptyMap(),
            application,
        )
    }
    private var lastSettings = deviceInfoSettings.asParams(overrideSerial)
    private val mutex = Mutex()

    override suspend fun getDeviceInfo(): DeviceInfo = mutex.withLock {
        if (lastSettings != deviceInfoSettings.asParams(overrideSerial)) {
            Logger.d("Invalidating deviceInfo")
            lastSettings = deviceInfoSettings.asParams(overrideSerial)
            deviceInfo.invalidate()
        }
        deviceInfo.get()
    }
}

private fun DeviceInfoSettings.asParams(overrideSerial: OverrideSerial) = DeviceInfoParams(
    androidBuildFormat = androidBuildFormat,
    androidBuildVersionKey = androidBuildVersionKey,
    androidHardwareVersionKey = androidHardwareVersionKey,
    androidSerialNumberKey = androidSerialNumberKey,
    overriddenSerialNumber = overrideSerial.overriddenSerial,
)
