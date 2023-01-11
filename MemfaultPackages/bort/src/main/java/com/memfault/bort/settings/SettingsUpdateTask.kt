package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.ClientDeviceInfoPreferenceProvider
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.clientserver.isServer
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.FetchedDeviceConfigContainer.Companion.asSamplingConfig
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
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
    private val cachedClientServerMode: CachedClientServerMode,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val clientDeviceInfoPreferenceProvider: ClientDeviceInfoPreferenceProvider,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        return try {
            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            if (useDeviceConfig()) {
                val deviceConfig = deviceConfigUpdateService.deviceConfig(
                    DeviceConfigUpdateService.DeviceConfigArgs(deviceInfo.asDeviceConfigInfo())
                )
                deviceConfig.handleUpdate(settingsUpdateHandler, samplingConfig)

                maybeFetchDeviceConfigForClientDevice()
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

    private suspend fun maybeFetchDeviceConfigForClientDevice() {
        if (!cachedClientServerMode.isServer()) return

        val clientDeviceInfo = clientDeviceInfoPreferenceProvider.get()
        if (clientDeviceInfo == null) {
            Logger.d("clientDeviceConfig not set")
            return
        }

        Logger.d("Additionally fetching for client: $clientDeviceInfo")
        val clientDeviceConfig = deviceConfigUpdateService.deviceConfig(
            DeviceConfigUpdateService.DeviceConfigArgs(clientDeviceInfo)
        )

        // Forward settings to client device.
        temporaryFileFactory.createTemporaryFile("deviceconfig", "json").useFile { file, preventDeletion ->
            preventDeletion()
            file.writeText(clientDeviceConfig.toJson())
            linkedDeviceFileSender.sendFileToLinkedDevice(file, CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG)
        }
    }

    override fun convertAndValidateInputData(inputData: Data) {
    }
}

suspend fun DecodedDeviceConfig.handleUpdate(
    settingsUpdateHandler: SettingsUpdateHandler,
    samplingConfig: CurrentSamplingConfig,
) {
    memfault?.bort?.sdkSettings?.let {
        settingsUpdateHandler.handleSettingsUpdate(it)
    }
    memfault?.sampling?.let {
        samplingConfig.update(it.asSamplingConfig(revision))
    }
}
