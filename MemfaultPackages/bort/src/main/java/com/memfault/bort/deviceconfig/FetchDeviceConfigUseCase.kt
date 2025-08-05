package com.memfault.bort.deviceconfig

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.settings.DecodedDeviceConfig
import com.memfault.bort.settings.DeviceConfigUpdateService
import com.memfault.bort.settings.asDeviceConfigInfo
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Called periodically by [com.memfault.bort.settings.SettingsUpdateTask] to fetch the device-config for this
 * device. Vendors can override the Default Anvil-provided implementation to pull the [DecodedDeviceConfig] from
 * another location by deleting the ContributesBinding line or using [ContributesBinding.replaces].
 */
interface FetchDeviceConfigUseCase {
    suspend fun get(): DecodedDeviceConfig?
}

@ContributesBinding(SingletonComponent::class)
class DefaultMemfaultFetchDeviceConfigUseCase
@Inject constructor(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val deviceConfigUpdateService: DeviceConfigUpdateService,
) : FetchDeviceConfigUseCase {
    override suspend fun get(): DecodedDeviceConfig? {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        return try {
            deviceConfigUpdateService.deviceConfig(
                DeviceConfigUpdateService.DeviceConfigArgs(deviceInfo.asDeviceConfigInfo()),
            )
        } catch (e: HttpException) {
            Logger.d("failed to fetch settings from remote endpoint", e)
            null
        } catch (e: Exception) {
            Logger.d("failed to deserialize fetched settings from remote endpoint", e)
            null
        }
    }
}
