package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.FetchedDeviceConfigContainer.Companion.asSamplingConfig
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class SettingsUpdateTask @Inject constructor(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val settingsUpdateService: SettingsUpdateService,
    override val metrics: BuiltinMetricsStore,
    private val settingsUpdateHandler: SettingsUpdateHandler,
    private val useDeviceConfig: UseDeviceConfig,
    private val deviceConfigUpdateService: DeviceConfigUpdateService,
    private val samplingConfig: CurrentSamplingConfig,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        return try {
            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            if (useDeviceConfig()) {
                val deviceConfig = deviceConfigUpdateService.deviceConfig(
                    DeviceConfigUpdateService.DeviceConfigArgs(
                        DeviceConfigUpdateService.DeviceInfo(
                            deviceSerial = deviceInfo.deviceSerial,
                            hardwareVersion = deviceInfo.hardwareVersion,
                            softwareType = SOFTWARE_TYPE,
                            softwareVersion = deviceInfo.softwareVersion,
                        )
                    )
                )
                // All parts of the response are technically optional
                deviceConfig.memfault?.bort?.sdkSettings?.let {
                    settingsUpdateHandler.handleSettingsUpdate(it)
                }
                deviceConfig.memfault?.sampling?.let {
                    samplingConfig.update(it.asSamplingConfig(deviceConfig.revision))
                }
            } else {
                val new = settingsUpdateService.settings(
                    deviceInfo.deviceSerial,
                    deviceInfo.softwareVersion,
                    deviceInfo.hardwareVersion,
                ).data
                settingsUpdateHandler.handleSettingsUpdate(new)
            }

            TaskResult.SUCCESS
        } catch (e: HttpException) {
            Logger.d("failed to fetch settings from remote endpoint", e)
            TaskResult.SUCCESS
        } catch (e: SerializationException) {
            Logger.d("failed to deserialize fetched settings from remote endpoint", e)
            TaskResult.SUCCESS
        }
    }

    override fun convertAndValidateInputData(inputData: Data) {
    }
}
